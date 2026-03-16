package backend.academy.linktracker.scrapper.client;

import backend.academy.linktracker.scrapper.dto.response.GithubResponse;
import backend.academy.linktracker.scrapper.properties.GithubProperties;
import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class GithubClient {

    private final RestClient restClient;

    public GithubClient(GithubProperties props) {
        this.restClient = RestClient.builder().baseUrl(props.getBaseUrl()).build();
    }

    public Optional<OffsetDateTime> getLastUpdate(String owner, String repo) {
        log.atDebug().addKeyValue("owner", owner).addKeyValue("repo", repo).log("Requesting GitHub repository");

        try {
            GithubResponse response = restClient
                    .get()
                    .uri("/repos/{owner}/{repo}", owner, repo)
                    .retrieve()
                    .body(GithubResponse.class);

            return Optional.ofNullable(response).map(GithubResponse::updatedAt).map(updatedAt -> {
                log.atDebug()
                        .addKeyValue("owner", owner)
                        .addKeyValue("repo", repo)
                        .addKeyValue("updatedAt", updatedAt)
                        .log("GitHub repository last update retrieved");
                return updatedAt;
            });
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                log.atWarn()
                        .addKeyValue("owner", owner)
                        .addKeyValue("repo", repo)
                        .log("GitHub repository not found");
                return Optional.empty();
            } else {
                log.atError()
                        .addKeyValue("owner", owner)
                        .addKeyValue("repo", repo)
                        .addKeyValue("status", e.getStatusCode())
                        .log("GitHub request failed with error");
                throw e; // Пробрасываем дальше
            }
        }
    }
}
