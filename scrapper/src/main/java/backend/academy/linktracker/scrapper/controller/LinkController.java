package backend.academy.linktracker.scrapper.controller;

import backend.academy.linktracker.scrapper.dto.request.AddLinkRequest;
import backend.academy.linktracker.scrapper.dto.request.RemoveLinkRequest;
import backend.academy.linktracker.scrapper.dto.response.LinkResponse;
import backend.academy.linktracker.scrapper.dto.response.ListLinksResponse;
import backend.academy.linktracker.scrapper.model.Link;
import backend.academy.linktracker.scrapper.service.LinkService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/links")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.communication.server", havingValue = "http")
public class LinkController {

    private final LinkService linkService;

    @GetMapping
    public ResponseEntity<ListLinksResponse> getLinks(@RequestHeader("Tg-Chat-Id") Long chatId) {

        List<LinkResponse> links =
                linkService.getLinks(chatId).stream().map(this::toResponse).toList();

        return ResponseEntity.ok(new ListLinksResponse(links, links.size()));
    }

    @PostMapping
    public ResponseEntity<LinkResponse> addLink(
            @RequestHeader("Tg-Chat-Id") Long chatId, @RequestBody AddLinkRequest request) {

        Link link = linkService.addLink(chatId, request.link(), request.tags(), request.filters());

        return ResponseEntity.ok(toResponse(link));
    }

    @DeleteMapping
    public ResponseEntity<LinkResponse> removeLink(
            @RequestHeader("Tg-Chat-Id") Long chatId, @RequestBody RemoveLinkRequest request) {

        Link link = linkService.removeLink(chatId, request.link());

        return ResponseEntity.ok(toResponse(link));
    }

    private LinkResponse toResponse(Link link) {
        return new LinkResponse(link.getId(), link.getUrl(), link.getTags(), link.getFilters());
    }
}
