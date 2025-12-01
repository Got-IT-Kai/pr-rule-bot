package com.code.review.infrastructure.adapter.outbound.ai;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "ai")
@Validated
public record AiProperties(
        @NotNull AiProvider provider
) {
}
