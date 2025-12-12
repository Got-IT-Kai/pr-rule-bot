package com.code.platform.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InMemoryIdempotencyStore")
class InMemoryIdempotencyStoreTest {

    InMemoryIdempotencyStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryIdempotencyStore();
    }

    @Nested
    @DisplayName("tryStart")
    class TryStart {

        @Test
        @DisplayName("should return true for new event")
        void shouldReturnTrueForNewEvent() {
            boolean result = store.tryStart("event-1");
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false for duplicate event")
        void shouldReturnFalseForDuplicateEvent() {
            store.tryStart("event-1");
            boolean result = store.tryStart("event-1");
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return true for null eventId")
        void shouldReturnTrueForNullEventId() {
            boolean result = store.tryStart(null);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should handle multiple distinct events")
        void shouldHandleMultipleDistinctEvents() {
            assertThat(store.tryStart("event-1")).isTrue();
            assertThat(store.tryStart("event-2")).isTrue();
            assertThat(store.tryStart("event-3")).isTrue();
        }
    }

    @Nested
    @DisplayName("markProcessed")
    class MarkProcessed {

        @Test
        @DisplayName("should mark event as processed")
        void shouldMarkEventAsProcessed() {
            store.tryStart("event-1");
            store.markProcessed("event-1");

            boolean result = store.tryStart("event-1");
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should handle null eventId gracefully")
        void shouldHandleNullEventIdGracefully() {
            store.markProcessed(null);
        }
    }
}
