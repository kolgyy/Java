package backend.academy.linktracker.scrapper.controller;

import backend.academy.linktracker.scrapper.dto.response.ApiErrorResponse;
import backend.academy.linktracker.scrapper.exception.ChatAlreadyRegisteredException;
import backend.academy.linktracker.scrapper.exception.ChatNotRegisteredException;
import backend.academy.linktracker.scrapper.exception.LinkAlreadyExistsException;
import backend.academy.linktracker.scrapper.exception.LinkNotFoundException;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ChatNotRegisteredException.class)
    public ResponseEntity<ApiErrorResponse> handleChatNotRegistered(ChatNotRegisteredException ex) {

        log.atWarn().addKeyValue("chatId", ex.getChatId()).log("Chat not registered");

        return buildResponse("Chat not registered", 404, ex);
    }

    @ExceptionHandler(ChatAlreadyRegisteredException.class)
    public ResponseEntity<ApiErrorResponse> handleChatAlreadyRegistered(ChatAlreadyRegisteredException ex) {

        log.atWarn().addKeyValue("chatId", ex.getChatId()).log("Chat already registered");

        return buildResponse("Chat already registered", 409, ex);
    }

    @ExceptionHandler(LinkAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleLinkExists(LinkAlreadyExistsException ex) {

        log.atWarn().addKeyValue("url", ex.getUrl()).log("Link already tracked");

        return buildResponse("Link already tracked", 409, ex);
    }

    @ExceptionHandler(LinkNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleLinkNotFound(LinkNotFoundException ex) {

        log.atWarn().addKeyValue("url", ex.getUrl()).log("Link not found");

        return buildResponse("Link not found", 404, ex);
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(String description, int code, Exception ex) {

        ApiErrorResponse response = new ApiErrorResponse(
                description, String.valueOf(code), ex.getClass().getSimpleName(), ex.getMessage(), getStackTrace(ex));

        return ResponseEntity.status(code).body(response);
    }

    private List<String> getStackTrace(Exception ex) {
        return Arrays.stream(ex.getStackTrace())
                .map(StackTraceElement::toString)
                .toList();
    }
}
