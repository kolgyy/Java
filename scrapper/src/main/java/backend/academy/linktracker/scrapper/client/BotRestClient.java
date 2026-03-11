package backend.academy.linktracker.scrapper.client;

import backend.academy.linktracker.scrapper.dto.LinkUpdate;
import backend.academy.linktracker.scrapper.properties.BotHttpProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "app.communication.client", havingValue = "http")
public class BotRestClient implements BotClient {

    private final RestClient restClient;

    public BotRestClient(BotHttpProperties props) {
        this.restClient = RestClient.builder().baseUrl(props.baseUrl()).build();
    }

    public void sendUpdate(LinkUpdate request) {

        restClient.post().uri("/updates").body(request).retrieve().toBodilessEntity();
    }
}
