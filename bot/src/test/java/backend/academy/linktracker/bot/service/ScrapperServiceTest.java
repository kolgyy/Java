package backend.academy.linktracker.bot.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import backend.academy.linktracker.bot.client.ScrapperClient;
import backend.academy.linktracker.bot.dto.request.AddLinkRequest;
import backend.academy.linktracker.bot.dto.request.RemoveLinkRequest;
import backend.academy.linktracker.bot.dto.response.LinkResponse;
import backend.academy.linktracker.bot.dto.response.ListLinksResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScrapperServiceTest {

    @Mock
    private ScrapperClient client;

    @Mock
    private UserService userService;

    @InjectMocks
    private ScrapperService service;

    @Test
    void getLinks_shouldRegisterChatIfNotRegistered() {
        long chatId = 1L;
        ListLinksResponse response = new ListLinksResponse(List.of(), 0);

        when(userService.isRegistered(chatId)).thenReturn(false);
        when(client.getLinks(chatId)).thenReturn(Optional.of(response));

        Optional<ListLinksResponse> result = service.getLinks(chatId);

        assertThat(result).contains(response);
        verify(userService).register(chatId);
        verify(client).registerChat(chatId);
    }

    @Test
    void getLinks_shouldNotRegisterChatIfAlreadyRegistered() {
        long chatId = 1L;
        ListLinksResponse response = new ListLinksResponse(List.of(), 0);

        when(userService.isRegistered(chatId)).thenReturn(true);
        when(client.getLinks(chatId)).thenReturn(Optional.of(response));

        Optional<ListLinksResponse> result = service.getLinks(chatId);

        assertThat(result).contains(response);
        verify(userService, never()).register(anyLong());
        verify(client, never()).registerChat(anyLong());
    }

    @Test
    void addLink_shouldCallClientAfterEnsuringChat() {
        long chatId = 1L;
        AddLinkRequest request = new AddLinkRequest("http://example.com", List.of("tag"), List.of("filter"));
        LinkResponse response = new LinkResponse(1L, "http://example.com", List.of("tag"), List.of("filter"));

        when(userService.isRegistered(chatId)).thenReturn(true);
        when(client.addLink(chatId, request)).thenReturn(Optional.of(response));

        Optional<LinkResponse> result = service.addLink(chatId, request);

        assertThat(result).contains(response);
        verify(client).addLink(chatId, request);
    }

    @Test
    void removeLink_shouldCallClientAfterEnsuringChat() {
        long chatId = 1L;
        RemoveLinkRequest request = new RemoveLinkRequest("http://example.com");
        LinkResponse response = new LinkResponse(1L, "http://example.com", List.of(), List.of());

        when(userService.isRegistered(chatId)).thenReturn(true);
        when(client.removeLink(chatId, request)).thenReturn(Optional.of(response));

        Optional<LinkResponse> result = service.removeLink(chatId, request);

        assertThat(result).contains(response);
        verify(client).removeLink(chatId, request);
    }

    @Test
    void registerChat_shouldCallClient() {
        long chatId = 1L;

        service.registerChat(chatId);

        verify(client).registerChat(chatId);
    }

    @Test
    void removeChat_shouldCallClient() {
        long chatId = 1L;

        service.removeChat(chatId);

        verify(client).removeChat(chatId);
    }
}
