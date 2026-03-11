package backend.academy.linktracker.bot.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import backend.academy.linktracker.bot.dto.LinkUpdate;
import backend.academy.linktracker.bot.service.UpdateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
public class UpdateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UpdateService updateService;

    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("app.communication.server", () -> "http");
        registry.add("app.telegram.token", () -> "test-token");
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); // Для поддержки Java 8 времени если нужно
    }

    @Test
    void testValidUpdateReturns200() throws Exception {
        // Arrange
        LinkUpdate update = new LinkUpdate(1L, "https://github.com/test/repo", "Test description", List.of(123L));

        doNothing().when(updateService).processUpdate(any(LinkUpdate.class));

        // Act & Assert
        mockMvc.perform(post("/updates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());
    }

    @Test
    void testInvalidUpdateReturns400() throws Exception {
        // Arrange
        String invalidUpdate = "{}";

        // Act & Assert
        mockMvc.perform(post("/updates").contentType(MediaType.APPLICATION_JSON).content(invalidUpdate))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testUpdateWithMissingFieldsReturns400() throws Exception {
        // Arrange
        String invalidUpdate = """
            {
                "id": 1,
                "url": "https://github.com/test/repo"
            }
            """;

        // Act & Assert
        mockMvc.perform(post("/updates").contentType(MediaType.APPLICATION_JSON).content(invalidUpdate))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testUpdateWithEmptyChatIdsReturns400() throws Exception {
        // Arrange
        LinkUpdate update = new LinkUpdate(
                1L, "https://github.com/test/repo", "Test description", List.of() // пустой список chatIds
                );

        // Act & Assert
        mockMvc.perform(post("/updates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isBadRequest());
    }
}
