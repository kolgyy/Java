package backend.academy.linktracker.scrapper.properties;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.scheduler")
public record SchedulerProperties(Duration delay) {

    public SchedulerProperties {
        if (delay == null) {
            delay = Duration.ofSeconds(60);
        }
    }
}
