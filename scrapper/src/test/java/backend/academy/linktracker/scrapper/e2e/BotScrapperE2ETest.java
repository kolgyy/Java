package backend.academy.linktracker.scrapper.e2e;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import backend.academy.linktracker.scrapper.dto.request.AddLinkRequest;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public class BotScrapperE2ETest {

    private static final Network NETWORK = Network.newNetwork();
    private static final RestTemplate restTemplate = new RestTemplate();

    private static String botBaseUrl;
    private static String scrapperBaseUrl;

    private static final GenericContainer<?> githubContainer = new GenericContainer<>("wiremock/wiremock:3.5.2")
            .withNetwork(NETWORK)
            .withNetworkAliases("github-api")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/__admin").forStatusCode(200));

    private static final GenericContainer<?> stackoverflowContainer = new GenericContainer<>("wiremock/wiremock:3.5.2")
            .withNetwork(NETWORK)
            .withNetworkAliases("stackoverflow-api")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/__admin").forStatusCode(200));

    private static final GenericContainer<?> scrapperContainer = new GenericContainer<>(
                    DockerImageName.parse("link-tracker/scrapper:latest"))
            .withImagePullPolicy(_ -> false)
            .withNetwork(NETWORK)
            .withNetworkAliases("scrapper")
            .dependsOn(githubContainer, stackoverflowContainer)
            .withEnv("SPRING_PROFILES_ACTIVE", "test")
            .withEnv("COMMUNICATION_PROTOCOL", "http")
            .withEnv("APP_COMMUNICATION_CLIENT", "http")
            .withEnv("APP_COMMUNICATION_SERVER", "http")
            .withEnv("BOT_HTTP_BASE_URL", "http://bot:8080")
            .withEnv("APP_GITHUB_BASE_URL", "http://github-api:8080")
            .withEnv("APP_STACKOVERFLOW_BASE_URL", "http://stackoverflow-api:8080")
            .withEnv("APP_SCHEDULER_DELAY", "2000")
            .withEnv("APP_SCHEDULER_INITIAL_DELAY", "1000")
            .withExposedPorts(8081)
            .waitingFor(Wait.forHttp("/actuator/health").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(5)));

    private static final GenericContainer<?> botContainer = new GenericContainer<>(
                    DockerImageName.parse("link-tracker/bot:latest"))
            .withImagePullPolicy(_ -> false)
            .withNetwork(NETWORK)
            .withNetworkAliases("bot")
            .withEnv("SPRING_PROFILES_ACTIVE", "test")
            .withEnv("COMMUNICATION_PROTOCOL", "http")
            .withEnv("APP_COMMUNICATION_CLIENT", "http")
            .withEnv("APP_COMMUNICATION_SERVER", "http")
            .withEnv("SCRAPPER_HTTP_BASE_URL", "http://scrapper:8081")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/actuator/health").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(5)));

    @BeforeAll
    public static void startContainers() {
        githubContainer.start();
        stackoverflowContainer.start();
        scrapperContainer.start();
        botContainer.start();

        botBaseUrl = "http://" + botContainer.getHost() + ":" + botContainer.getMappedPort(8080);
        scrapperBaseUrl = "http://" + scrapperContainer.getHost() + ":" + scrapperContainer.getMappedPort(8081);
    }

    @BeforeEach
    void setUpWiremock() {
        // Сбрасываем все stubs перед каждым тестом
        WireMock.configureFor(githubContainer.getHost(), githubContainer.getMappedPort(8080));
        WireMock.reset();

        WireMock.configureFor(stackoverflowContainer.getHost(), stackoverflowContainer.getMappedPort(8080));
        WireMock.reset();
    }

    @AfterAll
    static void tearDown() {
        botContainer.stop();
        scrapperContainer.stop();
        stackoverflowContainer.stop();
        githubContainer.stop();
        NETWORK.close();
    }

    @Test
    void testSchedulerGithub() {
        // Настраиваем WireMock для GitHub с будущей датой
        WireMock.configureFor(githubContainer.getHost(), githubContainer.getMappedPort(8080));

        // Будущая дата, чтобы гарантировать изменение
        String futureDate = Instant.now().plus(1, ChronoUnit.DAYS).toString();

        stubFor(get(urlPathMatching("/repos/test/repo"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(String.format("""
                                {
                                  "updated_at": "%s"
                                }
                                """, futureDate))));

        long chatId = System.currentTimeMillis(); // Уникальный ID для каждого теста
        registerChat(chatId);

        // Добавляем ссылку на GitHub
        postLink(chatId, new AddLinkRequest("https://github.com/test/repo", List.of(), List.of()));

        // Ждем, пока scheduler отработает
        await().atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofSeconds(2))
                .untilAsserted(() -> {
                    // Проверяем, что WireMock получил запрос
                    WireMock.configureFor(githubContainer.getHost(), githubContainer.getMappedPort(8080));
                    verify(getRequestedFor(urlPathMatching("/repos/test/repo")));

                    // Проверяем, что бот получил обновление
                    ResponseEntity<String> botHealth =
                            restTemplate.getForEntity(botBaseUrl + "/actuator/health", String.class);
                    assertThat(botHealth.getStatusCode()).isEqualTo(HttpStatus.OK);

                    // Можно также проверить через логи, но это менее надежно
                    String botLogs = botContainer.getLogs();
                    assertThat(botLogs).contains("Received link update");
                });
    }

    @Test
    void testSchedulerStackoverflow() {
        // Настраиваем WireMock для StackOverflow с будущей датой
        WireMock.configureFor(stackoverflowContainer.getHost(), stackoverflowContainer.getMappedPort(8080));

        // Будущая дата в Unix timestamp
        long futureTimestamp = Instant.now().plus(1, ChronoUnit.DAYS).getEpochSecond();

        // stub с учетом префикса и query-параметров
        stubFor(get(urlMatching(".*/questions/123.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(String.format("""
                {
                  "items": [
                    {
                      "last_activity_date": %d
                    }
                  ]
                }
            """, futureTimestamp))));

        long chatId = System.currentTimeMillis() + 1000; // уникальный ID для теста
        registerChat(chatId);

        // Добавляем ссылку на StackOverflow
        postLink(chatId, new AddLinkRequest("https://stackoverflow.com/questions/123", List.of(), List.of()));

        // Ждем, пока scheduler отработает
        await().atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofSeconds(2))
                .untilAsserted(() -> {
                    // Проверяем, что WireMock получил запрос
                    WireMock.configureFor(stackoverflowContainer.getHost(), stackoverflowContainer.getMappedPort(8080));
                    verify(getRequestedFor(urlMatching(".*/questions/123.*")));

                    // Проверяем, что бот получил обновление
                    String botLogs = botContainer.getLogs();
                    assertThat(botLogs).contains("Received link update");
                });
    }

    private static void registerChat(Long chatId) {
        ResponseEntity<Void> response =
                restTemplate.postForEntity(scrapperBaseUrl + "/tg-chat/" + chatId, null, Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private static void postLink(Long chatId, AddLinkRequest request) {
        ResponseEntity<Object> response = restTemplate.postForEntity(
                scrapperBaseUrl + "/links", new HttpEntity<>(request, headersForChat(chatId)), Object.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private static HttpHeaders headersForChat(Long chatId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Tg-Chat-Id", chatId.toString());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
