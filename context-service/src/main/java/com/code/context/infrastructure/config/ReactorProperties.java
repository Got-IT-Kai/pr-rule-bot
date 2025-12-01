package com.code.context.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Positive;

@Validated
@ConfigurationProperties(prefix = "context.reactor")
public record ReactorProperties(
        @Positive Integer maxConcurrentRequests,
        @Positive Integer prefetchSize
) {
    public ReactorProperties {
        if (maxConcurrentRequests == null) {
            maxConcurrentRequests = 5;
        }
        if (prefetchSize == null) {
            prefetchSize = 1;
        }
    }
}
