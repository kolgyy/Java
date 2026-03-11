package backend.academy.linktracker.scrapper.configuration;

import backend.academy.linktracker.scrapper.properties.BotGrpcProperties;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcConfig {
    @Bean
    public ManagedChannel botChannel(BotGrpcProperties props) {
        return ManagedChannelBuilder.forAddress(props.host(), props.port())
                .usePlaintext()
                .build();
    }
}
