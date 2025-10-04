package com.code.agent.infra.github.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "github")
public record GitHubProperties(
        @NotBlank String baseUrl,
        @NotBlank String token,
        @NotBlank String reviewPath,
        @NotNull Client client
) {
    public record Client(
            Duration responseTimeout,
            Duration connectTimeout
    ) {
        public Client {
            if (responseTimeout == null) {
                responseTimeout = Duration.ofSeconds(300);
            }
            if (connectTimeout == null) {
                connectTimeout = Duration.ofSeconds(5);
            }
        }
    }
}
