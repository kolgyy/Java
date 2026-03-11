package backend.academy.linktracker.scrapper.grpc.client;

import backend.academy.linktracker.scrapper.client.BotClient;
import backend.academy.linktracker.scrapper.dto.LinkUpdate;
import backend.academy.linktracker.scrapper.grpc.bot.BotServiceGrpc;
import backend.academy.linktracker.scrapper.grpc.bot.LinkUpdateRequest;
import io.grpc.ManagedChannel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.communication.client", havingValue = "grpc")
public class BotGrpcClient implements BotClient {

    private final BotServiceGrpc.BotServiceBlockingStub stub;

    public BotGrpcClient(ManagedChannel botChannel) {
        this.stub = BotServiceGrpc.newBlockingStub(botChannel);
    }

    @Override
    public void sendUpdate(LinkUpdate update) {
        LinkUpdateRequest request = LinkUpdateRequest.newBuilder()
                .setId(update.id())
                .setUrl(update.url())
                .setDescription(update.description())
                .addAllTgChatIds(update.tgChatIds())
                .build();
        stub.sendUpdate(request);
    }
}
