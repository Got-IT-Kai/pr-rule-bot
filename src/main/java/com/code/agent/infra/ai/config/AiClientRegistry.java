package com.code.agent.infra.ai.config;

import com.code.agent.infra.ai.model.AiProvider;
import com.code.agent.infra.ai.spi.AiModelClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration
@Slf4j
public class AiClientRegistry {

    @Bean
    public Map<AiProvider, AiModelClient> aiClients(List<AiModelClient> aiModelClients) {
        Map<AiProvider, List<AiModelClient>> grouped = aiModelClients.stream().collect(Collectors.groupingBy(AiModelClient::provider));

        List<AiProvider> dupKeys = grouped.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(Map.Entry::getKey)
                .toList();

        if (!dupKeys.isEmpty()) {
            throw new IllegalStateException("Duplicated AiModelClient for providers: " + dupKeys);
        }

        Map<AiProvider, AiModelClient> map = aiModelClients.stream()
                .collect(Collectors.toMap(
                        AiModelClient::provider,
                        Function.identity()
                ));

        log.info("Registered AI providers: {}", map.keySet());
        return Collections.unmodifiableMap(map);
    }
}
