package com.code.context.infrastructure.config;

import com.code.context.application.port.inbound.ContextCollectionService;
import com.code.context.application.port.outbound.EventPublisher;
import com.code.context.application.port.outbound.GitHubClient;
import com.code.context.application.service.ContextCollectionServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ContextServiceConfiguration {

    @Bean
    public ContextCollectionService contextCollectionService(
            GitHubClient gitHubClient,
            EventPublisher eventPublisher,
            ObjectMapper objectMapper) {
        return new ContextCollectionServiceImpl(gitHubClient, eventPublisher, objectMapper);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .findAndRegisterModules();
    }
}
