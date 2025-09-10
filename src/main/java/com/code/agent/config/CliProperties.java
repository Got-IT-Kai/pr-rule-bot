package com.code.agent.config;

import com.code.agent.domain.model.Repository;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "cli")
@Validated
public record CliProperties(@NotNull Repository repository,
                            @NotNull Integer prNumber,
                            @NotNull @Positive Integer timeOutMinutes) {}
