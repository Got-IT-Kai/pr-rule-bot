package com.code.review.infrastructure.adapter.inbound.event;

import com.code.events.context.ContextCollectedEvent;
import com.code.events.context.ContextCollectionStatus;
import com.code.platform.dlt.DltPublisher;
import com.code.platform.idempotency.IdempotencyStore;
import com.code.review.application.port.inbound.ReviewService;
import com.code.review.domain.model.ReviewResult;
import com.code.review.domain.model.ReviewStatus;
import com.code.review.infrastructure.config.KafkaTopicProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContextCollectedEventListener")
class ContextCollectedEventListenerTest {

    @Mock
    private ReviewService reviewService;

    @Mock
    private IdempotencyStore idempotencyStore;

    @Mock
    private DltPublisher dltPublisher;

    @Mock
    private Acknowledgment ack;

    @Mock
    private KafkaTopicProperties topicProperties;

    private ContextCollectedEventListener listener;

    private static final String EVENT_ID = "event-123";
    private static final String CONTEXT_ID = "context-456";
    private static final String OWNER = "owner";
    private static final String REPO = "repo";
    private static final int PR_NUMBER = 123;
    private static final String TITLE = "Add feature";
    private static final String DIFF = "diff --git a/file.txt b/file.txt\n+new line";
    private static final String CORRELATION_ID = "corr-001";

    @BeforeEach
    void setUp() {
        listener = new ContextCollectedEventListener(reviewService,
                idempotencyStore, dltPublisher, topicProperties);
    }

    private ContextCollectedEvent createEvent(ContextCollectionStatus status) {
        return new ContextCollectedEvent(
                EVENT_ID, CONTEXT_ID,
                OWNER, REPO, PR_NUMBER,
                TITLE, DIFF, status,
                CORRELATION_ID, Instant.now()
        );
    }

    private ReviewResult createReviewResult(ReviewStatus status) {
        return new ReviewResult(
                "review-123",
                CONTEXT_ID,
                OWNER,
                REPO,
                PR_NUMBER,
                "Review comment",
                status,
                "ollama",
                "qwen2.5-coder:3b",
                CORRELATION_ID,
                Instant.now()
        );
    }

    @Nested
    @DisplayName("when handling idempotency")
    class WhenHandlingIdempotency {

        @Test
        @DisplayName("should skip duplicate events and acknowledge")
        void shouldSkipDuplicateEvents() throws Exception {
            ContextCollectedEvent event = createEvent(ContextCollectionStatus.COMPLETED);
            CountDownLatch latch = new CountDownLatch(1);

            when(idempotencyStore.tryStart(EVENT_ID)).thenReturn(true, false);
            when(reviewService.perform(anyString(), anyString(), anyString(), anyInt(), anyString(), anyString(), anyString()))
                    .thenReturn(Mono.just(createReviewResult(ReviewStatus.COMPLETED)));
            doAnswer(inv -> {
                latch.countDown();
                return null;
            }).when(ack).acknowledge();

            listener.onContextCollected(event, ack);
            listener.onContextCollected(event, ack); // Duplicate - returns early

            assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();

            verify(reviewService, times(1)).perform(anyString(), anyString(), anyString(), anyInt(), anyString(), anyString(), anyString());
            verify(ack, times(2)).acknowledge();
        }

