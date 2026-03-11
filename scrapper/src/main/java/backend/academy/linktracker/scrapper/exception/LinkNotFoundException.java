package backend.academy.linktracker.scrapper.exception;

import lombok.Getter;

@Getter
public class LinkNotFoundException extends RuntimeException {

    private final String url;

    public LinkNotFoundException(String url) {
        super("Link not found: " + url);
        this.url = url;
    }
}
