package backend.academy.linktracker.bot.configuration;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcConfig {
    @Bean
    public ManagedChannel scrapperChannel(ScrapperGrpcProperties props) {
        return ManagedChannelBuilder.forAddress(props.host(), props.port())
                .usePlaintext()
                .build();
    }
}
