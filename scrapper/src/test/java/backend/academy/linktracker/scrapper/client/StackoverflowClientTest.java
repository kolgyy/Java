package backend.academy.linktracker.scrapper.client;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import backend.academy.linktracker.scrapper.dto.response.StackoverflowResponse;
import backend.academy.linktracker.scrapper.properties.StackoverflowProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.util.List;
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
public class StackoverflowClientTest {

    private static WireMockServer wireMockServer;

    @MockitoSpyBean
    private StackoverflowProperties stackoverflowProperties;

    private StackoverflowClient stackoverflowClient;
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        wireMockServer = new WireMockServer(8090);
        wireMockServer.start();
        WireMock.configureFor("localhost", 8090);
        registry.add("app.stackoverflow.base-url", () -> "http://localhost:8090");
    }

    @BeforeEach
    void setUp() {
        when(stackoverflowProperties.getBaseUrl()).thenReturn("http://localhost:8090");
        stackoverflowClient = new StackoverflowClient(stackoverflowProperties);
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); // Для поддержки Java 8 времени
    }

    @AfterEach
    void tearDown() {
        wireMockServer.resetAll();
        wireMockServer.stop();
    }

    @Test
    void testGetLastActivitySuccess() throws Exception {
        // Arrange
        String questionId = "12345";
        long lastActivityDate = System.currentTimeMillis() / 1000;

        StackoverflowResponse.Item item = new StackoverflowResponse.Item(12345, lastActivityDate);
        StackoverflowResponse response = new StackoverflowResponse(List.of(item));

        stubFor(get(urlEqualTo("/2.3/questions/" + questionId + "?site=stackoverflow"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(objectMapper.writeValueAsString(response))));

        // Act
        Optional<Long> result = stackoverflowClient.getLastActivity(questionId);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(lastActivityDate);

        verify(getRequestedFor(urlEqualTo("/2.3/questions/" + questionId + "?site=stackoverflow")));
    }

    @Test
    void testGetLastActivityNotFound() {
        // Arrange
        String questionId = "99999";

        stubFor(get(urlEqualTo("/2.3/questions/" + questionId + "?site=stackoverflow"))
                .willReturn(aResponse().withStatus(HttpStatus.NOT_FOUND.value())));

        // Act
        Optional<Long> result = stackoverflowClient.getLastActivity(questionId);

        // Assert
        assertThat(result).isEmpty();

        verify(getRequestedFor(urlEqualTo("/2.3/questions/" + questionId + "?site=stackoverflow")));
    }

    @Test
    void testGetLastActivityServerError() {
        // Arrange
        String questionId = "12345";

        stubFor(get(urlEqualTo("/2.3/questions/" + questionId + "?site=stackoverflow"))
                .willReturn(aResponse().withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())));

        // Act
        Optional<Long> result = stackoverflowClient.getLastActivity(questionId);

        // Assert
        assertThat(result).isEmpty();

        verify(getRequestedFor(urlEqualTo("/2.3/questions/" + questionId + "?site=stackoverflow")));
    }

    @Test
    void testGetLastActivityInvalidResponse() {
        // Arrange
        String questionId = "12345";

        stubFor(get(urlEqualTo("/2.3/questions/" + questionId + "?site=stackoverflow"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"invalid\": \"response\"}")));

        // Act
        Optional<Long> result = stackoverflowClient.getLastActivity(questionId);

        // Assert
        assertThat(result).isEmpty();

        verify(getRequestedFor(urlEqualTo("/2.3/questions/" + questionId + "?site=stackoverflow")));
    }
}
