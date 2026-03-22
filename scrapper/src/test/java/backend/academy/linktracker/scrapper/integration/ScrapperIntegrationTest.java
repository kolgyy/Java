package backend.academy.linktracker.scrapper.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import backend.academy.linktracker.scrapper.dto.request.AddLinkRequest;
import backend.academy.linktracker.scrapper.dto.request.RemoveLinkRequest;
import backend.academy.linktracker.scrapper.dto.response.LinkResponse;
import backend.academy.linktracker.scrapper.dto.response.ListLinksResponse;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public class ScrapperIntegrationTest {

    private static final Network NETWORK = Network.newNetwork();

    @Container
    private static final GenericContainer<?> githubContainer = new GenericContainer<>("wiremock/wiremock:3.5.2")
            .withNetwork(NETWORK)
            .withNetworkAliases("github-api")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/__admin").forStatusCode(200));

    @Container
    private static final GenericContainer<?> stackoverflowContainer = new GenericContainer<>("wiremock/wiremock:3.5.2")
            .withNetwork(NETWORK)
            .withNetworkAliases("stackoverflow-api")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/__admin").forStatusCode(200));

    @Container
    private static final GenericContainer<?> scrapperContainer = new GenericContainer<>(
                    DockerImageName.parse("link-tracker/scrapper:latest"))
            .withImagePullPolicy(_ -> false)
            .withNetwork(NETWORK)
            .withNetworkAliases("scrapper")
            .dependsOn(githubContainer, stackoverflowContainer)
            .withEnv("SPRING_PROFILES_ACTIVE", "test")
            .withEnv("COMMUNICATION_PROTOCOL", "http")
            .withExposedPorts(8081)
            .waitingFor(Wait.forHttp("/actuator/health").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(10)));

    private RestTemplate restTemplate;
    private String baseUrl;

    @BeforeAll
    public static void startContainers() {
        githubContainer.start();
        stackoverflowContainer.start();
        scrapperContainer.start();
    }

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        baseUrl = "http://" + scrapperContainer.getHost() + ":" + scrapperContainer.getMappedPort(8081);

        WireMock githubMock = new WireMock(githubContainer.getHost(), githubContainer.getMappedPort(8080));
        githubMock.register(get(anyUrl())
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"updated_at\":\"2026-03-17T00:00:00Z\"}")));

        WireMock stackoverflowMock =
                new WireMock(stackoverflowContainer.getHost(), stackoverflowContainer.getMappedPort(8080));
        stackoverflowMock.register(get(anyUrl())
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"items\":[{\"last_activity_date\":1700000000}]}")));
    }

    @AfterAll
    static void tearDown() {
        scrapperContainer.stop();
        stackoverflowContainer.stop();
        githubContainer.stop();
        NETWORK.close();
    }

    @Test
    void testAddAndGetLink() {
        long chatId = System.nanoTime();

        restTemplate.postForEntity(baseUrl + "/tg-chat/" + chatId, null, Void.class);

        AddLinkRequest addRequest = new AddLinkRequest("https://github.com/test/repo", List.of(), List.of());

        HttpHeaders headers = new HttpHeaders();
        headers.set("Tg-Chat-Id", String.valueOf(chatId));
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<LinkResponse> addResponse = restTemplate.exchange(
                baseUrl + "/links", HttpMethod.POST, new HttpEntity<>(addRequest, headers), LinkResponse.class);

        assertThat(addResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(addResponse.getBody().url()).isEqualTo("https://github.com/test/repo");

        ResponseEntity<ListLinksResponse> getResponse = restTemplate.exchange(
                baseUrl + "/links", HttpMethod.GET, new HttpEntity<>(headers), ListLinksResponse.class);

        assertThat(getResponse.getBody().links()).hasSize(1);
    }

    @Test
    void testAddAndDeleteLink() {
        long chatId = System.nanoTime();

        restTemplate.postForEntity(baseUrl + "/tg-chat/" + chatId, null, Void.class);

        AddLinkRequest addRequest = new AddLinkRequest("https://github.com/test/repo", List.of(), List.of());

        HttpHeaders headers = new HttpHeaders();
        headers.set("Tg-Chat-Id", String.valueOf(chatId));
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<LinkResponse> addResponse = restTemplate.exchange(
                baseUrl + "/links", HttpMethod.POST, new HttpEntity<>(addRequest, headers), LinkResponse.class);

        long linkId = addResponse.getBody().id();

        RemoveLinkRequest removeRequest = new RemoveLinkRequest("https://github.com/test/repo");

        ResponseEntity<LinkResponse> deleteResponse = restTemplate.exchange(
                baseUrl + "/links", HttpMethod.DELETE, new HttpEntity<>(removeRequest, headers), LinkResponse.class);

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ListLinksResponse> getResponse = restTemplate.exchange(
                baseUrl + "/links", HttpMethod.GET, new HttpEntity<>(headers), ListLinksResponse.class);

        assertThat(getResponse.getBody().links()).hasSize(0);
    }

    @Test
    void testAddLinkToNonExistentChat() {
        long chatId = System.nanoTime();

        AddLinkRequest addRequest = new AddLinkRequest("https://github.com/test/repo", List.of(), List.of());

        HttpHeaders headers = new HttpHeaders();
        headers.set("Tg-Chat-Id", String.valueOf(chatId));
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpClientErrorException exception = org.junit.jupiter.api.Assertions.assertThrows(
                HttpClientErrorException.class,
                () -> restTemplate.exchange(
                        baseUrl + "/links", HttpMethod.POST, new HttpEntity<>(addRequest, headers), String.class));

        assertThat(exception.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void testDeleteFromAnotherChat() {
        long chat1 = System.nanoTime();
        long chat2 = chat1 + 1;

        // Регистрируем первый чат
        restTemplate.postForEntity(baseUrl + "/tg-chat/" + chat1, null, Void.class);

        // Добавляем ссылку в первый чат
        AddLinkRequest request = new AddLinkRequest("https://github.com/test/repo", List.of(), List.of());

        HttpHeaders headers1 = new HttpHeaders();
        headers1.set("Tg-Chat-Id", String.valueOf(chat1));
        headers1.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.exchange(
                baseUrl + "/links", HttpMethod.POST, new HttpEntity<>(request, headers1), LinkResponse.class);

        // Пытаемся удалить ссылку из другого чата
        HttpHeaders headers2 = new HttpHeaders();
        headers2.set("Tg-Chat-Id", String.valueOf(chat2));
        headers2.setContentType(MediaType.APPLICATION_JSON);

        RemoveLinkRequest removeRequest = new RemoveLinkRequest("https://github.com/test/repo");

        // Проверяем, что получили ошибку
        assertThatThrownBy(() -> restTemplate.exchange(
                        baseUrl + "/links", HttpMethod.DELETE, new HttpEntity<>(removeRequest, headers2), Void.class))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(ex -> {
                    HttpClientErrorException httpEx = (HttpClientErrorException) ex;
                    assertThat(httpEx.getStatusCode().is4xxClientError()).isTrue();
                });

        // Проверяем, что ссылка всё ещё есть в первом чате
        ResponseEntity<ListLinksResponse> getResponse = restTemplate.exchange(
                baseUrl + "/links", HttpMethod.GET, new HttpEntity<>(headers1), ListLinksResponse.class);

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().links()).hasSize(1);
        assertThat(getResponse.getBody().links().get(0).url()).isEqualTo("https://github.com/test/repo");
    }

    @Test
    void testAddLinkToDeletedChat() {
        long chatId = System.nanoTime();

        // Регистрируем чат и сразу удаляем
        restTemplate.postForEntity(baseUrl + "/tg-chat/" + chatId, null, Void.class);
        restTemplate.exchange(baseUrl + "/tg-chat/" + chatId, HttpMethod.DELETE, null, Void.class);

        // Пытаемся добавить ссылку в удалённый чат
        AddLinkRequest request = new AddLinkRequest("https://github.com/test/repo", List.of(), List.of());

        HttpHeaders headers = new HttpHeaders();
        headers.set("Tg-Chat-Id", String.valueOf(chatId));
        headers.setContentType(MediaType.APPLICATION_JSON);

        // AssertJ проверка
        assertThatThrownBy(() -> restTemplate.exchange(
                        baseUrl + "/links", HttpMethod.POST, new HttpEntity<>(request, headers), Void.class))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(ex -> {
                    HttpClientErrorException httpEx = (HttpClientErrorException) ex;
                    assertThat(httpEx.getStatusCode().is4xxClientError()).isTrue();
                });
    }

    @Test
    void testDeleteNonExistentChat() {
        long chatId = System.nanoTime();

        HttpClientErrorException exception = org.junit.jupiter.api.Assertions.assertThrows(
                HttpClientErrorException.class,
                () -> restTemplate.exchange(baseUrl + "/tg-chat/" + chatId, HttpMethod.DELETE, null, String.class));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
