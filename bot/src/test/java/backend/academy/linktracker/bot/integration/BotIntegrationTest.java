package backend.academy.linktracker.bot.integration;

import static org.assertj.core.api.Assertions.assertThat;

import backend.academy.linktracker.bot.dto.LinkUpdate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class BotIntegrationTest {

    private static final Network NETWORK = Network.newNetwork();

    @Container
    @SuppressWarnings("resource")
    private static final GenericContainer<?> scrapperContainer = new GenericContainer<>(
                    DockerImageName.parse("link-tracker/scrapper:latest"))
            .withNetwork(NETWORK)
            .withNetworkAliases("scrapper")
            .withEnv("SERVER_PORT", "8081")
            .withEnv("SPRING_PROFILES_ACTIVE", "test")
            .withExposedPorts(8081)
            .waitingFor(Wait.forHttp("/actuator/health").forPort(8081));

    @Container
    @SuppressWarnings("resource")
    private static final GenericContainer<?> botContainer = new GenericContainer<>(
                    DockerImageName.parse("link-tracker/bot:latest"))
            .withNetwork(NETWORK)
            .withNetworkAliases("bot")
            .withEnv("SERVER_PORT", "8080")
            .withEnv("SPRING_PROFILES_ACTIVE", "test")
            .withEnv("SCRAPPER_BASE_URL", "http://scrapper:8081")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/actuator/health").forPort(8080));

    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;
    private String botUrl;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        objectMapper = new ObjectMapper();

        Integer mappedPort = botContainer.getMappedPort(8080);
        botUrl = "http://" + botContainer.getHost() + ":" + mappedPort;
    }

    @Test
    void testValidUpdateRequestReturns200() throws Exception {
        LinkUpdate update = new LinkUpdate(1L, "https://github.com/test/repo", "Test update", List.of(123L));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(update), headers);

        ResponseEntity<String> response =
                restTemplate.exchange(botUrl + "/updates", HttpMethod.POST, request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testInvalidUpdateRequestReturns400() {
        String invalidJson = "{\"invalidField\": \"value\"}";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(invalidJson, headers);

        ResponseEntity<String> response =
                restTemplate.exchange(botUrl + "/updates", HttpMethod.POST, request, String.class);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void testEmptyBodyReturns400() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(null, headers);

        ResponseEntity<String> response =
                restTemplate.exchange(botUrl + "/updates", HttpMethod.POST, request, String.class);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void testWrongContentTypeReturns415() throws Exception {
        LinkUpdate update = new LinkUpdate(1L, "https://github.com/test/repo", "Test update", List.of(123L));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        HttpEntity<LinkUpdate> request = new HttpEntity<>(update, headers);

        ResponseEntity<String> response =
                restTemplate.exchange(botUrl + "/updates", HttpMethod.POST, request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void testMalformedJsonReturns400() {
        String malformedJson = "{this is not valid json}";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(malformedJson, headers);

        ResponseEntity<String> response =
                restTemplate.exchange(botUrl + "/updates", HttpMethod.POST, request, String.class);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }
}
