package backend.academy.linktracker.scrapper.client;

import backend.academy.linktracker.scrapper.dto.response.StackoverflowResponse;
import backend.academy.linktracker.scrapper.properties.StackoverflowProperties;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class StackoverflowClient {

    private final RestClient restClient;

    public StackoverflowClient(StackoverflowProperties props) {
        this.restClient = RestClient.builder().baseUrl(props.getBaseUrl()).build();
    }

    public Optional<Long> getLastActivity(String questionId) {
        log.atDebug().addKeyValue("questionId", questionId).log("Requesting StackOverflow question");

        try {
            StackoverflowResponse response = restClient
                    .get()
                    .uri("/2.3/questions/{id}?site=stackoverflow", questionId)
                    .retrieve()
                    .body(StackoverflowResponse.class);

            Optional<Long> lastActivityOpt = Optional.ofNullable(response)
                    .filter(r -> r.items() != null && !r.items().isEmpty())
                    .map(r -> r.items().getFirst().lastActivityDate());

            lastActivityOpt.ifPresentOrElse(
                    lastActivity -> log.atDebug()
                            .addKeyValue("questionId", questionId)
                            .addKeyValue("lastActivity", lastActivity)
                            .log("StackOverflow last activity retrieved"),
                    () -> log.atWarn().addKeyValue("questionId", questionId).log("No StackOverflow question found"));

            return lastActivityOpt;

        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                log.atWarn().addKeyValue("questionId", questionId).log("StackOverflow question not found (404)");
                return Optional.empty();
            } else {
                log.atError()
                        .addKeyValue("questionId", questionId)
                        .addKeyValue("status", e.getStatusCode())
                        .log("StackOverflow request failed with error");
                throw e; // пробрасываем остальные ошибки дальше
            }
        }
    }
}
