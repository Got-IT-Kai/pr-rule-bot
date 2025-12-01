package com.code.platform.config;

import com.code.platform.metrics.MetricsHelper;
import com.code.platform.metrics.MetricsProperties;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MetricsProperties.class)
@RequiredArgsConstructor
public class MetricsAutoConfiguration {

    private final MetricsProperties metricsProperties;

    @Bean
    public MetricsHelper metricsHelper(MeterRegistry meterRegistry) {
        return new MetricsHelper(meterRegistry, metricsProperties.name());
    }
}