        @Test
        @DisplayName("should process new events")
        void shouldProcessNewEvents() throws Exception {
            ContextCollectedEvent event = createEvent(ContextCollectionStatus.COMPLETED);
            CountDownLatch latch = new CountDownLatch(1);

            when(idempotencyStore.tryStart(EVENT_ID)).thenReturn(true);
            when(reviewService.perform(anyString(), anyString(), anyString(), anyInt(), anyString(), anyString(), anyString()))
                    .thenReturn(Mono.just(createReviewResult(ReviewStatus.COMPLETED)));
            doAnswer(inv -> {
                latch.countDown();
                return null;
            }).when(ack).acknowledge();

            listener.onContextCollected(event, ack);

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

            verify(reviewService).perform(anyString(), anyString(), anyString(), anyInt(), anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("when handling non-completed status")
    class WhenHandlingNonCompletedStatus {

        @Test
        @DisplayName("should skip FAILED status and acknowledge")
        void shouldSkipFailedStatus() {
            ContextCollectedEvent event = createEvent(ContextCollectionStatus.FAILED);

            listener.onContextCollected(event, ack);

            verify(reviewService, never()).perform(anyString(), anyString(), anyString(), anyInt(), anyString(), anyString(), anyString());
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("should skip PENDING status and acknowledge")
        void shouldSkipPendingStatus() {
            ContextCollectedEvent event = createEvent(ContextCollectionStatus.PENDING);

            listener.onContextCollected(event, ack);

            verify(reviewService, never()).perform(anyString(), anyString(), anyString(), anyInt(), anyString(), anyString(), anyString());
            verify(ack).acknowledge();
        }
    }

    @Nested
    @DisplayName("when handling empty diff")
    class WhenHandlingEmptyDiff {

        @Test
        @DisplayName("should skip null diff and acknowledge")
        void shouldSkipNullDiff() {
            ContextCollectedEvent event = new ContextCollectedEvent(
                    EVENT_ID, CONTEXT_ID,
                    OWNER, REPO, PR_NUMBER,
                    TITLE, null, ContextCollectionStatus.COMPLETED,
                    CORRELATION_ID, Instant.now()
            );

            listener.onContextCollected(event, ack);

            verify(reviewService, never()).perform(anyString(), anyString(), anyString(), anyInt(), anyString(), anyString(), anyString());
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("should skip blank diff and acknowledge")
        void shouldSkipBlankDiff() {
            ContextCollectedEvent event = new ContextCollectedEvent(
                    EVENT_ID, CONTEXT_ID,
                    OWNER, REPO, PR_NUMBER,
                    TITLE, "   ", ContextCollectionStatus.COMPLETED,
                    CORRELATION_ID, Instant.now()
            );

            listener.onContextCollected(event, ack);

            verify(reviewService, never()).perform(anyString(), anyString(), anyString(), anyInt(), anyString(), anyString(), anyString());
            verify(ack).acknowledge();
        }
    }

    @Nested
    @DisplayName("when review completes successfully")
    class WhenReviewCompletesSuccessfully {

        @Test
        @DisplayName("should record success metrics and acknowledge")
        void shouldRecordSuccessMetrics() throws Exception {
            ContextCollectedEvent event = createEvent(ContextCollectionStatus.COMPLETED);
            CountDownLatch latch = new CountDownLatch(1);

            when(idempotencyStore.tryStart(any())).thenReturn(true);
            when(reviewService.perform(anyString(), anyString(), anyString(), anyInt(), anyString(), anyString(), anyString()))
                    .thenReturn(Mono.just(createReviewResult(ReviewStatus.COMPLETED)));
            doAnswer(inv -> {
                latch.countDown();
                return null;
            }).when(ack).acknowledge();

            listener.onContextCollected(event, ack);

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

            verify(ack).acknowledge();
        }
    }

    @Nested
    @DisplayName("when review returns FAILED status")
    class WhenReviewReturnsFailedStatus {

        @Test
        @DisplayName("should record failure metrics and nack for FAILED result")
        void shouldRecordFailureMetricsForFailedResult() throws Exception {
            ContextCollectedEvent event = createEvent(ContextCollectionStatus.COMPLETED);
            CountDownLatch latch = new CountDownLatch(1);

            // ReviewService returns FAILED result (error was handled internally)
            when(idempotencyStore.tryStart(any())).thenReturn(true);
            when(reviewService.perform(anyString(), anyString(), anyString(), anyInt(), anyString(), anyString(), anyString()))
                    .thenReturn(Mono.just(createReviewResult(ReviewStatus.FAILED)));
            when(topicProperties.contextCollected()).thenReturn("context.collected");
            doAnswer(inv -> {
                latch.countDown();
                return null;
            }).when(dltPublisher).forwardToDlt(any(), any(), any(), any());

            listener.onContextCollected(event, ack);

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

            verify(dltPublisher).forwardToDlt(eq("context.collected.dlt"), eq(EVENT_ID), eq(event), eq(ack));
        }
    }

    @Nested
    @DisplayName("when review throws error")
    class WhenReviewThrowsError {

        @Test
        @DisplayName("should record failure metrics and nack")
        void shouldRecordFailureMetrics() throws Exception {
            ContextCollectedEvent event = createEvent(ContextCollectionStatus.COMPLETED);
            CountDownLatch latch = new CountDownLatch(1);

            // ReviewService throws error (rare case, most errors are handled internally)
            when(idempotencyStore.tryStart(any())).thenReturn(true);
            when(reviewService.perform(anyString(), anyString(), anyString(), anyInt(), anyString(), anyString(), anyString()))
                    .thenReturn(Mono.error(new RuntimeException("Unexpected error")));
            when(topicProperties.contextCollected()).thenReturn("context.collected");
            doAnswer(inv -> {
                latch.countDown();
                return null;
            }).when(dltPublisher).forwardToDlt(any(), any(), any(), any());

            listener.onContextCollected(event, ack);

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

            verify(dltPublisher).forwardToDlt(eq("context.collected.dlt"), eq(EVENT_ID), eq(event), eq(ack));
        }
    }
}
