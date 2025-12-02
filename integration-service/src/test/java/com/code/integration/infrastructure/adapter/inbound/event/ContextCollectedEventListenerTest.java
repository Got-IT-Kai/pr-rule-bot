package com.code.integration.infrastructure.adapter.inbound.event;

import com.code.events.context.ContextCollectedEvent;
import com.code.events.context.ContextCollectionStatus;
import com.code.events.integration.CommentPostingFailedEvent;
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
@DisplayName("ContextCollectedEventListener")
class ContextCollectedEventListenerTest {

    @Mock
    private CommentPostingService commentPostingService;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private MetricsHelper metricsHelper;

    @Mock
    private Acknowledgment ack;

    private ContextCollectedEventListener listener;

    private static final String EVENT_ID = "event-123";
    private static final String CONTEXT_ID = "context-456";
    private static final String OWNER = "owner";
    private static final String REPO = "repo";
    private static final int PR_NUMBER = 123;
    private static final String TITLE = "Test PR";
    private static final String CORRELATION_ID = "corr-001";

    @BeforeEach
    void setUp() {
        listener = new ContextCollectedEventListener(commentPostingService, eventPublisher, metricsHelper);
    }

    private ContextCollectedEvent createEvent(ContextCollectionStatus status, String diff) {
        return new ContextCollectedEvent(
                EVENT_ID, CONTEXT_ID,
                OWNER, REPO, PR_NUMBER, TITLE,
                diff, status, CORRELATION_ID, Instant.now()
        );
    }

    @Nested
    @DisplayName("when handling status-based routing")
    class WhenHandlingStatusBasedRouting {

