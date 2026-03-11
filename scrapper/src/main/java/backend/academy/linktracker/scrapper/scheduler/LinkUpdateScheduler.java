package backend.academy.linktracker.scrapper.scheduler;

import backend.academy.linktracker.scrapper.properties.SchedulerProperties;
import backend.academy.linktracker.scrapper.service.LinkUpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LinkUpdateScheduler {

    private final SchedulerProperties schedulerProperties;
    private final LinkUpdateService updateService;

    @Scheduled(fixedDelayString = "${app.scheduler.delay}")
    public void checkLinks() {
        log.atInfo().addKeyValue("delay", schedulerProperties.delay()).log("Scheduler triggered: sending updates");

        updateService.sendUpdates();
    }
}
