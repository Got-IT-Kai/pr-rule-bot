package com.code.integration.domain.model;

import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.concurrent.TimeoutException;

public enum ErrorType {
    HTTP_4XX,
    HTTP_5XX,
    NETWORK,
    TIMEOUT,
    UNKNOWN;

    public static ErrorType from(Throwable error) {
        return switch (error) {
            case WebClientResponseException e ->
                e.getStatusCode().is4xxClientError() ? HTTP_4XX : HTTP_5XX;
            case WebClientRequestException e -> NETWORK;
            case TimeoutException e -> TIMEOUT;
            default -> UNKNOWN;
        };
    }
}
