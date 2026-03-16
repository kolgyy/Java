package backend.academy.linktracker.scrapper.client;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import backend.academy.linktracker.scrapper.dto.response.GithubResponse;
import backend.academy.linktracker.scrapper.properties.GithubProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.*;

class GithubClientTest {

    private static final int WIREMOCK_PORT = 8089;

    private WireMockServer wireMockServer;
    private GithubClient githubClient;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Настройка WireMock
        wireMockServer = new WireMockServer(WIREMOCK_PORT);
        wireMockServer.start();
        configureFor("localhost", WIREMOCK_PORT);

        // Настройка клиента
        GithubProperties properties = new GithubProperties();
        properties.setBaseUrl("http://localhost:" + WIREMOCK_PORT);
        githubClient = new GithubClient(properties);

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void testGetLastUpdateSuccess() throws Exception {
        String owner = "test-owner";
        String repo = "test-repo";
        OffsetDateTime now = OffsetDateTime.now();

        wireMockServer.stubFor(get(urlEqualTo("/repos/" + owner + "/" + repo))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(new GithubResponse(now)))));

        Optional<OffsetDateTime> result = githubClient.getLastUpdate(owner, repo);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(now);

        wireMockServer.verify(getRequestedFor(urlEqualTo("/repos/" + owner + "/" + repo)));
    }

    @Test
    void testGetLastUpdateNotFound() {
        wireMockServer.stubFor(get(urlEqualTo("/repos/non-existent/test-repo"))
                .willReturn(aResponse().withStatus(404)));

        Optional<OffsetDateTime> result = githubClient.getLastUpdate("non-existent", "test-repo");

        assertThat(result).isEmpty();
    }
}
