package com.code.agent.infra.ai.config;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TokenizationConfig {

    @Bean
    public EncodingRegistry encodingRegistry() {
        return Encodings.newDefaultEncodingRegistry();
    }

    @Bean
    public Encoding tiktokenEncoding(EncodingRegistry encodingRegistry) {
        return encodingRegistry.getEncodingForModel("gpt-4").orElseThrow();
    }
}
