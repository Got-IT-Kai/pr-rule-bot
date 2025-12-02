package com.code.review.infrastructure.config;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TokenizationConfig {

    @Bean
    public Encoding encoding() {
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        return registry.getEncoding(EncodingType.CL100K_BASE);
    }
}
