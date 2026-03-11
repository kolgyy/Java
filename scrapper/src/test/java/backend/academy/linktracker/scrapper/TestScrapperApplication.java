package backend.academy.linktracker.scrapper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestScrapperApplication {

    private static final Network NETWORK = Network.newNetwork();

    @Bean
    @ServiceConnection
    @Primary
    public GenericContainer<?> scrapperContainer() {
        return new GenericContainer<>(DockerImageName.parse("link-tracker/scrapper:latest"))
                .withNetwork(NETWORK)
                .withNetworkAliases("scrapper")
                .withEnv("SERVER_PORT", "8081")
                .withEnv("SPRING_PROFILES_ACTIVE", "test")
                .withEnv("COMMUNICATION_PROTOCOL", "http")
                .withEnv("BOT_HTTP_BASE_URL", "http://bot:8080")
                .withEnv("GITHUB_TOKEN", "test-token")
                .withEnv("STACKOVERFLOW_KEY", "test-key")
                .withEnv("STACKOVERFLOW_ACCESS_KEY", "test-access-key")
                .withExposedPorts(8081)
                .waitingFor(Wait.forHttp("/actuator/health").forPort(8081));
    }

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
                .waitingFor(Wait.forHttp("/actuator/health").forPort(8080));
    }

    public static void main(String[] args) {
        SpringApplication.from(ScrapperApplication::main)
                .with(TestScrapperApplication.class)
                .run(args);
    }
}
