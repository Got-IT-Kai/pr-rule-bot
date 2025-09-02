package com.code.agent.infra.github.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "github")
public record GitHubProperties(String baseUrl, String token, String reviewPath) {}
