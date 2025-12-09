package com.code.integration.infrastructure.adapter.inbound.event;

import com.code.events.context.ContextCollectedEvent;
import com.code.events.context.ContextCollectionStatus;
import com.code.events.integration.CommentPostingFailedEvent;
import com.code.integration.application.port.inbound.CommentPostingService;
import com.code.integration.application.port.outbound.EventPublisher;
import com.code.integration.domain.model.ReviewComment;
import com.code.integration.infrastructure.config.KafkaTopicProperties;
import com.code.integration.infrastructure.support.ReactiveRetrySupport;
import com.code.platform.dlt.DltPublisher;
import com.code.platform.idempotency.IdempotencyStore;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContextCollectedEventListener")
class ContextCollectedEventListenerTest {

    @Mock
    private CommentPostingService commentPostingService;

    @Mock
    private EventPublisher eventPublisher;


    @Mock
    private Acknowledgment ack;

    @Mock
    private KafkaTopicProperties topicProperties;

    @Mock
    private ReactiveRetrySupport retrySupport;

    @Mock
    private IdempotencyStore idempotencyStore;

    @Mock
    private DltPublisher dltPublisher;

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
        listener = new ContextCollectedEventListener(commentPostingService, eventPublisher,
                idempotencyStore, dltPublisher, topicProperties, retrySupport);
    }

    private void stubRetrySupport() {
        when(retrySupport.transientRetry(anyInt(), any())).thenReturn(reactor.util.retry.Retry.max(0));
        when(retrySupport.unwrap(any())).thenAnswer(inv -> {
            Throwable error = inv.getArgument(0);
            Throwable unwrapped = reactor.core.Exceptions.unwrap(error);
            return (reactor.core.Exceptions.isRetryExhausted(unwrapped) && unwrapped.getCause() != null)
                    ? unwrapped.getCause()
                    : unwrapped;
        });
    }

    private void stubRetryTransientOnly() {
        when(retrySupport.transientRetry(anyInt(), any())).thenReturn(reactor.util.retry.Retry.max(0));
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
            stubRetryTransientOnly();
            when(idempotencyStore.tryStart(any())).thenReturn(true);
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
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("should post SKIPPED status notification")
        void shouldPostSkippedNotification() throws Exception {
            stubRetryTransientOnly();
            when(idempotencyStore.tryStart(any())).thenReturn(true);
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
            verify(ack).acknowledge();
        }
    }

    @Nested
    @DisplayName("when handling idempotency")
    class WhenHandlingIdempotency {

        @Test
        @DisplayName("should skip duplicate events and acknowledge")
        void shouldSkipDuplicateEvents() throws Exception {
            stubRetryTransientOnly();
            ContextCollectedEvent event = createEvent(ContextCollectionStatus.FAILED, null);
            CountDownLatch latch = new CountDownLatch(1);

            when(idempotencyStore.tryStart(EVENT_ID)).thenReturn(true, false);
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
        }

        @Test
        @DisplayName("should process new events")
        void shouldProcessNewEvents() throws Exception {
            stubRetryTransientOnly();
            ContextCollectedEvent event = createEvent(ContextCollectionStatus.FAILED, null);
            CountDownLatch latch = new CountDownLatch(1);

            when(idempotencyStore.tryStart(EVENT_ID)).thenReturn(true);
            when(commentPostingService.postComment(any())).thenReturn(Mono.empty());
            doAnswer(inv -> {
                latch.countDown();
                return null;
            }).when(ack).acknowledge();

            listener.onContextCollected(event, ack);

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

            verify(commentPostingService).postComment(any());
        }
    }

    @Nested
    @DisplayName("when comment posting fails")
    class WhenCommentPostingFails {

        @BeforeEach
        void setUp() {
            stubRetrySupport();
            when(idempotencyStore.tryStart(any())).thenReturn(true);
        }

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
        @DisplayName("should forward to DLT when failed event publish fails")
        void shouldForwardToDltWhenFailedEventPublishFails() throws Exception {
            ContextCollectedEvent event = createEvent(ContextCollectionStatus.FAILED, null);
            CountDownLatch latch = new CountDownLatch(1);
            RuntimeException commentError = new RuntimeException("Comment posting failed");
            RuntimeException publishError = new RuntimeException("Publish failed");

            when(commentPostingService.postComment(any())).thenReturn(Mono.error(commentError));
            when(eventPublisher.publish(any(CommentPostingFailedEvent.class))).thenReturn(Mono.error(publishError));
            when(topicProperties.contextCollected()).thenReturn("context-collected");
            doAnswer(inv -> {
                latch.countDown();
                return null;
            }).when(dltPublisher).forwardToDlt(any(), any(), any(), any());

            listener.onContextCollected(event, ack);

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

            verify(dltPublisher).forwardToDlt(eq("context-collected.dlt"), eq(EVENT_ID), eq(event), eq(ack));
        }
    }
}
