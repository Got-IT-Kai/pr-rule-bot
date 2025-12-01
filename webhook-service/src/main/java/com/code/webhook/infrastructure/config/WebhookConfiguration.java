package com.code.webhook.infrastructure.config;

import com.code.webhook.application.port.inbound.WebhookService;
import com.code.webhook.application.port.outbound.EventPublisher;
import com.code.webhook.application.port.outbound.SignatureValidator;
import com.code.webhook.application.service.WebhookServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class WebhookConfiguration {

    private final WebhookProperties webhookProperties;

    @Bean
    public WebhookService webhookService(
            SignatureValidator signatureValidator,
            EventPublisher eventPublisher,
            ObjectMapper objectMapper) {

        return new WebhookServiceImpl(
                signatureValidator,
                eventPublisher,
                objectMapper,
                webhookProperties.secret()
        );
    }
}
