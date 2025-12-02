package com.code.integration.infrastructure.adapter.inbound.event;

import com.code.events.integration.CommentPostingFailedEvent;
import com.code.events.review.ReviewFailedEvent;
import com.code.integration.application.port.inbound.CommentPostingService;
import com.code.integration.application.port.outbound.EventPublisher;
import com.code.integration.domain.model.ReviewComment;
import com.code.platform.metrics.MetricsHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewFailedEventListener")
class ReviewFailedEventListenerTest {

    @Mock
    private CommentPostingService commentPostingService;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private MetricsHelper metricsHelper;

    @Mock
    private Acknowledgment ack;

    private ReviewFailedEventListener listener;

    private static final String EVENT_ID = "event-123";
    private static final String REVIEW_ID = "review-456";
    private static final String CONTEXT_ID = "context-789";
    private static final String OWNER = "owner";
    private static final String REPO = "repo";
    private static final int PR_NUMBER = 123;
    private static final String ERROR_MESSAGE = "AI service unavailable";
    private static final String CORRELATION_ID = "corr-001";

    @BeforeEach
    void setUp() {
        listener = new ReviewFailedEventListener(commentPostingService, eventPublisher, metricsHelper);
    }

    private ReviewFailedEvent createEvent() {
        return new ReviewFailedEvent(
                EVENT_ID, REVIEW_ID, CONTEXT_ID,
                OWNER, REPO, PR_NUMBER,
                ERROR_MESSAGE, CORRELATION_ID, Instant.now()
        );
    }

    @Nested
    @DisplayName("when handling idempotency")
    class WhenHandlingIdempotency {

        @Test
        @DisplayName("should skip duplicate events and acknowledge")
        void shouldSkipDuplicateEvents() throws Exception {
            ReviewFailedEvent event = createEvent();
            CountDownLatch latch = new CountDownLatch(1); // Only first call goes through subscribe

            when(commentPostingService.postComment(any())).thenReturn(Mono.empty());
            doAnswer(inv -> {
                latch.countDown();
                return null;
            }).when(ack).acknowledge();

            listener.onReviewFailed(event, ack);
            listener.onReviewFailed(event, ack); // Duplicate - returns early

            assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();

            verify(commentPostingService, times(1)).postComment(any());
            verify(ack, times(2)).acknowledge(); // Both calls ack (first in subscribe, second in isDuplicate)
            verify(metricsHelper).incrementCounter("event.idempotency", "result", "duplicate");
        }

        @Test
        @DisplayName("should process new events")
        void shouldProcessNewEvents() throws Exception {
            ReviewFailedEvent event = createEvent();
            CountDownLatch latch = new CountDownLatch(1);

            when(commentPostingService.postComment(any())).thenReturn(Mono.empty());
            doAnswer(inv -> {
                latch.countDown();
                return null;
            }).when(ack).acknowledge();

            listener.onReviewFailed(event, ack);

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

            verify(metricsHelper).incrementCounter("event.idempotency", "result", "new");
            verify(commentPostingService).postComment(any());
        }
    }

    @Nested
    @DisplayName("when comment posting succeeds")
    class WhenCommentPostingSucceeds {

        @Test
        @DisplayName("should post failure notification comment and acknowledge")
        void shouldPostFailureNotificationAndAcknowledge() throws Exception {
            ReviewFailedEvent event = createEvent();
            CountDownLatch latch = new CountDownLatch(1);

            when(commentPostingService.postComment(any())).thenReturn(Mono.empty());
            doAnswer(inv -> {
                latch.countDown();
                return null;
            }).when(ack).acknowledge();

            listener.onReviewFailed(event, ack);

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

            ArgumentCaptor<ReviewComment> captor = ArgumentCaptor.forClass(ReviewComment.class);
            verify(commentPostingService).postComment(captor.capture());

            ReviewComment capturedComment = captor.getValue();
            assertThat(capturedComment.repositoryOwner()).isEqualTo(OWNER);
            assertThat(capturedComment.repositoryName()).isEqualTo(REPO);
            assertThat(capturedComment.pullRequestNumber()).isEqualTo(PR_NUMBER);
            assertThat(capturedComment.body()).contains("Code Review Failed");
            assertThat(capturedComment.body()).contains(ERROR_MESSAGE);
            assertThat(capturedComment.body()).contains(CORRELATION_ID);

            verify(metricsHelper).incrementCounter("comment.posting", "status", "success", "type", "failure_notification");
            verify(ack).acknowledge();
        }
    }

    @Nested
    @DisplayName("when comment posting fails")
    class WhenCommentPostingFails {

        @Test
        @DisplayName("should publish failed event and acknowledge")
        void shouldPublishFailedEventAndAcknowledge() throws Exception {
            ReviewFailedEvent event = createEvent();
            CountDownLatch latch = new CountDownLatch(1);
            RuntimeException error = new RuntimeException("Comment posting failed");

            when(commentPostingService.postComment(any())).thenReturn(Mono.error(error));
            when(eventPublisher.publish(any(CommentPostingFailedEvent.class))).thenReturn(Mono.empty());
            doAnswer(inv -> {
                latch.countDown();
                return null;
            }).when(ack).acknowledge();

            listener.onReviewFailed(event, ack);

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

            verify(metricsHelper).incrementCounter("comment.posting", "status", "failure", "type", "UNKNOWN");
            verify(eventPublisher).publish(any(CommentPostingFailedEvent.class));
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("should handle failed event publish failure")
        void shouldHandleFailedEventPublishFailure() throws Exception {
            ReviewFailedEvent event = createEvent();
            CountDownLatch latch = new CountDownLatch(1);
            RuntimeException commentError = new RuntimeException("Comment posting failed");
            RuntimeException publishError = new RuntimeException("Publish failed");

            when(commentPostingService.postComment(any())).thenReturn(Mono.error(commentError));
            when(eventPublisher.publish(any(CommentPostingFailedEvent.class))).thenReturn(Mono.error(publishError));
            doAnswer(inv -> {
                latch.countDown();
                return null;
            }).when(ack).acknowledge();

            listener.onReviewFailed(event, ack);

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

            verify(metricsHelper).incrementCounter("comment.posting.event", "status", "publish_failed");
            verify(ack).acknowledge();
        }
    }
}
