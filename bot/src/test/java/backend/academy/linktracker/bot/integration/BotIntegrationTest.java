package backend.academy.linktracker.bot.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import backend.academy.linktracker.bot.dto.LinkUpdate;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public class BotIntegrationTest {

    private static final Network NETWORK = Network.newNetwork();
    private static final RestTemplate restTemplate = new RestTemplate();
    private static String botBaseUrl;

    private static final GenericContainer<?> botContainer = new GenericContainer<>(
                    DockerImageName.parse("link-tracker/bot:latest"))
            .withNetwork(NETWORK)
            .withNetworkAliases("bot")
            .withEnv("SPRING_PROFILES_ACTIVE", "test")
            .withEnv("COMMUNICATION_PROTOCOL", "http")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/actuator/health").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(5)));

    @BeforeAll
    static void setUp() {
        botContainer.start();
        botBaseUrl = "http://" + botContainer.getHost() + ":" + botContainer.getMappedPort(8080);
    }

    @AfterAll
    static void tearDown() {
        botContainer.stop();
        NETWORK.close();
    }

    // 1. Корректный запрос -> 200 OK
    @Test
    void testCorrectBotUpdate() {
        LinkUpdate correctUpdate = new LinkUpdate(1L, "https://example.com", "Example description", List.of(1L));

        ResponseEntity<Void> response = restTemplate.postForEntity(
                botBaseUrl + "/updates", new HttpEntity<>(correctUpdate, jsonHeaders()), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // 2. Некорректный запрос -> 400 Bad Request
    @Test
    void testInvalidBotUpdate() {
        String invalidJson = """
            {
              "id": null,
              "url": 123,
              "description": 456,
              "tgChatIds": "not-a-list"
            }
            """;

        HttpEntity<String> invalidEntity = new HttpEntity<>(invalidJson, jsonHeaders());

        assertThatThrownBy(() -> restTemplate.postForEntity(botBaseUrl + "/updates", invalidEntity, Void.class))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(ex -> assertThat(((HttpClientErrorException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private static HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
