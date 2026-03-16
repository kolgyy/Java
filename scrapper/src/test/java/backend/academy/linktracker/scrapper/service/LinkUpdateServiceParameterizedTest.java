package backend.academy.linktracker.scrapper.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import backend.academy.linktracker.scrapper.client.BotClient;
import backend.academy.linktracker.scrapper.client.GithubClient;
import backend.academy.linktracker.scrapper.client.StackoverflowClient;
import backend.academy.linktracker.scrapper.dto.LinkUpdate;
import backend.academy.linktracker.scrapper.model.*;
import backend.academy.linktracker.scrapper.repository.LinkRepository;
import backend.academy.linktracker.scrapper.repository.TgChatRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

class LinkUpdateServiceParameterizedTest {

    private LinkRepository linkRepository;
    private TgChatRepository tgChatRepository;
    private BotClient botClient;
    private GithubClient githubClient;
    private StackoverflowClient stackoverflowClient;

    private LinkUpdateService updateService;

    @BeforeEach
    void setUp() {
        linkRepository = mock(LinkRepository.class);
        tgChatRepository = mock(TgChatRepository.class);
        botClient = mock(BotClient.class);
        githubClient = mock(GithubClient.class);
        stackoverflowClient = mock(StackoverflowClient.class);

        updateService =
                new LinkUpdateService(linkRepository, tgChatRepository, botClient, githubClient, stackoverflowClient);
    }

    @ParameterizedTest
    @EnumSource(
            value = LinkType.class,
            names = {"GITHUB", "STACKOVERFLOW"})
    void sendUpdates_onlyTrackedChatsReceiveUpdate(LinkType type) {
        TgChat chat1 = new TgChat();
        chat1.setId(1L);

        TgChat chat2 = new TgChat();
        chat2.setId(2L);

        Link trackedLink = new Link();
        trackedLink.setId(100L);
        trackedLink.setUrl(
                type == LinkType.GITHUB
                        ? "https://github.com/user/repo"
                        : "https://stackoverflow.com/questions/123456");

        TrackedLink tracked;
        if (type == LinkType.GITHUB) {
            tracked = new GithubTrackedLink(trackedLink, "user", "repo");
            tracked.setLastUpdated(OffsetDateTime.now().minusDays(1));
            when(githubClient.getLastUpdate("user", "repo")).thenReturn(Optional.of(OffsetDateTime.now()));
        } else { // STACKOVERFLOW
            tracked = new StackoverflowTrackedLink(trackedLink, "123456");
            tracked.setLastUpdated(OffsetDateTime.now().minusDays(1));
            long newEpoch = OffsetDateTime.now().toEpochSecond();
            when(stackoverflowClient.getLastActivity(String.valueOf(123456L))).thenReturn(Optional.of(newEpoch));
        }

        // Настройка репозиториев
        when(tgChatRepository.findAll()).thenReturn(List.of(chat1, chat2));
        when(linkRepository.findAllByChatId(1L)).thenReturn(List.of(trackedLink));
        when(linkRepository.findAllTrackedByChatId(1L)).thenReturn(List.of(tracked));
        when(linkRepository.findAllByChatId(2L)).thenReturn(List.of());
        when(linkRepository.findAllTrackedByChatId(2L)).thenReturn(List.of());

        // Вызов метода
        updateService.sendUpdates();

        // Проверяем, что сообщение отправлено только chat1
        ArgumentCaptor<LinkUpdate> captor = ArgumentCaptor.forClass(LinkUpdate.class);
        verify(botClient, times(1)).sendUpdate(captor.capture());

        LinkUpdate updateSent = captor.getValue();
        assertEquals(trackedLink.getId(), updateSent.id());
        assertEquals(trackedLink.getUrl(), updateSent.url());
        assertEquals(List.of(1L), updateSent.tgChatIds());
    }
}
