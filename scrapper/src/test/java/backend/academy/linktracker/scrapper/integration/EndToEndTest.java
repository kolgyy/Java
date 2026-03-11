package backend.academy.linktracker.scrapper.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import backend.academy.linktracker.scrapper.dto.request.AddLinkRequest;
import backend.academy.linktracker.scrapper.dto.response.LinkResponse;
import backend.academy.linktracker.scrapper.dto.response.ListLinksResponse;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
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
    private static final GenericContainer<?> botContainer = new GenericContainer<>(
                    DockerImageName.parse("link-tracker/bot:latest"))
            .withNetwork(NETWORK)
            .withNetworkAliases("bot")
            .withEnv("SERVER_PORT", "8080")
            .withEnv("COMMUNICATION_PROTOCOL", "http")
            .withEnv("TELEGRAM_TOKEN", "test-token")
            .withExposedPorts(8080);

    @Container
    private static final GenericContainer<?> scrapperContainer = new GenericContainer<>(
                    DockerImageName.parse("link-tracker/scrapper:latest"))
            .withNetwork(NETWORK)
            .withNetworkAliases("scrapper")
            .withEnv("SERVER_PORT", "8081")
            .withEnv("COMMUNICATION_PROTOCOL", "http")
            .withEnv("BOT_HTTP_BASE_URL", "http://bot:8080")
            .withEnv("APP_SCHEDULER_DELAY", "5s")
            .withEnv("GITHUB_TOKEN", "test-token")
            .withEnv("STACKOVERFLOW_KEY", "test-key")
            .withEnv("STACKOVERFLOW_ACCESS_TOKEN", "test-access-key")
            .dependsOn(botContainer)
            .withExposedPorts(8081);

    private static WireMockServer githubMockServer;
    private static WireMockServer stackoverflowMockServer;

    @LocalServerPort
    private int port;

    private RestTemplate restTemplate;
    private String scrapperBaseUrl;

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("app.github.base-url", () -> "http://localhost:8089");
        registry.add("app.stackoverflow.base-url", () -> "http://localhost:8090");
    }

    @BeforeAll
    static void startWireMock() {
        githubMockServer = new WireMockServer(8089);
        githubMockServer.start();
        WireMock.configureFor("localhost", 8089);

        stackoverflowMockServer = new WireMockServer(8090);
        stackoverflowMockServer.start();
        // Не перенастраиваем WireMock для stackoverflow, так как это перезапишет конфигурацию github
    }

    @AfterAll
    static void stopWireMock() {
        if (githubMockServer != null) {
            githubMockServer.stop();
        }
        if (stackoverflowMockServer != null) {
            stackoverflowMockServer.stop();
        }
    }

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();

        // URL для доступа к контейнеру Scrapper
        Integer scrapperPort = scrapperContainer.getMappedPort(8081);
        scrapperBaseUrl = "http://" + scrapperContainer.getHost() + ":" + scrapperPort;

        // Сбрасываем WireMock перед каждым тестом
        if (githubMockServer != null) {
            githubMockServer.resetAll();
        }
        if (stackoverflowMockServer != null) {
            stackoverflowMockServer.resetAll();
        }
    }

    @Test
    void testEndToEndLinkTracking() {
        // 1. Регистрация чата
        ResponseEntity<Void> registerResponse =
                restTemplate.postForEntity(scrapperBaseUrl + "/tg-chat/123", null, Void.class);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 2. Настройка моков для внешних API
        stubGithubResponse("test-owner", "test-repo");
        stubStackoverflowResponse("12345");

        // 3. Добавление GitHub ссылки
        AddLinkRequest addGithubRequest =
                new AddLinkRequest("https://github.com/test-owner/test-repo", List.of("github", "test"), List.of());

        HttpHeaders headers = new HttpHeaders();
        headers.set("Tg-Chat-Id", "123");
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<AddLinkRequest> addGithubEntity = new HttpEntity<>(addGithubRequest, headers);

        ResponseEntity<LinkResponse> addGithubResponse =
                restTemplate.exchange(scrapperBaseUrl + "/links", HttpMethod.POST, addGithubEntity, LinkResponse.class);

        assertThat(addGithubResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Objects.requireNonNull(addGithubResponse.getBody()).url())
                .isEqualTo("https://github.com/test-owner/test-repo");

        // 4. Добавление StackOverflow ссылки
        AddLinkRequest addStackoverflowRequest = new AddLinkRequest(
                "https://stackoverflow.com/questions/12345/test-question", List.of("stackoverflow", "java"), List.of());

        HttpEntity<AddLinkRequest> addStackoverflowEntity = new HttpEntity<>(addStackoverflowRequest, headers);

        ResponseEntity<LinkResponse> addStackoverflowResponse = restTemplate.exchange(
                scrapperBaseUrl + "/links", HttpMethod.POST, addStackoverflowEntity, LinkResponse.class);

        assertThat(addStackoverflowResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Objects.requireNonNull(addStackoverflowResponse.getBody()).url())
                .isEqualTo("https://stackoverflow.com/questions/12345/test-question");

        // 5. Проверка списка ссылок
        HttpHeaders getHeaders = new HttpHeaders();
        getHeaders.set("Tg-Chat-Id", "123");

        HttpEntity<Void> getEntity = new HttpEntity<>(getHeaders);
        ResponseEntity<ListLinksResponse> getResponse =
                restTemplate.exchange(scrapperBaseUrl + "/links", HttpMethod.GET, getEntity, ListLinksResponse.class);

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Objects.requireNonNull(getResponse.getBody()).size()).isEqualTo(2);
    }

    @Test
    void testSchedulerSendsUpdatesOnlyToSubscribedUsers() {
        // 1. Регистрация двух чатов
        restTemplate.postForEntity(scrapperBaseUrl + "/tg-chat/1", null, Void.class);
        restTemplate.postForEntity(scrapperBaseUrl + "/tg-chat/2", null, Void.class);

        // 2. Настройка моков для внешних API
        stubGithubResponse("owner1", "repo1");

        // 3. Добавление ссылки только для чата 1
        AddLinkRequest addRequest = new AddLinkRequest("https://github.com/owner1/repo1", List.of("test"), List.of());

        HttpHeaders headers1 = new HttpHeaders();
        headers1.set("Tg-Chat-Id", "1");
        headers1.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<AddLinkRequest> addEntity1 = new HttpEntity<>(addRequest, headers1);

        ResponseEntity<LinkResponse> addResponse =
                restTemplate.exchange(scrapperBaseUrl + "/links", HttpMethod.POST, addEntity1, LinkResponse.class);

        assertThat(addResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 4. Проверяем, что оба чата зарегистрированы
        HttpHeaders headers2 = new HttpHeaders();
        headers2.set("Tg-Chat-Id", "2");
        HttpEntity<Void> getEntity2 = new HttpEntity<>(headers2);

        ResponseEntity<ListLinksResponse> getResponse2 =
                restTemplate.exchange(scrapperBaseUrl + "/links", HttpMethod.GET, getEntity2, ListLinksResponse.class);

        assertThat(getResponse2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Objects.requireNonNull(getResponse2.getBody()).size()).isEqualTo(0);

        // Даем время для срабатывания планировщика
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofSeconds(1))
                .until(() -> {
                    // Проверяем, что WireMock получил запрос к GitHub API
                    try {
                        githubMockServer.verify(getRequestedFor(urlEqualTo("/repos/owner1/repo1")));
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                });
    }

    @Test
    void testAddInvalidLink() {
        // Регистрация чата
        restTemplate.postForEntity(scrapperBaseUrl + "/tg-chat/1", null, Void.class);

        // Попытка добавить некорректную ссылку
        AddLinkRequest addRequest = new AddLinkRequest("tbank://github.com/user/repo", List.of("test"), List.of());

        HttpHeaders headers = new HttpHeaders();
        headers.set("Tg-Chat-Id", "1");
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<AddLinkRequest> addEntity = new HttpEntity<>(addRequest, headers);

        ResponseEntity<String> addResponse =
                restTemplate.exchange(scrapperBaseUrl + "/links", HttpMethod.POST, addEntity, String.class);

        assertThat(addResponse.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void testAddDuplicateLink() {
        // Регистрация чата
        restTemplate.postForEntity(scrapperBaseUrl + "/tg-chat/1", null, Void.class);

        // Добавление ссылки первый раз
        AddLinkRequest addRequest = new AddLinkRequest("https://github.com/test/repo", List.of("test"), List.of());

        HttpHeaders headers = new HttpHeaders();
        headers.set("Tg-Chat-Id", "1");
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<AddLinkRequest> addEntity = new HttpEntity<>(addRequest, headers);

        ResponseEntity<LinkResponse> firstResponse =
                restTemplate.exchange(scrapperBaseUrl + "/links", HttpMethod.POST, addEntity, LinkResponse.class);

        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Попытка добавить ту же ссылку второй раз
        ResponseEntity<String> secondResponse =
                restTemplate.exchange(scrapperBaseUrl + "/links", HttpMethod.POST, addEntity, String.class);

        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    private void stubGithubResponse(String owner, String repo) {
        githubMockServer.stubFor(WireMock.get(urlEqualTo("/repos/" + owner + "/" + repo))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"updated_at\":\"" + OffsetDateTime.now() + "\"}")));
    }

    private void stubStackoverflowResponse(String questionId) {
        long currentTime = System.currentTimeMillis() / 1000;
        stackoverflowMockServer.stubFor(WireMock.get(urlEqualTo("/2.3/questions/" + questionId + "?site=stackoverflow"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"items\":[{\"last_activity_date\":" + currentTime + "}]}")));
    }

    private void stubGithubErrorResponse(String owner, String repo, int statusCode) {
        githubMockServer.stubFor(WireMock.get(urlEqualTo("/repos/" + owner + "/" + repo))
                .willReturn(aResponse().withStatus(statusCode)));
    }
}
