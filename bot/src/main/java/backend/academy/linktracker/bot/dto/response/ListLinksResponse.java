package backend.academy.linktracker.bot.dto.response;

import java.util.List;

public record ListLinksResponse(List<LinkResponse> links, Integer size) {}
