package backend.academy.linktracker.scrapper.client;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import backend.academy.linktracker.scrapper.dto.response.StackoverflowResponse;
import backend.academy.linktracker.scrapper.properties.StackoverflowProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.*;

class StackoverflowClientTest {

    private static final int WIREMOCK_PORT = 8090;

    private WireMockServer wireMockServer;
    private StackoverflowClient client;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WIREMOCK_PORT);
        wireMockServer.start();
        configureFor("localhost", WIREMOCK_PORT);

        StackoverflowProperties properties = new StackoverflowProperties();
        properties.setBaseUrl("http://localhost:" + WIREMOCK_PORT);
        client = new StackoverflowClient(properties);

        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void testGetLastActivitySuccess() throws Exception {
        String questionId = "12345";
        long lastActivity = System.currentTimeMillis() / 1000;

        StackoverflowResponse.Item item = new StackoverflowResponse.Item(12345, lastActivity);
        StackoverflowResponse response = new StackoverflowResponse(List.of(item));

        wireMockServer.stubFor(get(urlEqualTo("/2.3/questions/" + questionId + "?site=stackoverflow"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(response))));

        Optional<Long> result = client.getLastActivity(questionId);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(lastActivity);

        wireMockServer.verify(getRequestedFor(urlEqualTo("/2.3/questions/" + questionId + "?site=stackoverflow")));
    }

    @Test
    void testGetLastActivityNotFound() {
        wireMockServer.stubFor(get(urlEqualTo("/2.3/questions/99999?site=stackoverflow"))
                .willReturn(aResponse().withStatus(404)));

        Optional<Long> result = client.getLastActivity("99999");

        assertThat(result).isEmpty();
    }
}
