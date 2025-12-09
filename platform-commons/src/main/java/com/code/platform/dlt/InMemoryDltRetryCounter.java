package com.code.platform.dlt;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
public class InMemoryDltRetryCounter implements DltRetryCounter {

    private static final Duration TTL = Duration.ofHours(24);
    private static final int MAX_SIZE = 10_000;

    private final Cache<String, Integer> retryCounters = Caffeine.newBuilder()
            .expireAfterWrite(TTL)
            .maximumSize(MAX_SIZE)
            .build();

    @Override
    public int incrementAndGet(String eventId) {
        if (eventId == null) {
            log.warn("Attempted to increment retry count for null eventId");
            return 0;
        }
        return retryCounters.asMap().merge(eventId, 1, Integer::sum);
    }

    @Override
    public void reset(String eventId) {
        retryCounters.invalidate(eventId);
    }
}
