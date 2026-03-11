package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.client.BotClient;
import backend.academy.linktracker.scrapper.client.GithubClient;
import backend.academy.linktracker.scrapper.client.StackoverflowClient;
import backend.academy.linktracker.scrapper.dto.LinkUpdate;
import backend.academy.linktracker.scrapper.model.GithubTrackedLink;
import backend.academy.linktracker.scrapper.model.Link;
import backend.academy.linktracker.scrapper.model.StackoverflowTrackedLink;
import backend.academy.linktracker.scrapper.model.TrackedLink;
import backend.academy.linktracker.scrapper.repository.LinkRepository;
import backend.academy.linktracker.scrapper.repository.TgChatRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LinkUpdateService {

    private final LinkRepository linkRepository;
    private final TgChatRepository tgChatRepository;
    private final BotClient botClient;
    private final GithubClient githubClient;
    private final StackoverflowClient stackoverflowClient;

    public void sendUpdates() {
        tgChatRepository.findAll().forEach(chat -> {
            List<Link> links = linkRepository.findAllByChatId(chat.getId());
            List<TrackedLink> trackedLinks = linkRepository.findAllTrackedByChatId(chat.getId());

            Map<String, TrackedLink> trackedMap = trackedLinks.stream()
                    .collect(Collectors.toMap(t -> t.getLink().getUrl(), t -> t));

            links.forEach(link -> {
                Optional.ofNullable(trackedMap.get(link.getUrl())).ifPresent(tracked -> {
                    Optional<OffsetDateTime> currentUpdate = fetchCurrentUpdate(tracked);

                    boolean changed = currentUpdate
                            .map(u -> tracked.getLastUpdated().map(u::isAfter).orElse(true))
                            .orElse(false);

                    if (changed) {
                        currentUpdate.ifPresent(tracked::setLastUpdated);

                        botClient.sendUpdate(new LinkUpdate(
                                link.getId(), link.getUrl(), "данные изменились", List.of(chat.getId())));

                        log.atInfo()
                                .addKeyValue("linkId", link.getId())
                                .addKeyValue("url", link.getUrl())
                                .addKeyValue("chatId", chat.getId())
                                .log("Sent update because resource changed");
                    }
                });
            });
        });
    }

    private Optional<OffsetDateTime> fetchCurrentUpdate(TrackedLink tracked) {
        return switch (tracked.getType()) {
            case GITHUB -> {
                GithubTrackedLink gh = (GithubTrackedLink) tracked;
                yield githubClient.getLastUpdate(gh.getOwner(), gh.getRepo());
            }
            case STACKOVERFLOW -> {
                StackoverflowTrackedLink so = (StackoverflowTrackedLink) tracked;
                yield stackoverflowClient
                        .getLastActivity(so.getQuestionId())
                        .map(epoch -> OffsetDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneOffset.UTC));
            }
            case UNKNOWN -> {
                log.atWarn().addKeyValue("url", tracked.getLink().getUrl()).log("Skipping unknown link type");
                yield Optional.empty();
            }
        };
    }
}
