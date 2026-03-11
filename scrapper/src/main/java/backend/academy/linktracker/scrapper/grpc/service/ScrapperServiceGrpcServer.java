package backend.academy.linktracker.scrapper.grpc.service;

import backend.academy.linktracker.scrapper.grpc.AddLinkRequest;
import backend.academy.linktracker.scrapper.grpc.ChatRequest;
import backend.academy.linktracker.scrapper.grpc.Empty;
import backend.academy.linktracker.scrapper.grpc.LinkResponse;
import backend.academy.linktracker.scrapper.grpc.ListLinksResponse;
import backend.academy.linktracker.scrapper.grpc.RemoveLinkRequest;
import backend.academy.linktracker.scrapper.grpc.ScrapperServiceGrpc;
import backend.academy.linktracker.scrapper.service.LinkService;
import backend.academy.linktracker.scrapper.service.TgChatService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.grpc.server.service.GrpcService;

@Slf4j
@GrpcService
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.communication.server", havingValue = "grpc")
public class ScrapperServiceGrpcServer extends ScrapperServiceGrpc.ScrapperServiceImplBase {

    private final TgChatService tgChatService;
    private final LinkService linkService;

    @Override
    public void registerChat(ChatRequest request, StreamObserver<Empty> responseObserver) {
        tgChatService.registerChat(request.getChatId());
        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void removeChat(ChatRequest request, StreamObserver<Empty> responseObserver) {
        tgChatService.removeChat(request.getChatId());
        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void getLinks(ChatRequest request, StreamObserver<ListLinksResponse> responseObserver) {
        var links = linkService.getLinks(request.getChatId());

        var builder = ListLinksResponse.newBuilder();
        links.forEach(link -> builder.addLinks(LinkResponse.newBuilder()
                .setId(link.getId())
                .setUrl(link.getUrl())
                .addAllTags(link.getTags())
                .addAllFilters(link.getFilters())
                .build()));

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void addLink(AddLinkRequest request, StreamObserver<LinkResponse> responseObserver) {
        var link = linkService.addLink(
                request.getChatId(), request.getLink(), request.getTagsList(), request.getFiltersList());
        var response = LinkResponse.newBuilder()
                .setId(link.getId())
                .setUrl(link.getUrl())
                .addAllTags(link.getTags())
                .addAllFilters(link.getFilters())
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void removeLink(RemoveLinkRequest request, StreamObserver<LinkResponse> responseObserver) {
        var link = linkService.removeLink(request.getChatId(), request.getLink());
        var response = LinkResponse.newBuilder()
                .setId(link.getId())
                .setUrl(link.getUrl())
                .addAllTags(link.getTags())
                .addAllFilters(link.getFilters())
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
