package com.code.platform.metrics;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.application")
public record MetricsProperties(
        String name
) {
    public MetricsProperties {
        if (name == null || name.isBlank()) {
            name = "unknown";
        }
    }
}
