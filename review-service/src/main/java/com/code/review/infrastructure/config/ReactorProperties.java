package com.code.review.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Positive;

@Validated
@ConfigurationProperties(prefix = "review.reactor")
public record ReactorProperties(
        @Positive Integer maxConcurrentReviews,
        @Positive Integer prefetchSize
) {
    public ReactorProperties {
        if (maxConcurrentReviews == null) {
            maxConcurrentReviews = 10;
        }
        if (prefetchSize == null) {
            prefetchSize = 1;
        }
    }
}
