package com.code.agent.infra.github.util;

import org.springframework.web.reactive.function.client.WebClientResponseException;

public final class GitHubRetryUtil {
    private GitHubRetryUtil() {}

    public static boolean isRetryableError(Throwable error) {
        if (error instanceof WebClientResponseException e) {
            int statusCode = e.getStatusCode().value();
            return statusCode == 503 || statusCode == 504;
        }

        return false;
    }
}
