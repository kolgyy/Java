package backend.academy.linktracker.scrapper.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import backend.academy.linktracker.scrapper.dto.request.AddLinkRequest;
import backend.academy.linktracker.scrapper.dto.response.LinkResponse;
import backend.academy.linktracker.scrapper.dto.response.ListLinksResponse;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class EndToEndTest {

    private static final Network NETWORK = Network.newNetwork();

    @Container
    @ServiceConnection
    private static final GenericContainer<?> botContainer = new GenericContainer<>(
                    DockerImageName.parse("link-tracker/bot:latest"))
            .withNetwork(NETWORK)
            .withNetworkAliases("bot")
            .withEnv("SERVER_PORT", "8080")
            .withEnv("SPRING_PROFILES_ACTIVE", "test")
            .withEnv("TELEGRAM_TOKEN", "test-token")
            .withExposedPorts(8080);

    @Container
    @ServiceConnection
    private static final GenericContainer<?> scrapperContainer = new GenericContainer<>(
                    DockerImageName.parse("link-tracker/scrapper:latest"))
            .withNetwork(NETWORK)
            .withNetworkAliases("scrapper")
            .withEnv("SERVER_PORT", "8081")
            .withEnv("SPRING_PROFILES_ACTIVE", "test")
            .withEnv("BOT_HTTP_BASE_URL", "http://bot:8080")
            .withExposedPorts(8081)
            .dependsOn(botContainer);

    private static WireMockServer githubMock;
    private static WireMockServer stackoverflowMock;

    @LocalServerPort
    private int port;

    private RestTemplate restTemplate;
    private String scrapperBaseUrl;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.github.base-url", () -> "http://localhost:8089");
        registry.add("app.stackoverflow.base-url", () -> "http://localhost:8090");
    }

    @BeforeAll
    static void startWireMock() {
        githubMock = new WireMockServer(8089);
        githubMock.start();

        stackoverflowMock = new WireMockServer(8090);
        stackoverflowMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        githubMock.stop();
        stackoverflowMock.stop();
    }

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        scrapperBaseUrl = "http://" + scrapperContainer.getHost() + ":" + scrapperContainer.getMappedPort(8081);

        githubMock.resetAll();
        stackoverflowMock.resetAll();
    }

    @Test
    void testEndToEndLinkTracking() {
        // Регистрация чата
        restTemplate.postForEntity(scrapperBaseUrl + "/tg-chat/123", null, Void.class);

        // Моки GitHub / StackOverflow
        stubGithub("owner", "repo");
        stubStackoverflow("12345");

        // Добавление GitHub ссылки
        AddLinkRequest githubRequest =
                new AddLinkRequest("https://github.com/owner/repo", List.of("github"), List.of());
        HttpHeaders headers = new HttpHeaders();
        headers.set("Tg-Chat-Id", "123");
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<LinkResponse> githubResp = restTemplate.exchange(
                scrapperBaseUrl + "/links",
                HttpMethod.POST,
                new HttpEntity<>(githubRequest, headers),
                LinkResponse.class);
        assertThat(githubResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Objects.requireNonNull(githubResp.getBody()).url()).isEqualTo("https://github.com/owner/repo");

        // Проверка списка ссылок
        ResponseEntity<ListLinksResponse> listResp = restTemplate.exchange(
                scrapperBaseUrl + "/links", HttpMethod.GET, new HttpEntity<>(headers), ListLinksResponse.class);
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Objects.requireNonNull(listResp.getBody()).size()).isEqualTo(1);
    }

    private void stubGithub(String owner, String repo) {
        githubMock.stubFor(get(urlEqualTo("/repos/" + owner + "/" + repo))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"updated_at\":\"" + OffsetDateTime.now() + "\"}")));
    }

    private void stubStackoverflow(String questionId) {
        long currentTime = System.currentTimeMillis() / 1000;
        stackoverflowMock.stubFor(get(urlEqualTo("/2.3/questions/" + questionId + "?site=stackoverflow"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"items\":[{\"last_activity_date\":" + currentTime + "}]}")));
    }
}
