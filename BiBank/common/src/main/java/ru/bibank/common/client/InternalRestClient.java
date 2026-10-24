package ru.bibank.common.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import ru.bibank.common.security.InternalTokenFilter;
import ru.bibank.common.web.ApiError;
import ru.bibank.common.web.ApiException;

import java.time.Duration;
import java.util.function.Function;

/**
 * Клиент для вызова /internal/** API другого сервиса.
 * Ошибки нижестоящего сервиса транслируются в {@link ApiException} с исходным статусом и кодом,
 * недоступность — в 503.
 */
public class InternalRestClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String serviceName;

    public InternalRestClient(String serviceName, String baseUrl, String internalToken,
                              ObjectMapper objectMapper, Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader(InternalTokenFilter.HEADER, internalToken)
                .build();
        this.objectMapper = objectMapper;
        this.serviceName = serviceName;
    }

    public <T> T call(Function<RestClient, T> request) {
        try {
            return request.apply(restClient);
        } catch (RestClientResponseException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            if (status != null && status.is4xxClientError()) {
                try {
                    ApiError error = objectMapper.readValue(e.getResponseBodyAsByteArray(), ApiError.class);
                    throw new ApiException(status, error.code(), error.message());
                } catch (java.io.IOException parse) {
                    throw new ApiException(status, "DOWNSTREAM_ERROR", serviceName + ": " + e.getStatusText());
                }
            }
            throw new DownstreamUnavailableException(serviceName + " responded with " + e.getStatusCode(), e);
        } catch (ResourceAccessException e) {
            throw new DownstreamUnavailableException(serviceName + " is unavailable", e);
        }
    }

    /**
     * Сервис недоступен или ответил 5xx: результат операции неизвестен, её можно повторить
     * с тем же ключом идемпотентности.
     */
    public static class DownstreamUnavailableException extends ApiException {
        public DownstreamUnavailableException(String message, Throwable cause) {
            super(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", message);
            initCause(cause);
        }
    }
}
