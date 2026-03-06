package backend.academy.linktracker.bot.command;

import backend.academy.linktracker.bot.configuration.BotCommandsProperties;
import backend.academy.linktracker.bot.model.Link;
import backend.academy.linktracker.bot.service.LinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ListCommand implements UserCommand {

    private final LinkService linkService;
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

        List<Link> links;

        if (args.length > 1 && !args[1].isBlank()) {
            String tag = args[1].trim();
            links = linkService.getLinksByTag(chatId, tag);
        } else {
            links = linkService.getLinks(chatId);
        }

        if (links.isEmpty()) {
            return properties.list().emptyMessage();
        }

        StringBuilder response = new StringBuilder(properties.list().header() + "\n");

        for (Link link : links) {
            response.append("- ").append(link.getUrl());

            if (link.getTags() != null && !link.getTags().isEmpty()) {
                response.append(" [");
                response.append(String.join(", ", link.getTags()));
                response.append("]");
            }

            response.append("\n");
        }
        return response.toString();
    }
}
