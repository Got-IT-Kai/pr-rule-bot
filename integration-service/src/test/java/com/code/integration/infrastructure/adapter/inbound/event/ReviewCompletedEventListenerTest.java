package com.code.integration.infrastructure.adapter.inbound.event;

import com.code.events.integration.CommentPostingFailedEvent;
import com.code.events.review.ReviewCompletedEvent;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewCompletedEventListener")
class ReviewCompletedEventListenerTest {

    @Mock
    private CommentPostingService commentPostingService;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private MetricsHelper metricsHelper;

    @Mock
    private Acknowledgment ack;

    private ReviewCompletedEventListener listener;

    private static final String EVENT_ID = "event-123";
    private static final String REVIEW_ID = "review-456";
    private static final String CONTEXT_ID = "context-789";
    private static final String OWNER = "owner";
    private static final String REPO = "repo";
    private static final int PR_NUMBER = 123;
    private static final String MARKDOWN = "# Code Review\n\nLooks good!";
    private static final String CORRELATION_ID = "corr-001";

    @BeforeEach
    void setUp() {
        listener = new ReviewCompletedEventListener(commentPostingService, eventPublisher, metricsHelper);
    }

    private ReviewCompletedEvent createEvent() {
        return new ReviewCompletedEvent(
                EVENT_ID, REVIEW_ID, CONTEXT_ID,
                OWNER, REPO, PR_NUMBER, MARKDOWN,
                "ollama", "qwen2.5-coder:3b",
                CORRELATION_ID, Instant.now()
        );
    }

    @Nested
    @DisplayName("when handling idempotency")
    class WhenHandlingIdempotency {

        @Test
        @DisplayName("should skip duplicate events and acknowledge")
        void shouldSkipDuplicateEvents() throws Exception {
            ReviewCompletedEvent event = createEvent();
            CountDownLatch latch = new CountDownLatch(1); // Only first call goes through subscribe

            when(commentPostingService.postComment(any())).thenReturn(Mono.empty());
            doAnswer(inv -> {
                latch.countDown();
                return null;
            }).when(ack).acknowledge();

            listener.onReviewCompleted(event, ack);
            listener.onReviewCompleted(event, ack); // Duplicate - returns early

            assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();

            verify(commentPostingService, times(1)).postComment(any());
            verify(ack, times(2)).acknowledge(); // Both calls ack (first in subscribe, second in isDuplicate)
            verify(metricsHelper).incrementCounter("event.idempotency", "result", "duplicate");
        }

        @Test
        @DisplayName("should process new events")
        void shouldProcessNewEvents() throws Exception {
            ReviewCompletedEvent event = createEvent();
            CountDownLatch latch = new CountDownLatch(1);

            when(commentPostingService.postComment(any())).thenReturn(Mono.empty());
            doAnswer(inv -> {
                latch.countDown();
                return null;
            }).when(ack).acknowledge();

            listener.onReviewCompleted(event, ack);

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

            verify(metricsHelper).incrementCounter("event.idempotency", "result", "new");
            verify(commentPostingService).postComment(any());
        }
    }

    @Nested
    @DisplayName("when comment posting succeeds")
    class WhenCommentPostingSucceeds {

        @Test
        @DisplayName("should post comment with correct content and acknowledge")
        void shouldPostCommentAndAcknowledge() throws Exception {
            ReviewCompletedEvent event = createEvent();
            CountDownLatch latch = new CountDownLatch(1);

            when(commentPostingService.postComment(any())).thenReturn(Mono.empty());
            doAnswer(inv -> {
                latch.countDown();
                return null;
            }).when(ack).acknowledge();

            listener.onReviewCompleted(event, ack);

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

            ArgumentCaptor<ReviewComment> captor = ArgumentCaptor.forClass(ReviewComment.class);
            verify(commentPostingService).postComment(captor.capture());

            ReviewComment capturedComment = captor.getValue();
            assertThat(capturedComment.repositoryOwner()).isEqualTo(OWNER);
            assertThat(capturedComment.repositoryName()).isEqualTo(REPO);
            assertThat(capturedComment.pullRequestNumber()).isEqualTo(PR_NUMBER);
            assertThat(capturedComment.body()).isEqualTo(MARKDOWN);

            verify(metricsHelper).incrementCounter("comment.posting", "status", "success", "type", "review_completed");
            verify(ack).acknowledge();
        }
    }

    @Nested
    @DisplayName("when comment posting fails")
    class WhenCommentPostingFails {

