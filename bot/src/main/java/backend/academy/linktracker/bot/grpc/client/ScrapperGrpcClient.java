package backend.academy.linktracker.bot.grpc.client;

import backend.academy.linktracker.bot.client.ScrapperClient;
import backend.academy.linktracker.bot.dto.request.AddLinkRequest;
import backend.academy.linktracker.bot.dto.request.RemoveLinkRequest;
import backend.academy.linktracker.bot.dto.response.LinkResponse;
import backend.academy.linktracker.bot.dto.response.ListLinksResponse;
import backend.academy.linktracker.bot.grpc.scrapper.ChatRequest;
import backend.academy.linktracker.bot.grpc.scrapper.ScrapperServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.communication.client", havingValue = "grpc")
public class ScrapperGrpcClient implements ScrapperClient {

    private final ScrapperServiceGrpc.ScrapperServiceBlockingStub stub;

    public ScrapperGrpcClient(ManagedChannel scrapperChannel) {
        this.stub = ScrapperServiceGrpc.newBlockingStub(scrapperChannel);
    }

    @Override
    public void registerChat(long chatId) {
        try {
            stub.registerChat(ChatRequest.newBuilder().setChatId(chatId).build());
        } catch (StatusRuntimeException e) {
            logGrpcError("registerChat", e, "chatId", chatId);
        }
    }

    @Override
    public void removeChat(long chatId) {
        try {
            stub.removeChat(ChatRequest.newBuilder().setChatId(chatId).build());
        } catch (StatusRuntimeException e) {
            logGrpcError("removeChat", e, "chatId", chatId);
        }
    }

    @Override
    public Optional<ListLinksResponse> getLinks(long chatId) {
        try {
            var grpcRequest = ChatRequest.newBuilder().setChatId(chatId).build();
            var grpcResponse = stub.getLinks(grpcRequest);

            var links = grpcResponse.getLinksList().stream()
                    .map(grpcLink -> new LinkResponse(
                            grpcLink.getId(), grpcLink.getUrl(), grpcLink.getTagsList(), grpcLink.getFiltersList()))
                    .collect(Collectors.toList());

            return Optional.of(new ListLinksResponse(links, links.size()));
        } catch (StatusRuntimeException e) {
            logGrpcError("getLinks", e, "chatId", chatId);
            return Optional.empty();
        }
    }

    @Override
    public Optional<LinkResponse> addLink(long chatId, AddLinkRequest request) {
        try {
            var grpcRequest = backend.academy.linktracker.bot.grpc.scrapper.AddLinkRequest.newBuilder()
                    .setChatId(chatId)
                    .setLink(request.link())
                    .addAllTags(request.tags())
                    .addAllFilters(request.filters())
                    .build();

            var grpcResponse = stub.addLink(grpcRequest);

            return Optional.of(new LinkResponse(
                    grpcResponse.getId(),
                    grpcResponse.getUrl(),
                    grpcResponse.getTagsList(),
                    grpcResponse.getFiltersList()));
        } catch (StatusRuntimeException e) {
            logGrpcError("addLink", e, "chatId", chatId, "link", request.link());
            return Optional.empty();
        }
    }

    @Override
    public Optional<LinkResponse> removeLink(long chatId, RemoveLinkRequest request) {
        try {
            var grpcRequest = backend.academy.linktracker.bot.grpc.scrapper.RemoveLinkRequest.newBuilder()
                    .setChatId(chatId)
                    .setLink(request.link())
                    .build();

            var grpcResponse = stub.removeLink(grpcRequest);

            return Optional.of(new LinkResponse(
                    grpcResponse.getId(),
                    grpcResponse.getUrl(),
                    grpcResponse.getTagsList(),
                    grpcResponse.getFiltersList()));
        } catch (StatusRuntimeException e) {
            logGrpcError("removeLink", e, "chatId", chatId, "link", request.link());
            return Optional.empty();
        }
    }

    private void logGrpcError(String methodName, StatusRuntimeException e, Object... keyValues) {
        LoggingEventBuilder logBuilder = log.atWarn()
                .addKeyValue("method", methodName)
                .addKeyValue("grpcStatus", e.getStatus().getCode());

        for (int i = 0; i < keyValues.length; i += 2) {
            if (i + 1 < keyValues.length) {
                logBuilder.addKeyValue(String.valueOf(keyValues[i]), keyValues[i + 1]);
            }
        }

        logBuilder.log("gRPC call failed", e);
    }
}
