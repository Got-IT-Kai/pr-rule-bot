package com.code.review.infrastructure.config;

import com.code.review.application.port.inbound.ReviewService;
import com.code.review.application.port.outbound.AiModelPort;
import com.code.review.application.port.outbound.EventPublisher;
import com.code.review.application.service.ReviewServiceImpl;
import com.code.platform.metrics.MetricsHelper;
import com.knuddels.jtokkit.api.Encoding;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class ReviewServiceConfiguration {

    private final ReactorProperties reactorProperties;

    @Bean
    public ReviewService reviewService(
            AiModelPort aiModelPort,
            EventPublisher eventPublisher,
            Encoding encoding,
            MetricsHelper metricsHelper) {
        return new ReviewServiceImpl(aiModelPort, eventPublisher, encoding, reactorProperties, metricsHelper);
    }
}
