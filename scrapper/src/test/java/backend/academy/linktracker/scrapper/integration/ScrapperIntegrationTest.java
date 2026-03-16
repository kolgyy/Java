package backend.academy.linktracker.scrapper.integration;

import static org.assertj.core.api.Assertions.assertThat;

import backend.academy.linktracker.scrapper.dto.request.AddLinkRequest;
import backend.academy.linktracker.scrapper.dto.response.LinkResponse;
import backend.academy.linktracker.scrapper.dto.response.ListLinksResponse;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class ScrapperIntegrationTest {

    private static final Network NETWORK = Network.newNetwork();

    @Container
    @ServiceConnection
    private static final GenericContainer<?> scrapperContainer = new GenericContainer<>(
                    DockerImageName.parse("link-tracker/scrapper:latest"))
            .withNetwork(NETWORK)
            .withNetworkAliases("scrapper")
            .withEnv("SERVER_PORT", "8081")
            .withEnv("SPRING_PROFILES_ACTIVE", "test")
            .withEnv("BOT_HTTP_BASE_URL", "http://bot:8080")
            .withExposedPorts(8081);

    private RestTemplate restTemplate;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        Integer mappedPort = scrapperContainer.getMappedPort(8081);
        baseUrl = "http://" + scrapperContainer.getHost() + ":" + mappedPort;
    }

    // Добавление и получение ссылки
    @Test
    void testAddAndGetLink() {
        // Регистрация чата
        restTemplate.postForEntity(baseUrl + "/tg-chat/1", null, Void.class);

        // Добавление ссылки
        AddLinkRequest addRequest = new AddLinkRequest("https://github.com/test/repo", List.of("test"), List.of());
        HttpHeaders headers = new HttpHeaders();
        headers.set("Tg-Chat-Id", "1");
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<LinkResponse> addResponse = restTemplate.exchange(
                baseUrl + "/links", HttpMethod.POST, new HttpEntity<>(addRequest, headers), LinkResponse.class);

        assertThat(addResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Objects.requireNonNull(addResponse.getBody()).url()).isEqualTo("https://github.com/test/repo");

        // Получение списка ссылок
        ResponseEntity<ListLinksResponse> getResponse = restTemplate.exchange(
                baseUrl + "/links", HttpMethod.GET, new HttpEntity<>(headers), ListLinksResponse.class);

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Objects.requireNonNull(getResponse.getBody()).size()).isEqualTo(1);
    }

    // Добавление и удаление ссылки
    @Test
    void testAddAndDeleteLink() {
        restTemplate.postForEntity(baseUrl + "/tg-chat/1", null, Void.class);

        AddLinkRequest addRequest = new AddLinkRequest("https://github.com/test/repo", List.of("test"), List.of());
        HttpHeaders headers = new HttpHeaders();
        headers.set("Tg-Chat-Id", "1");
        headers.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.exchange(
                baseUrl + "/links", HttpMethod.POST, new HttpEntity<>(addRequest, headers), LinkResponse.class);

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                baseUrl + "/links", HttpMethod.DELETE, new HttpEntity<>(addRequest, headers), Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ListLinksResponse> getResponse = restTemplate.exchange(
                baseUrl + "/links", HttpMethod.GET, new HttpEntity<>(headers), ListLinksResponse.class);
        assertThat(getResponse.getBody().size()).isEqualTo(0);
    }

    // Попытка добавления ссылки в несуществующий чат
    @Test
    void testAddLinkToNonExistentChat() {
        AddLinkRequest addRequest = new AddLinkRequest("https://github.com/test/repo", List.of("test"), List.of());
        HttpHeaders headers = new HttpHeaders();
        headers.set("Tg-Chat-Id", "999");
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> addResponse = restTemplate.exchange(
                baseUrl + "/links", HttpMethod.POST, new HttpEntity<>(addRequest, headers), String.class);

        assertThat(addResponse.getStatusCode().is4xxClientError()).isTrue();
    }

    // Попытка удаления ссылки из несуществующего чата
    @Test
    void testDeleteLinkFromNonExistentChat() {
        AddLinkRequest deleteRequest = new AddLinkRequest("https://github.com/test/repo", List.of("test"), List.of());
        HttpHeaders headers = new HttpHeaders();
        headers.set("Tg-Chat-Id", "999");
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> deleteResponse = restTemplate.exchange(
                baseUrl + "/links", HttpMethod.DELETE, new HttpEntity<>(deleteRequest, headers), String.class);

        assertThat(deleteResponse.getStatusCode().is4xxClientError()).isTrue();
    }

    // Работа с удалённым чатом
    @Test
    void testAddLinkToDeletedChat() {
        restTemplate.postForEntity(baseUrl + "/tg-chat/1", null, Void.class);
        restTemplate.exchange(baseUrl + "/tg-chat/1", HttpMethod.DELETE, null, Void.class);

        AddLinkRequest addRequest = new AddLinkRequest("https://github.com/test/repo", List.of("test"), List.of());
        HttpHeaders headers = new HttpHeaders();
        headers.set("Tg-Chat-Id", "1");
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> addResponse = restTemplate.exchange(
                baseUrl + "/links", HttpMethod.POST, new HttpEntity<>(addRequest, headers), String.class);

        assertThat(addResponse.getStatusCode().is4xxClientError()).isTrue();
    }

    // Удаление несуществующего чата
    @Test
    void testDeleteNonExistentChat() {
        ResponseEntity<String> deleteResponse =
                restTemplate.exchange(baseUrl + "/tg-chat/999", HttpMethod.DELETE, null, String.class);

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