        @Test
        @DisplayName("should classify HTTP 4xx errors correctly")
        void shouldClassifyHttp4xxErrors() throws Exception {
            ReviewCompletedEvent event = createEvent();
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

            listener.onReviewCompleted(event, ack);

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

            verify(metricsHelper).incrementCounter("comment.posting", "status", "failure", "type", "HTTP_4XX");
            verify(eventPublisher).publish(any(CommentPostingFailedEvent.class));
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("should classify HTTP 5xx errors correctly")
        void shouldClassifyHttp5xxErrors() throws Exception {
            ReviewCompletedEvent event = createEvent();
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

            listener.onReviewCompleted(event, ack);

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

            verify(metricsHelper).incrementCounter("comment.posting", "status", "failure", "type", "HTTP_5XX");
            verify(eventPublisher).publish(any(CommentPostingFailedEvent.class));
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("should classify network errors correctly")
        void shouldClassifyNetworkErrors() throws Exception {
            ReviewCompletedEvent event = createEvent();
            CountDownLatch latch = new CountDownLatch(1);
            WebClientRequestException error = new WebClientRequestException(
                    new RuntimeException("Connection refused"),
                    HttpMethod.POST,
                    URI.create("http://localhost"),
                    HttpHeaders.EMPTY
            );

            when(commentPostingService.postComment(any())).thenReturn(Mono.error(error));
            when(eventPublisher.publish(any(CommentPostingFailedEvent.class))).thenReturn(Mono.empty());
            doAnswer(inv -> {
                latch.countDown();
                return null;
            }).when(ack).acknowledge();

            listener.onReviewCompleted(event, ack);

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

            verify(metricsHelper).incrementCounter("comment.posting", "status", "failure", "type", "NETWORK");
            verify(eventPublisher).publish(any(CommentPostingFailedEvent.class));
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("should classify timeout errors correctly")
        void shouldClassifyTimeoutErrors() throws Exception {
            ReviewCompletedEvent event = createEvent();
            CountDownLatch latch = new CountDownLatch(1);
            TimeoutException error = new TimeoutException("Request timeout");

            when(commentPostingService.postComment(any())).thenReturn(Mono.error(error));
            when(eventPublisher.publish(any(CommentPostingFailedEvent.class))).thenReturn(Mono.empty());
            doAnswer(inv -> {
                latch.countDown();
                return null;
            }).when(ack).acknowledge();

            listener.onReviewCompleted(event, ack);

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

            verify(metricsHelper).incrementCounter("comment.posting", "status", "failure", "type", "TIMEOUT");
            verify(eventPublisher).publish(any(CommentPostingFailedEvent.class));
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("should classify unknown errors correctly")
        void shouldClassifyUnknownErrors() throws Exception {
            ReviewCompletedEvent event = createEvent();
            CountDownLatch latch = new CountDownLatch(1);
            RuntimeException error = new RuntimeException("Unknown error");

            when(commentPostingService.postComment(any())).thenReturn(Mono.error(error));
            when(eventPublisher.publish(any(CommentPostingFailedEvent.class))).thenReturn(Mono.empty());
            doAnswer(inv -> {
                latch.countDown();
                return null;
            }).when(ack).acknowledge();

            listener.onReviewCompleted(event, ack);

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

            verify(metricsHelper).incrementCounter("comment.posting", "status", "failure", "type", "UNKNOWN");
            verify(eventPublisher).publish(any(CommentPostingFailedEvent.class));
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("should publish failed event with correct details")
        void shouldPublishFailedEventWithCorrectDetails() throws Exception {
            ReviewCompletedEvent event = createEvent();
            CountDownLatch latch = new CountDownLatch(1);
            RuntimeException error = new RuntimeException("Test error");

            when(commentPostingService.postComment(any())).thenReturn(Mono.error(error));
            when(eventPublisher.publish(any(CommentPostingFailedEvent.class))).thenReturn(Mono.empty());
            doAnswer(inv -> {
                latch.countDown();
                return null;
            }).when(ack).acknowledge();

            listener.onReviewCompleted(event, ack);

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

            ArgumentCaptor<CommentPostingFailedEvent> captor = ArgumentCaptor.forClass(CommentPostingFailedEvent.class);
            verify(eventPublisher).publish(captor.capture());

            CommentPostingFailedEvent failedEvent = captor.getValue();
            assertThat(failedEvent.reviewId()).isEqualTo(REVIEW_ID);
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
            ReviewCompletedEvent event = createEvent();
            CountDownLatch latch = new CountDownLatch(1);
            RuntimeException commentError = new RuntimeException("Comment posting failed");
            RuntimeException publishError = new RuntimeException("Publish failed");

            when(commentPostingService.postComment(any())).thenReturn(Mono.error(commentError));
            when(eventPublisher.publish(any(CommentPostingFailedEvent.class))).thenReturn(Mono.error(publishError));
            doAnswer(inv -> {
                latch.countDown();
                return null;
            }).when(ack).acknowledge();

            listener.onReviewCompleted(event, ack);

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

            verify(metricsHelper).incrementCounter("comment.posting.event", "status", "publish_failed");
            verify(ack).acknowledge();
        }
    }
}
