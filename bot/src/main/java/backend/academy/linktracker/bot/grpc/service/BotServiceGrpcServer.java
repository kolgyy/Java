package backend.academy.linktracker.bot.grpc.service;

import backend.academy.linktracker.bot.dto.LinkUpdate;
import backend.academy.linktracker.bot.grpc.BotServiceGrpc;
import backend.academy.linktracker.bot.grpc.Empty;
import backend.academy.linktracker.bot.grpc.LinkUpdateRequest;
import backend.academy.linktracker.bot.service.UpdateService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.grpc.server.service.GrpcService;

@Slf4j
@GrpcService
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.communication.server", havingValue = "grpc")
public class BotServiceGrpcServer extends BotServiceGrpc.BotServiceImplBase {

    private final UpdateService updateService;

    @Override
    public void sendUpdate(LinkUpdateRequest request, StreamObserver<Empty> responseObserver) {
        log.atInfo()
                .addKeyValue("url", request.getUrl())
                .addKeyValue("chatCount", request.getTgChatIdsCount())
                .log("Received gRPC link update");

        updateService.processUpdate(new LinkUpdate(
                request.getId(), request.getUrl(), request.getDescription(), request.getTgChatIdsList()));

        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }
}
