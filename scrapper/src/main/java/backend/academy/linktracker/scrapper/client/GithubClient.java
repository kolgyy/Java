package backend.academy.linktracker.scrapper.client;

import backend.academy.linktracker.scrapper.dto.response.GithubResponse;
import backend.academy.linktracker.scrapper.properties.GithubProperties;
import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class GithubClient {

    private final RestClient restClient;

    public GithubClient(GithubProperties props) {
        this.restClient = RestClient.builder().baseUrl(props.getBaseUrl()).build();
    }

    public Optional<OffsetDateTime> getLastUpdate(String owner, String repo) {

        log.atDebug().addKeyValue("owner", owner).addKeyValue("repo", repo).log("Requesting GitHub repository");

        GithubResponse response = restClient
                .get()
                .uri("/repos/{owner}/{repo}", owner, repo)
                .retrieve()
                .body(GithubResponse.class);

        Optional<OffsetDateTime> updatedAtOpt = Optional.ofNullable(response).map(GithubResponse::updatedAt);

        updatedAtOpt.ifPresentOrElse(
                updatedAt -> log.atDebug()
                        .addKeyValue("owner", owner)
                        .addKeyValue("repo", repo)
                        .addKeyValue("updatedAt", updatedAt)
                        .log("GitHub repository last update retrieved"),
                () -> log.atWarn()
                        .addKeyValue("owner", owner)
                        .addKeyValue("repo", repo)
                        .log("GitHub repository last update not found"));

        return updatedAtOpt;
    }
}
