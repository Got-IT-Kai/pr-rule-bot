package com.code.platform.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Arrays;

@Configuration
public class MetricsConfiguration {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags(Environment environment) {
        String applicationName = environment.getProperty("spring.application.name", "unknown");
        String profile = Arrays.stream(environment.getActiveProfiles())
                .findFirst()
                .orElse("default");

        return registry -> registry.config()
                .commonTags(
                        "application", applicationName,
                        "profile", profile
                );
    }
}
