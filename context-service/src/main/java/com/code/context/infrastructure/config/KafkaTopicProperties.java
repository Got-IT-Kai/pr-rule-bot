package com.code.context.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

@Validated
@ConfigurationProperties(prefix = "kafka.topics")
public record KafkaTopicProperties(
    @NotBlank String pullRequestReceived,
    @NotBlank String contextCollected
) {
}