        @Test
        @DisplayName("should skip COMPLETED status without posting comment")
        void shouldSkipCompletedStatus() {
            ContextCollectedEvent event = createEvent(ContextCollectionStatus.COMPLETED, "diff content");

            listener.onContextCollected(event, ack);

            verify(commentPostingService, never()).postComment(any());
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("should post FAILED status notification")
        void shouldPostFailedNotification() throws Exception {
            ContextCollectedEvent event = createEvent(ContextCollectionStatus.FAILED, null);
            CountDownLatch latch = new CountDownLatch(1);

            when(commentPostingService.postComment(any())).thenReturn(Mono.empty());
            doAnswer(inv -> {
                latch.countDown();
                return null;
            }).when(ack).acknowledge();

            listener.onContextCollected(event, ack);

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

            ArgumentCaptor<ReviewComment> captor = ArgumentCaptor.forClass(ReviewComment.class);
            verify(commentPostingService).postComment(captor.capture());

            ReviewComment comment = captor.getValue();
            assertThat(comment.repositoryOwner()).isEqualTo(OWNER);
            assertThat(comment.repositoryName()).isEqualTo(REPO);
            assertThat(comment.pullRequestNumber()).isEqualTo(PR_NUMBER);
            assertThat(comment.body()).contains("Code Review Context Collection Failed");
            assertThat(comment.body()).contains(CORRELATION_ID);

            verify(metricsHelper).incrementCounter("comment.posting",
                    "status", "success",
                    "type", "context_failed");
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("should post SKIPPED status notification")
        void shouldPostSkippedNotification() throws Exception {
            ContextCollectedEvent event = createEvent(ContextCollectionStatus.SKIPPED, null);
            CountDownLatch latch = new CountDownLatch(1);

            when(commentPostingService.postComment(any())).thenReturn(Mono.empty());
            doAnswer(inv -> {
                latch.countDown();
                return null;
            }).when(ack).acknowledge();

            listener.onContextCollected(event, ack);

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

            ArgumentCaptor<ReviewComment> captor = ArgumentCaptor.forClass(ReviewComment.class);
            verify(commentPostingService).postComment(captor.capture());

            ReviewComment comment = captor.getValue();
            assertThat(comment.body()).contains("Code Review Skipped");
            assertThat(comment.body()).contains("empty, too large, or consists only of non-reviewable changes");
            assertThat(comment.body()).contains(CORRELATION_ID);

            verify(metricsHelper).incrementCounter("comment.posting",
                    "status", "success",
                    "type", "context_skipped");
            verify(ack).acknowledge();
        }
    }

    @Nested
    @DisplayName("when handling idempotency")
    class WhenHandlingIdempotency {

        @Test
        @DisplayName("should skip duplicate events and acknowledge")
        void shouldSkipDuplicateEvents() throws Exception {
            ContextCollectedEvent event = createEvent(ContextCollectionStatus.FAILED, null);
            CountDownLatch latch = new CountDownLatch(1);

            when(commentPostingService.postComment(any())).thenReturn(Mono.empty());
            doAnswer(inv -> {
                latch.countDown();
                return null;
            }).when(ack).acknowledge();

            listener.onContextCollected(event, ack);
            listener.onContextCollected(event, ack); // Duplicate

            assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();

            verify(commentPostingService, times(1)).postComment(any());
            verify(ack, times(2)).acknowledge();
            verify(metricsHelper).incrementCounter("event.idempotency", "result", "duplicate");
        }

        @Test
        @DisplayName("should process new events")
        void shouldProcessNewEvents() throws Exception {
            ContextCollectedEvent event = createEvent(ContextCollectionStatus.FAILED, null);
            CountDownLatch latch = new CountDownLatch(1);

            when(commentPostingService.postComment(any())).thenReturn(Mono.empty());
            doAnswer(inv -> {
                latch.countDown();
                return null;
            }).when(ack).acknowledge();

            listener.onContextCollected(event, ack);

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

            verify(metricsHelper).incrementCounter("event.idempotency", "result", "new");
            verify(commentPostingService).postComment(any());
        }
    }

    @Nested
    @DisplayName("when comment posting fails")
    class WhenCommentPostingFails {

        @Test
        @DisplayName("should classify HTTP 4xx errors correctly")
        void shouldClassifyHttp4xxErrors() throws Exception {
            ContextCollectedEvent event = createEvent(ContextCollectionStatus.FAILED, null);
            CountDownLatch latch = new CountDownLatch(1);
            WebClientResponseException error = WebClientResponseException.BadRequest.create(
                    400, "Bad Request", null, null, null
            );

            when(commentPostingService.postComment(any())).thenReturn(Mono.error(error));
            when(eventPublisher.publish(any(CommentPostingFailedEvent.class))).thenReturn(Mono.empty());
            doAnswer(inv -> {
                latch.countDown();
                return null;
            }).when(ack).acknowledge();

            listener.onContextCollected(event, ack);

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

            verify(metricsHelper).incrementCounter("comment.posting", "status", "failure", "type", "HTTP_4XX");
            verify(eventPublisher).publish(any(CommentPostingFailedEvent.class));
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("should classify HTTP 5xx errors correctly")
        void shouldClassifyHttp5xxErrors() throws Exception {
            ContextCollectedEvent event = createEvent(ContextCollectionStatus.SKIPPED, null);
            CountDownLatch latch = new CountDownLatch(1);
            WebClientResponseException error = WebClientResponseException.InternalServerError.create(
                    500, "Internal Server Error", null, null, null
            );

            when(commentPostingService.postComment(any())).thenReturn(Mono.error(error));
            when(eventPublisher.publish(any(CommentPostingFailedEvent.class))).thenReturn(Mono.empty());
            doAnswer(inv -> {
                latch.countDown();
                return null;
            }).when(ack).acknowledge();

            listener.onContextCollected(event, ack);

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

            verify(metricsHelper).incrementCounter("comment.posting", "status", "failure", "type", "HTTP_5XX");
            verify(eventPublisher).publish(any(CommentPostingFailedEvent.class));
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("should publish failed event with correct details")
        void shouldPublishFailedEventWithCorrectDetails() throws Exception {
            ContextCollectedEvent event = createEvent(ContextCollectionStatus.FAILED, null);
            CountDownLatch latch = new CountDownLatch(1);
            RuntimeException error = new RuntimeException("Test error");

            when(commentPostingService.postComment(any())).thenReturn(Mono.error(error));
            when(eventPublisher.publish(any(CommentPostingFailedEvent.class))).thenReturn(Mono.empty());
            doAnswer(inv -> {
                latch.countDown();
                return null;
            }).when(ack).acknowledge();

            listener.onContextCollected(event, ack);

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

            ArgumentCaptor<CommentPostingFailedEvent> captor = ArgumentCaptor.forClass(CommentPostingFailedEvent.class);
            verify(eventPublisher).publish(captor.capture());

            CommentPostingFailedEvent failedEvent = captor.getValue();
            assertThat(failedEvent.reviewId()).isEqualTo(CONTEXT_ID);
            assertThat(failedEvent.repositoryOwner()).isEqualTo(OWNER);
            assertThat(failedEvent.repositoryName()).isEqualTo(REPO);
            assertThat(failedEvent.pullRequestNumber()).isEqualTo(PR_NUMBER);
            assertThat(failedEvent.errorMessage()).isEqualTo("Test error");
            assertThat(failedEvent.errorType()).isEqualTo("UNKNOWN");
            assertThat(failedEvent.correlationId()).isEqualTo(CORRELATION_ID);
        }

        @Test
        @DisplayName("should handle failed event publish failure with logging and metrics")
        void shouldHandleFailedEventPublishFailure() throws Exception {
            ContextCollectedEvent event = createEvent(ContextCollectionStatus.FAILED, null);
            CountDownLatch latch = new CountDownLatch(1);
            RuntimeException commentError = new RuntimeException("Comment posting failed");
            RuntimeException publishError = new RuntimeException("Publish failed");

            when(commentPostingService.postComment(any())).thenReturn(Mono.error(commentError));
            when(eventPublisher.publish(any(CommentPostingFailedEvent.class))).thenReturn(Mono.error(publishError));
            doAnswer(inv -> {
                latch.countDown();
                return null;
            }).when(ack).acknowledge();

            listener.onContextCollected(event, ack);

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

            verify(metricsHelper).incrementCounter("comment.posting.event", "status", "publish_failed");
            verify(ack).acknowledge();
        }
    }
}
