package com.code.platform.github;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "github.api")
public record GitHubProperties(
        String baseUrl,
        String token,
        Timeout timeout,
        Retry retry
) {
    public GitHubProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.github.com";
        }
    }

    public record Timeout(
            Duration connect,
            Duration read,
            Duration write
    ) {
        public Timeout {
            if (connect == null) {
                connect = Duration.ofSeconds(5);
            }
            if (read == null) {
                read = Duration.ofSeconds(10);
            }
            if (write == null) {
                write = Duration.ofSeconds(10);
            }
        }
    }

    public record Retry(
            Integer maxAttempts,
            Duration backoff
    ) {
        public Retry {
            if (maxAttempts == null) {
                maxAttempts = 3;
            }
            if (backoff == null) {
                backoff = Duration.ofSeconds(1);
            }
        }
    }
}
