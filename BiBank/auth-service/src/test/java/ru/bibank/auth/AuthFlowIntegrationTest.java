package ru.bibank.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.bibank.common.outbox.OutboxPublisher;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Полный цикл аутентификации на настоящих PostgreSQL и Redis:
 * регистрация, вход, ротация refresh token, выход, блокировка и защита от перебора.
 */
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "bibank.security.jwt-secret=test-secret-test-secret-test-secret-123456",
        "bibank.security.internal-token=test-internal-token",
        "bibank.admin.email=admin@test.local",
        "bibank.admin.password=Admin12345",
        "spring.kafka.admin.auto-create=false"
})
class AuthFlowIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    @MockitoBean
    OutboxPublisher outboxPublisher;

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    @Test
    void registerLoginRefreshAndLogout() throws Exception {
        String email = "flow" + System.nanoTime() + "@test.ru";
        call(post("/api/auth/register"), Map.of("email", email, "password", "Secret123", "fullName", "Анна Смирнова"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.password").doesNotExist());
        call(post("/api/auth/register"), Map.of("email", email, "password", "Secret123", "fullName", "Дубликат"))
                .andExpect(status().isConflict());

        JsonNode login = body(call(post("/api/auth/login"), Map.of("email", email, "password", "Secret123"))
                .andExpect(status().isOk()));
        String access = login.get("accessToken").asText();
        String refresh = login.get("refreshToken").asText();

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());

        // refresh token одноразовый: после ротации старый больше не работает
        JsonNode rotated = body(call(post("/api/auth/refresh"), Map.of("refreshToken", refresh)).andExpect(status().isOk()));
        call(post("/api/auth/refresh"), Map.of("refreshToken", refresh)).andExpect(status().isUnauthorized());

        String newRefresh = rotated.get("refreshToken").asText();
        call(post("/api/auth/logout"), Map.of("refreshToken", newRefresh)).andExpect(status().isNoContent());
        call(post("/api/auth/refresh"), Map.of("refreshToken", newRefresh)).andExpect(status().isUnauthorized());
    }

    @Test
    void weakPasswordAndBadEmailAreValidated() throws Exception {
        call(post("/api/auth/register"), Map.of("email", "not-an-email", "password", "short", "fullName", "A"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.fullName").exists());
    }

    @Test
    void repeatedWrongPasswordsLockTheLogin() throws Exception {
        String email = "brute" + System.nanoTime() + "@test.ru";
        call(post("/api/auth/register"), Map.of("email", email, "password", "Secret123", "fullName", "Иван Иванов"))
                .andExpect(status().isCreated());
        for (int i = 0; i < 5; i++) {
            call(post("/api/auth/login"), Map.of("email", email, "password", "Wrong1234")).andExpect(status().isUnauthorized());
        }
        // даже верный пароль не принимается, пока действует блокировка
        call(post("/api/auth/login"), Map.of("email", email, "password", "Secret123")).andExpect(status().isTooManyRequests());
    }

    @Test
    void blockedUserLosesSessionsAndCannotLogin() throws Exception {
        String email = "block" + System.nanoTime() + "@test.ru";
        JsonNode user = body(call(post("/api/auth/register"), Map.of("email", email, "password", "Secret123", "fullName", "Пётр Петров")));
        JsonNode session = body(call(post("/api/auth/login"), Map.of("email", email, "password", "Secret123")));
        String admin = body(call(post("/api/auth/login"), Map.of("email", "admin@test.local", "password", "Admin12345"))
                .andExpect(status().isOk())).get("accessToken").asText();

        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + session.get("accessToken").asText()))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/users/" + user.get("id").asLong() + "/status")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BLOCKED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));

        call(post("/api/auth/refresh"), Map.of("refreshToken", session.get("refreshToken").asText()))
                .andExpect(status().isUnauthorized());
        call(post("/api/auth/login"), Map.of("email", email, "password", "Secret123")).andExpect(status().isForbidden());
    }

    private ResultActions call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               Map<String, ?> payload) throws Exception {
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
