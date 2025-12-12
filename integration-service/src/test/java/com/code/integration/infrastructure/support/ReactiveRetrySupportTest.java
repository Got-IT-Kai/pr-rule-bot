package com.code.integration.infrastructure.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.Exceptions;
import reactor.util.retry.Retry;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReactiveRetrySupport")
class ReactiveRetrySupportTest {

    ReactiveRetrySupport retrySupport;

    @BeforeEach
    void setUp() {
        retrySupport = new ReactiveRetrySupport();
    }

    @Nested
    @DisplayName("transientRetry")
    class TransientRetry {

        @Test
        @DisplayName("should create retry spec with backoff")
        void shouldCreateRetrySpecWithBackoff() {
            Retry retry = retrySupport.transientRetry(3, Duration.ofMillis(100));
            assertThat(retry).isNotNull();
        }
    }

    @Nested
    @DisplayName("unwrap")
    class Unwrap {

        @Test
        @DisplayName("should unwrap regular exception")
        void shouldUnwrapRegularException() {
            RuntimeException original = new RuntimeException("test");
            Throwable result = retrySupport.unwrap(original);
            assertThat(result).isEqualTo(original);
        }

        @Test
        @DisplayName("should unwrap retry exhausted exception")
        void shouldUnwrapRetryExhaustedException() {
            RuntimeException cause = new RuntimeException("original cause");
            Throwable retryExhausted = Exceptions.retryExhausted("Retry exhausted", cause);

            Throwable result = retrySupport.unwrap(retryExhausted);
            assertThat(result).isEqualTo(cause);
        }

        @Test
        @DisplayName("should return retry exhausted when no cause")
        void shouldReturnRetryExhaustedWhenNoCause() {
            Throwable retryExhausted = Exceptions.retryExhausted("Retry exhausted", null);

            Throwable result = retrySupport.unwrap(retryExhausted);
            assertThat(result.getMessage()).contains("Retry exhausted");
        }
    }
}
