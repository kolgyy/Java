package backend.academy.linktracker.bot.client;

import backend.academy.linktracker.bot.configuration.ScrapperHttpProperties;
import backend.academy.linktracker.bot.dto.request.AddLinkRequest;
import backend.academy.linktracker.bot.dto.request.RemoveLinkRequest;
import backend.academy.linktracker.bot.dto.response.LinkResponse;
import backend.academy.linktracker.bot.dto.response.ListLinksResponse;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "app.communication.client", havingValue = "http")
public class ScrapperRestClient implements ScrapperClient {

    private final RestClient restClient;

    public ScrapperRestClient(ScrapperHttpProperties props) {
        this.restClient = RestClient.builder().baseUrl(props.baseUrl()).build();
    }

    @Override
    public void registerChat(long chatId) {
        restClient.post().uri("/tg-chat/{id}", chatId).retrieve().toBodilessEntity();
    }

    @Override
    public void removeChat(long chatId) {
        restClient.delete().uri("/tg-chat/{id}", chatId).retrieve().toBodilessEntity();
    }

    @Override
    public Optional<ListLinksResponse> getLinks(long chatId) {
        return Optional.ofNullable(restClient
                .get()
                .uri("/links")
                .header("Tg-Chat-Id", String.valueOf(chatId))
                .retrieve()
                .body(ListLinksResponse.class));
    }

    @Override
    public Optional<LinkResponse> addLink(long chatId, AddLinkRequest request) {
        return Optional.ofNullable(restClient
                .post()
                .uri("/links")
                .header("Tg-Chat-Id", String.valueOf(chatId))
                .body(request)
                .retrieve()
                .body(LinkResponse.class));
    }

    @Override
    public Optional<LinkResponse> removeLink(long chatId, RemoveLinkRequest request) {
        return Optional.ofNullable(restClient
                .method(HttpMethod.DELETE)
                .uri("/links")
                .header("Tg-Chat-Id", String.valueOf(chatId))
                .body(request)
                .retrieve()
                .body(LinkResponse.class));
    }
}
