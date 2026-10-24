package ru.bibank.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

/**
 * Единый формат ошибки для всех сервисов.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        Map<String, String> fieldErrors
) {
    public static ApiError of(HttpStatus status, String code, String message, String path) {
        return new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), code, message, path, null);
    }

    public static ApiError validation(String message, String path, Map<String, String> fieldErrors) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return new ApiError(Instant.now(), status.value(), status.getReasonPhrase(),
                "VALIDATION_FAILED", message, path, fieldErrors);
    }
}
