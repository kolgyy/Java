package backend.academy.linktracker.bot.command;

import backend.academy.linktracker.bot.configuration.BotCommandsProperties;
import backend.academy.linktracker.bot.dto.response.LinkResponse;
import backend.academy.linktracker.bot.dto.response.ListLinksResponse;
import backend.academy.linktracker.bot.service.ScrapperService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ListCommand implements UserCommand {

    private final ScrapperService scrapperService;
    private final BotCommandsProperties properties;

    @Override
    public String name() {
        return properties.list().name();
    }

    @Override
    public String description() {
        return properties.list().description();
    }

    @Override
    public String execute(Long chatId, String[] args) {

        log.atInfo()
                .addKeyValue("chatId", chatId)
                .addKeyValue("command", name())
                .log("Executing list command");

        List<LinkResponse> links =
                scrapperService.getLinks(chatId).map(ListLinksResponse::links).orElse(List.of());

        if (args.length > 1 && !args[1].isBlank()) {
            String tag = args[1].trim();

            links = links.stream()
                    .filter(link -> link.tags() != null && link.tags().contains(tag))
                    .toList();
        }

        if (links.isEmpty()) {
            return properties.list().emptyMessage();
        }

        StringBuilder result = new StringBuilder(properties.list().header() + "\n");

        for (LinkResponse link : links) {
            result.append("- ").append(link.url());

            if (link.tags() != null && !link.tags().isEmpty()) {
                result.append(" [").append(String.join(", ", link.tags())).append("]");
            }

            result.append("\n");
        }

        return result.toString();
    }
}
