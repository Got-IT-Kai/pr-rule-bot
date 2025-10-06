package com.code.agent.config;

import com.code.agent.domain.model.Repository;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * CLI configuration properties.
 *
 * @param repository Repository information (owner and name)
 * @param prNumber Pull request number to review
 * @param timeOutMinutes Timeout in minutes for review process
 * @param forceReview Force review even if one already exists (useful for testing or re-reviews)
 */
@ConfigurationProperties(prefix = "cli")
@Validated
public record CliProperties(@NotNull Repository repository,
                            @NotNull Integer prNumber,
                            @NotNull @Positive Integer timeOutMinutes,
                            boolean forceReview) {}
