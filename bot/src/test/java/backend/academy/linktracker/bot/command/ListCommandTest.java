package backend.academy.linktracker.bot.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import backend.academy.linktracker.bot.configuration.BotCommandsProperties;
import backend.academy.linktracker.bot.dto.response.LinkResponse;
import backend.academy.linktracker.bot.dto.response.ListLinksResponse;
import backend.academy.linktracker.bot.service.ScrapperService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ListCommandTest {

    private ScrapperService scrapperService;
    private ListCommand command;
    private BotCommandsProperties.List props;

    @BeforeEach
    void setUp() {
        scrapperService = mock(ScrapperService.class);

        props = new BotCommandsProperties.List("/list", "Список ссылок", "Список пуст", "Ваши ссылки:");

        BotCommandsProperties properties = mock(BotCommandsProperties.class);
        when(properties.list()).thenReturn(props);

        command = new ListCommand(scrapperService, properties);
    }

    @Test
    void execute_shouldReturnEmptyMessage_whenNoLinks() {
        long chatId = 1L;
        when(scrapperService.getLinks(chatId)).thenReturn(Optional.of(new ListLinksResponse(List.of(), 0)));

        String result = command.execute(chatId, new String[] {"/list"});

        assertThat(result).isEqualTo(props.emptyMessage());
    }

    @Test
    void execute_shouldReturnEmptyMessage_whenGetLinksReturnsEmptyOptional() {
        long chatId = 1L;
        when(scrapperService.getLinks(chatId)).thenReturn(Optional.empty());

        String result = command.execute(chatId, new String[] {"/list"});

        assertThat(result).isEqualTo(props.emptyMessage());
    }

    @Test
    void execute_shouldListAllLinks() {
        long chatId = 1L;
        List<LinkResponse> links = List.of(
                new LinkResponse(1L, "https://github.com/user/repo", List.of("tag1"), List.of()),
                new LinkResponse(2L, "https://stackoverflow.com/questions/123", List.of(), List.of()));
        when(scrapperService.getLinks(chatId)).thenReturn(Optional.of(new ListLinksResponse(links, 2)));

        String result = command.execute(chatId, new String[] {"/list"});

        String expected = """
                Ваши ссылки:
                - https://github.com/user/repo [tag1]
                - https://stackoverflow.com/questions/123
                """;
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void execute_shouldFilterLinksByTag() {
        long chatId = 1L;
        List<LinkResponse> links = List.of(
                new LinkResponse(1L, "https://github.com/user/repo", List.of("tag1"), List.of()),
                new LinkResponse(2L, "https://stackoverflow.com/questions/123", List.of("tag2"), List.of()));
        when(scrapperService.getLinks(chatId)).thenReturn(Optional.of(new ListLinksResponse(links, 2)));

        String result = command.execute(chatId, new String[] {"/list", "tag1"});

        String expected = """
                Ваши ссылки:
                - https://github.com/user/repo [tag1]
                """;
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void execute_shouldReturnEmpty_whenNoLinksMatchTag() {
        long chatId = 1L;
        List<LinkResponse> links =
                List.of(new LinkResponse(1L, "https://github.com/user/repo", List.of("tag1"), List.of()));
        when(scrapperService.getLinks(chatId)).thenReturn(Optional.of(new ListLinksResponse(links, 1)));

        String result = command.execute(chatId, new String[] {"/list", "nonexistent"});

        assertThat(result).isEqualTo(props.emptyMessage());
    }

    @Test
    void execute_shouldHandleEmptyTagArgument() {
        long chatId = 1L;
        List<LinkResponse> links =
                List.of(new LinkResponse(1L, "https://github.com/user/repo", List.of("tag1"), List.of()));
        when(scrapperService.getLinks(chatId)).thenReturn(Optional.of(new ListLinksResponse(links, 1)));

        // Передаём пустой аргумент вместо тега
        String result = command.execute(chatId, new String[] {"/list", ""});

        String expected = """
                Ваши ссылки:
                - https://github.com/user/repo [tag1]
                """;
        assertThat(result).isEqualTo(expected);
    }
}
