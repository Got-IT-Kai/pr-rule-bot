package com.code.integration.infrastructure.support;

import com.code.integration.domain.model.ErrorType;
import org.springframework.stereotype.Component;
import reactor.core.Exceptions;
import reactor.util.retry.Retry;

import java.time.Duration;

@Component
public class ReactiveRetrySupport {

    public Retry transientRetry(int maxAttempts, Duration initialBackoff) {
        return Retry.backoff(maxAttempts, initialBackoff)
                .maxBackoff(Duration.ofSeconds(1))
                .filter(this::isTransientError);
    }

    private boolean isTransientError(Throwable error) {
        ErrorType type = ErrorType.from(error);
        return type == ErrorType.HTTP_5XX || type == ErrorType.NETWORK || type == ErrorType.TIMEOUT;
    }

    public Throwable unwrap(Throwable error) {
        Throwable unwrapped = Exceptions.unwrap(error);
        return (Exceptions.isRetryExhausted(unwrapped) && unwrapped.getCause() != null)
                ? unwrapped.getCause()
                : unwrapped;
    }
}
