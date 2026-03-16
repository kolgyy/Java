package backend.academy.linktracker.scrapper.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import backend.academy.linktracker.scrapper.exception.LinkAlreadyExistsException;
import backend.academy.linktracker.scrapper.exception.LinkNotFoundException;
import backend.academy.linktracker.scrapper.model.Link;
import backend.academy.linktracker.scrapper.repository.LinkRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LinkServiceTest {

    @Mock
    private LinkRepository repository;

    @Mock
    private TgChatService tgChatService;

    @InjectMocks
    private LinkService service;

    @Test
    void getLinks_shouldReturnLinks_whenChatExists() {
        Long chatId = 1L;
        List<Link> links = List.of(new Link(null, "http://example.com", List.of("tag1"), List.of()));

        when(repository.findAllByChatId(chatId)).thenReturn(links);

        List<Link> result = service.getLinks(chatId);

        assertThat(result).isEqualTo(links);
        verify(tgChatService).assertChatRegistered(chatId);
    }

    @Test
    void addLink_shouldSaveLink_whenLinkNotExists() {
        Long chatId = 1L;
        String url = "http://example.com";

        when(repository.findByChatIdAndUrl(chatId, url)).thenReturn(Optional.empty());

        Link result = service.addLink(chatId, url, List.of("tag1"), List.of("filter1"));

        assertThat(result.getUrl()).isEqualTo(url);
        assertThat(result.getTags()).contains("tag1");
        assertThat(result.getFilters()).contains("filter1");

        verify(repository).save(eq(chatId), any(Link.class));
        verify(tgChatService).assertChatRegistered(chatId);
    }

    @Test
    void addLink_shouldThrowException_whenLinkAlreadyExists() {
        Long chatId = 1L;
        String url = "http://example.com";

        when(repository.findByChatIdAndUrl(chatId, url))
                .thenReturn(Optional.of(new Link(null, url, List.of(), List.of())));

        assertThatThrownBy(() -> service.addLink(chatId, url, List.of(), List.of()))
                .isInstanceOf(LinkAlreadyExistsException.class);

        verify(repository, never()).save(anyLong(), any());
        verify(tgChatService).assertChatRegistered(chatId);
    }

    @Test
    void removeLink_shouldDeleteLink_whenLinkExists() {
        Long chatId = 1L;
        String url = "http://example.com";
        Link link = new Link(null, url, List.of(), List.of());

        when(repository.findByChatIdAndUrl(chatId, url)).thenReturn(Optional.of(link));

        Link result = service.removeLink(chatId, url);

        assertThat(result).isEqualTo(link);
        verify(repository).delete(chatId, url);
        verify(tgChatService).assertChatRegistered(chatId);
    }

    @Test
    void removeLink_shouldThrowException_whenLinkNotExists() {
        Long chatId = 1L;
        String url = "http://example.com";

        when(repository.findByChatIdAndUrl(chatId, url)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeLink(chatId, url)).isInstanceOf(LinkNotFoundException.class);

        verify(repository, never()).delete(anyLong(), any());
        verify(tgChatService).assertChatRegistered(chatId);
    }
}
