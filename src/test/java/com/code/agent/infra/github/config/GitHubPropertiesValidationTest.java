package com.code.agent.infra.github.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validation tests for {@link GitHubProperties}.
 * Ensures that Bean Validation constraints are properly enforced:
 * - @NotBlank validations
 * - @NotNull validations
 * - Default value initialization
 */
@DisplayName("GitHubProperties Validation")
class GitHubPropertiesValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Nested
    @DisplayName("when validating baseUrl")
    class WhenValidatingBaseUrl {

        @ParameterizedTest(name = "should reject baseUrl: ''{0}''")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("should reject blank values")
        void shouldRejectBlankValues(String baseUrl) {
            // Given
            GitHubProperties properties = new GitHubProperties(
                baseUrl,
                "valid-token",
                "/valid/path",
                validClient(),
                "test-webhook-secret"
            );

            // When
            Set<ConstraintViolation<GitHubProperties>> violations = validator.validate(properties);

            // Then
            assertThat(violations)
                .isNotEmpty()
                .extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .contains("baseUrl");
        }

        @Test
        @DisplayName("should accept valid URL")
        void shouldAcceptValidUrl() {
            // Given
            GitHubProperties properties = new GitHubProperties(
                "https://api.github.com",
                "valid-token",
                "/valid/path",
                validClient(),
                "test-webhook-secret"
            );

            // When
            Set<ConstraintViolation<GitHubProperties>> violations = validator.validate(properties);

            // Then
            assertThat(violations).isEmpty();
        }
    }

    @Nested
    @DisplayName("when validating token")
    class WhenValidatingToken {

        @ParameterizedTest(name = "should reject token: ''{0}''")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("should reject blank values")
        void shouldRejectBlankValues(String token) {
            // Given
            GitHubProperties properties = new GitHubProperties(
                "https://api.github.com",
                token,
                "/valid/path",
                validClient(),
                "test-webhook-secret"
            );

            // When
            Set<ConstraintViolation<GitHubProperties>> violations = validator.validate(properties);

            // Then
            assertThat(violations)
                .isNotEmpty()
                .extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .contains("token");
        }

        @Test
        @DisplayName("should accept valid token")
        void shouldAcceptValidToken() {
            // Given
            GitHubProperties properties = new GitHubProperties(
                "https://api.github.com",
                "ghp_validtoken123456789",
                "/valid/path",
                validClient(),
                "test-webhook-secret"
            );

            // When
            Set<ConstraintViolation<GitHubProperties>> violations = validator.validate(properties);

            // Then
            assertThat(violations).isEmpty();
        }
    }

    @Nested
    @DisplayName("when validating reviewPath")
    class WhenValidatingReviewPath {

        @ParameterizedTest(name = "should reject reviewPath: ''{0}''")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("should reject blank values")
        void shouldRejectBlankValues(String reviewPath) {
            // Given
            GitHubProperties properties = new GitHubProperties(
                "https://api.github.com",
                "valid-token",
                reviewPath,
                validClient(),
                "test-webhook-secret"
            );

            // When
            Set<ConstraintViolation<GitHubProperties>> violations = validator.validate(properties);

            // Then
            assertThat(violations)
                .isNotEmpty()
                .extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .contains("reviewPath");
        }

        @Test
        @DisplayName("should accept valid path with placeholders")
        void shouldAcceptValidPath() {
            // Given
            GitHubProperties properties = new GitHubProperties(
                "https://api.github.com",
                "valid-token",
                "/repos/{owner}/{repo}/pulls/{pull_number}/reviews",
                validClient(),
                "test-webhook-secret"
            );

            // When
            Set<ConstraintViolation<GitHubProperties>> violations = validator.validate(properties);

            // Then
            assertThat(violations).isEmpty();
        }
    }

    @Nested
    @DisplayName("when validating client")
    class WhenValidatingClient {

        @Test
        @DisplayName("should reject null client")
        void shouldRejectNullClient() {
            // Given
            GitHubProperties properties = new GitHubProperties(
                "https://api.github.com",
                "valid-token",
                "/valid/path",
                null,
                "test-webhook-secret"
            );

            // When
            Set<ConstraintViolation<GitHubProperties>> violations = validator.validate(properties);

            // Then
            assertThat(violations)
                .isNotEmpty()
                .extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .contains("client");
        }

        @Test
        @DisplayName("should accept valid client configuration")
        void shouldAcceptValidClient() {
            // Given
            GitHubProperties properties = new GitHubProperties(
                "https://api.github.com",
                "valid-token",
                "/valid/path",
                validClient(),
                "test-webhook-secret"
            );

            // When
            Set<ConstraintViolation<GitHubProperties>> violations = validator.validate(properties);

            // Then
            assertThat(violations).isEmpty();
        }
    }

    @Nested
    @DisplayName("when validating client defaults")
    class WhenValidatingClientDefaults {

        @Test
        @DisplayName("should initialize default timeouts when null")
        void shouldInitializeDefaultTimeouts() {
            // Given & When
            GitHubProperties.Client client = new GitHubProperties.Client(null, null);

            // Then
            assertThat(client.responseTimeout()).isEqualTo(Duration.ofSeconds(300));
            assertThat(client.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        }

        @Test
        @DisplayName("should preserve custom timeouts")
        void shouldPreserveCustomTimeouts() {
            // Given
            Duration customResponse = Duration.ofSeconds(600);
            Duration customConnect = Duration.ofSeconds(10);

            // When
            GitHubProperties.Client client = new GitHubProperties.Client(customResponse, customConnect);

            // Then
            assertThat(client.responseTimeout()).isEqualTo(customResponse);
            assertThat(client.connectTimeout()).isEqualTo(customConnect);
        }
    }

    @Nested
    @DisplayName("when validating complete properties")
    class WhenValidatingCompleteProperties {

        @Test
        @DisplayName("should accept all valid values")
        void shouldAcceptAllValidValues() {
            // Given
            GitHubProperties properties = new GitHubProperties(
                "https://api.github.com",
                "ghp_validtoken123456789",
                "/repos/{owner}/{repo}/pulls/{pull_number}/reviews",
                new GitHubProperties.Client(
                    Duration.ofSeconds(300),
                    Duration.ofSeconds(5)
                ),
                "test-webhook-secret"
            );

            // When
            Set<ConstraintViolation<GitHubProperties>> violations = validator.validate(properties);

            // Then
            assertThat(violations).isEmpty();
            assertThat(properties.baseUrl()).isEqualTo("https://api.github.com");
            assertThat(properties.token()).isEqualTo("ghp_validtoken123456789");
            assertThat(properties.reviewPath()).isEqualTo("/repos/{owner}/{repo}/pulls/{pull_number}/reviews");
            assertThat(properties.client()).isNotNull();
        }

        @Test
        @DisplayName("should collect multiple violations")
        void shouldCollectMultipleViolations() {
            // Given: All fields invalid
            GitHubProperties properties = new GitHubProperties(
                null,  // invalid baseUrl
                "",    // invalid token
                "   ", // invalid reviewPath
                null,  // invalid client
                ""     // invalid webhookSecret
            );

            // When
            Set<ConstraintViolation<GitHubProperties>> violations = validator.validate(properties);

            // Then
            assertThat(violations)
                .hasSize(5)
                .extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .containsExactlyInAnyOrder("baseUrl", "token", "reviewPath", "client", "webhookSecret");
        }
    }

    // Helper method
    private GitHubProperties.Client validClient() {
        return new GitHubProperties.Client(
            Duration.ofSeconds(300),
            Duration.ofSeconds(5)
        );
    }
}