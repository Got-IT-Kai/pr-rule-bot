package com.code.platform.idempotency;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private static final Duration TTL = Duration.ofHours(24);
    private static final int MAX_SIZE = 10_000;

    private enum Status {
        IN_PROGRESS,
        PROCESSED
    }

    private final Cache<String, Status> cache = Caffeine.newBuilder()
            .expireAfterWrite(TTL)
            .maximumSize(MAX_SIZE)
            .build();

    @Override
    public boolean tryStart(String eventId) {
        if (eventId == null) {
            log.warn("Event with null eventId, skipping idempotency check");
            return true;
        }

        Status previous = cache.asMap().putIfAbsent(eventId, Status.IN_PROGRESS);
        return previous == null;
    }

    @Override
    public void markProcessed(String eventId) {
        if (eventId != null) {
            cache.put(eventId, Status.PROCESSED);
        }
    }
}
