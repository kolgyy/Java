package backend.academy.linktracker.scrapper.client;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import backend.academy.linktracker.scrapper.dto.response.GithubResponse;
import backend.academy.linktracker.scrapper.properties.GithubProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
public class GithubClientTest {

    private static WireMockServer wireMockServer;

    @MockitoSpyBean
    private GithubProperties githubProperties;

    private GithubClient githubClient;
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        wireMockServer = new WireMockServer(8089);
        wireMockServer.start();
        WireMock.configureFor("localhost", 8089);
        registry.add("app.github.base-url", () -> "http://localhost:8089");
    }

    @BeforeEach
    void setUp() {
        when(githubProperties.getBaseUrl()).thenReturn("http://localhost:8089");
        githubClient = new GithubClient(githubProperties);

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule()); // Для поддержки OffsetDateTime
    }

    @AfterEach
    void tearDown() {
        wireMockServer.resetAll();
        wireMockServer.stop();
    }

    @Test
    void testGetLastUpdateSuccess() throws Exception {
        // Arrange
        String owner = "test-owner";
        String repo = "test-repo";
        OffsetDateTime now = OffsetDateTime.now();

        GithubResponse response = new GithubResponse(now);

        stubFor(get(urlEqualTo("/repos/" + owner + "/" + repo))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(objectMapper.writeValueAsString(response))));

        // Act
        Optional<OffsetDateTime> result = githubClient.getLastUpdate(owner, repo);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(now);

        verify(getRequestedFor(urlEqualTo("/repos/" + owner + "/" + repo)));
    }

    @Test
    void testGetLastUpdateNotFound() {
        // Arrange
        String owner = "test-owner";
        String repo = "non-existent";

        stubFor(get(urlEqualTo("/repos/" + owner + "/" + repo))
                .willReturn(aResponse().withStatus(HttpStatus.NOT_FOUND.value())));

        // Act
        Optional<OffsetDateTime> result = githubClient.getLastUpdate(owner, repo);

        // Assert
        assertThat(result).isEmpty();

        verify(getRequestedFor(urlEqualTo("/repos/" + owner + "/" + repo)));
    }

    @Test
    void testGetLastUpdateServerError() {
        // Arrange
        String owner = "test-owner";
        String repo = "test-repo";

        stubFor(get(urlEqualTo("/repos/" + owner + "/" + repo))
                .willReturn(aResponse().withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())));

        // Act
        Optional<OffsetDateTime> result = githubClient.getLastUpdate(owner, repo);

        // Assert
        assertThat(result).isEmpty();

        verify(getRequestedFor(urlEqualTo("/repos/" + owner + "/" + repo)));
    }

    @Test
    void testGetLastUpdateInvalidResponseBody() {
        // Arrange
        String owner = "test-owner";
        String repo = "test-repo";

        stubFor(get(urlEqualTo("/repos/" + owner + "/" + repo))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"invalid\": \"response\"}")));

        // Act
        Optional<OffsetDateTime> result = githubClient.getLastUpdate(owner, repo);

        // Assert
        assertThat(result).isEmpty();

        verify(getRequestedFor(urlEqualTo("/repos/" + owner + "/" + repo)));
    }
}
