package backend.academy.linktracker.bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestBotApplication {

    private static final Network NETWORK = Network.newNetwork();

    @Bean
    @Primary
    public GenericContainer<?> botContainer() {
        return new GenericContainer<>(DockerImageName.parse("link-tracker/bot:latest"))
                .withNetwork(NETWORK)
                .withNetworkAliases("bot")
                .withEnv("SERVER_PORT", "8080")
                .withEnv("SPRING_PROFILES_ACTIVE", "test")
                .withEnv("COMMUNICATION_PROTOCOL", "http")
                .withEnv("TELEGRAM_TOKEN", "test-token")
                .withExposedPorts(8080)
                .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forHttp("/actuator/health")
                        .forPort(8080));
    }

    public static void main(String[] args) {
        SpringApplication.from(BotApplication::main)
                .with(TestBotApplication.class)
                .run(args);
    }
}
