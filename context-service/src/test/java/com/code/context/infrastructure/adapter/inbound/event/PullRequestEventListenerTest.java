package com.code.context.infrastructure.adapter.inbound.event;

import com.code.context.application.port.inbound.ContextCollectionService;
import com.code.context.domain.model.CollectionStatus;
import com.code.context.domain.model.PullRequestContext;
import com.code.context.infrastructure.config.KafkaTopicProperties;
import com.code.events.webhook.PullRequestReceivedEvent;
import com.code.events.webhook.WebhookAction;
import com.code.platform.dlt.DltPublisher;
import com.code.platform.idempotency.IdempotencyStore;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PullRequestEventListener")
class PullRequestEventListenerTest {

    @Mock
    ContextCollectionService contextCollectionService;

    @Mock
    IdempotencyStore idempotencyStore;

    @Mock
    DltPublisher dltPublisher;

    @Mock
    KafkaTopicProperties topicProperties;

    @Mock
    Acknowledgment acknowledgment;

    PullRequestEventListener listener;

    static final String EVENT_ID = "event-123";
    static final String OWNER = "test-owner";
    static final String REPO = "test-repo";
    static final int PR_NUMBER = 42;
    static final String CORRELATION_ID = "correlation-456";

    @BeforeEach
    void setUp() {
        listener = new PullRequestEventListener(
                contextCollectionService, idempotencyStore, dltPublisher, topicProperties);
    }

    PullRequestReceivedEvent createEvent(WebhookAction action) {
        return new PullRequestReceivedEvent(
                EVENT_ID,
                OWNER,
                REPO,
                PR_NUMBER,
                action,
                "Test PR Title",
                "test-author",
                "abc123",
                Instant.now(),
                CORRELATION_ID,
                "github",
                "123456"
        );
    }

    PullRequestContext createContext() {
        return new PullRequestContext(
                "context-789",
                OWNER,
                REPO,
                PR_NUMBER,
                "Test PR Title",
                "/repos/test-owner/test-repo/pulls/42",
                "diff content",
                List.of(),
                null,
                CollectionStatus.COMPLETED,
                CORRELATION_ID,
                Instant.now()
        );
    }

    @Nested
    @DisplayName("when receiving new event")
    class WhenReceivingNewEvent {

        @BeforeEach
        void setUp() {
            when(idempotencyStore.tryStart(EVENT_ID)).thenReturn(true);
            when(contextCollectionService.collect(any(), any(), any(), any(), any(), any()))
                    .thenReturn(Mono.just(createContext()));
        }

        @Test
        @DisplayName("should collect context for OPENED action")
        void shouldCollectContextForOpenedAction() {
            PullRequestReceivedEvent event = createEvent(WebhookAction.OPENED);

            listener.onPullRequestReceived(event, acknowledgment);

            verify(contextCollectionService).collect(
                    eq(OWNER), eq(REPO), eq(PR_NUMBER),
                    eq("Test PR Title"),
                    eq("/repos/test-owner/test-repo/pulls/42"),
                    eq(CORRELATION_ID)
            );
        }

        @Test
        @DisplayName("should collect context for SYNCHRONIZE action")
        void shouldCollectContextForSynchronizeAction() {
            PullRequestReceivedEvent event = createEvent(WebhookAction.SYNCHRONIZE);

            listener.onPullRequestReceived(event, acknowledgment);

            verify(contextCollectionService).collect(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("should acknowledge after successful processing")
        void shouldAcknowledgeAfterSuccess() {
            PullRequestReceivedEvent event = createEvent(WebhookAction.OPENED);

            listener.onPullRequestReceived(event, acknowledgment);

            // Wait for async processing
            verify(idempotencyStore, timeout(1000)).markProcessed(EVENT_ID);
            verify(acknowledgment, timeout(1000)).acknowledge();
        }
    }

    @Nested
    @DisplayName("when receiving duplicate event")
    class WhenReceivingDuplicateEvent {

        @BeforeEach
        void setUp() {
            when(idempotencyStore.tryStart(EVENT_ID)).thenReturn(false);
        }

        @Test
        @DisplayName("should skip processing and acknowledge")
        void shouldSkipAndAcknowledge() {
            PullRequestReceivedEvent event = createEvent(WebhookAction.OPENED);

            listener.onPullRequestReceived(event, acknowledgment);

            verify(contextCollectionService, never()).collect(any(), any(), any(), any(), any(), any());
            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("when receiving non-triggering action")
    class WhenReceivingNonTriggeringAction {

        @BeforeEach
        void setUp() {
            when(idempotencyStore.tryStart(EVENT_ID)).thenReturn(true);
        }

        @Test
        @DisplayName("should skip CLOSED action")
        void shouldSkipClosedAction() {
            PullRequestReceivedEvent event = createEvent(WebhookAction.CLOSED);

            listener.onPullRequestReceived(event, acknowledgment);

            verify(contextCollectionService, never()).collect(any(), any(), any(), any(), any(), any());
            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("when context collection fails")
    class WhenContextCollectionFails {

        @BeforeEach
        void setUp() {
            when(idempotencyStore.tryStart(EVENT_ID)).thenReturn(true);
            when(topicProperties.pullRequestReceived()).thenReturn("pull-request-received");
            when(contextCollectionService.collect(any(), any(), any(), any(), any(), any()))
                    .thenReturn(Mono.error(new RuntimeException("Collection failed")));
        }

        @Test
        @DisplayName("should forward to DLT")
        void shouldForwardToDlt() {
            PullRequestReceivedEvent event = createEvent(WebhookAction.OPENED);

            listener.onPullRequestReceived(event, acknowledgment);

            verify(dltPublisher, timeout(1000)).forwardToDlt(
                    eq("pull-request-received.dlt"),
                    eq(EVENT_ID),
                    eq(event),
                    eq(acknowledgment)
            );
        }
    }
}
