package backend.academy.linktracker.scrapper.exception;

import lombok.Getter;

@Getter
public class LinkAlreadyExistsException extends RuntimeException {

    private final String url;

    public LinkAlreadyExistsException(String url) {
        super("Link already tracked: " + url);
        this.url = url;
    }
}
