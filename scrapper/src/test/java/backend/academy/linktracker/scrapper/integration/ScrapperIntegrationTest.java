package backend.academy.linktracker.scrapper.integration;

import static org.assertj.core.api.Assertions.assertThat;

import backend.academy.linktracker.scrapper.dto.request.AddLinkRequest;
import backend.academy.linktracker.scrapper.dto.response.LinkResponse;
import backend.academy.linktracker.scrapper.dto.response.ListLinksResponse;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@WireMockTest
public class ScrapperIntegrationTest {

    @LocalServerPort
    private int port;

    private RestTemplate restTemplate;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        baseUrl = "http://localhost:" + port;
    }

    @Test
    void testRegisterChatAndAddLink() {
        // 1. Регистрация чата
        ResponseEntity<Void> registerResponse = restTemplate.postForEntity(baseUrl + "/tg-chat/1", null, Void.class);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 2. Добавление ссылки
        AddLinkRequest addRequest =
                new AddLinkRequest("https://github.com/test/repo", List.of("test", "java"), List.of());

        HttpHeaders headers = new HttpHeaders();
        headers.set("Tg-Chat-Id", "1");
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<AddLinkRequest> requestEntity = new HttpEntity<>(addRequest, headers);

        ResponseEntity<LinkResponse> addResponse =
                restTemplate.exchange(baseUrl + "/links", HttpMethod.POST, requestEntity, LinkResponse.class);

        assertThat(addResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Objects.requireNonNull(addResponse.getBody()).url()).isEqualTo("https://github.com/test/repo");

        // 3. Получение списка ссылок
        HttpHeaders getHeaders = new HttpHeaders();
        getHeaders.set("Tg-Chat-Id", "1");

        HttpEntity<Void> getEntity = new HttpEntity<>(getHeaders);

        ResponseEntity<ListLinksResponse> getResponse =
                restTemplate.exchange(baseUrl + "/links", HttpMethod.GET, getEntity, ListLinksResponse.class);

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Objects.requireNonNull(getResponse.getBody()).size()).isEqualTo(1);
    }

    @Test
    void testAddLinkToNonExistentChat() {
        // Регистрация чата 1
        restTemplate.postForEntity(baseUrl + "/tg-chat/1", null, Void.class);

        // Попытка добавить ссылку в несуществующий чат 2
        AddLinkRequest addRequest = new AddLinkRequest("https://github.com/test/repo", List.of("test"), List.of());

        HttpHeaders headers = new HttpHeaders();
        headers.set("Tg-Chat-Id", "2");
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<AddLinkRequest> requestEntity = new HttpEntity<>(addRequest, headers);

        ResponseEntity<String> addResponse =
                restTemplate.exchange(baseUrl + "/links", HttpMethod.POST, requestEntity, String.class);

        assertThat(addResponse.getStatusCode()).isNotEqualTo(HttpStatus.OK);
        assertThat(addResponse.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void testDeleteNonExistentChat() {
        ResponseEntity<String> deleteResponse =
                restTemplate.exchange(baseUrl + "/tg-chat/999", HttpMethod.DELETE, null, String.class);

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
