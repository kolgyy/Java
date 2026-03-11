package backend.academy.linktracker.bot.integration;

import static org.assertj.core.api.Assertions.assertThat;

import backend.academy.linktracker.bot.dto.LinkUpdate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class BotIntegrationTest {

    @Container
    private static final GenericContainer<?> botContainer = new GenericContainer<>(
                    DockerImageName.parse("link-tracker/bot:latest"))
            .withEnv("SERVER_PORT", "8080")
            .withEnv("COMMUNICATION_PROTOCOL", "http")
            .withEnv("TELEGRAM_TOKEN", "test-token")
            .withExposedPorts(8080);

    @LocalServerPort
    private int port;

    private RestTemplate restTemplate;
    private String botUrl;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        objectMapper = new ObjectMapper();

        Integer mappedPort = botContainer.getMappedPort(8080);
        botUrl = "http://" + botContainer.getHost() + ":" + mappedPort;
    }

    @Test
    void testValidUpdateRequestReturns200() throws Exception {
        // Arrange
        LinkUpdate update =
                new LinkUpdate(1L, "https://github.com/test/repo", "Test update description", List.of(123L, 456L));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> requestEntity = new HttpEntity<>(objectMapper.writeValueAsString(update), headers);

        // Act
        ResponseEntity<String> response =
                restTemplate.exchange(botUrl + "/updates", HttpMethod.POST, requestEntity, String.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testInvalidUpdateRequestReturns400() {
        // Arrange
        String invalidUpdate = """
            {
                "invalidField": "value"
            }
            """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> requestEntity = new HttpEntity<>(invalidUpdate, headers);

        // Act
        ResponseEntity<String> response =
                restTemplate.exchange(botUrl + "/updates", HttpMethod.POST, requestEntity, String.class);

        // Assert
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.OK);
        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void testEmptyBodyReturns400() {
        // Arrange
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> requestEntity = new HttpEntity<>(null, headers);

        // Act
        ResponseEntity<String> response =
                restTemplate.exchange(botUrl + "/updates", HttpMethod.POST, requestEntity, String.class);

        // Assert
        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void testWrongContentTypeReturns415() {
        // Arrange
        LinkUpdate update =
                new LinkUpdate(1L, "https://github.com/test/repo", "Test update description", List.of(123L, 456L));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);

        HttpEntity<LinkUpdate> requestEntity = new HttpEntity<>(update, headers);

        // Act
        ResponseEntity<String> response =
                restTemplate.exchange(botUrl + "/updates", HttpMethod.POST, requestEntity, String.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void testMalformedJsonReturns400() {
        // Arrange
        String malformedJson = "{this is not valid json}";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> requestEntity = new HttpEntity<>(malformedJson, headers);

        // Act
        ResponseEntity<String> response =
                restTemplate.exchange(botUrl + "/updates", HttpMethod.POST, requestEntity, String.class);

        // Assert
        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }
}
