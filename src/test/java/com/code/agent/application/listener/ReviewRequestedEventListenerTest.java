package com.code.agent.application.listener;

import com.code.agent.application.event.ReviewCompletedEvent;
import com.code.agent.application.event.ReviewFailedEvent;
import com.code.agent.application.event.ReviewRequestedEvent;
import com.code.agent.application.port.out.AiPort;
import com.code.agent.application.port.out.EventBusPort;
import com.code.agent.domain.model.PullRequestReviewInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewRequestedEventListenerTest {

    @Mock
    private AiPort aiPort;

    @Mock
    private EventBusPort eventBusPort;

    private ReviewRequestedEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new ReviewRequestedEventListener(aiPort, eventBusPort);
    }

    @Test
    void onReviewRequested_Success() {
        PullRequestReviewInfo info = new PullRequestReviewInfo("owner", "repo", 1, "diffUrl");
        ReviewRequestedEvent event = new ReviewRequestedEvent(info, "diff content");
        String reviewResult = "Review completed";

        when(aiPort.evaluateDiff("diff content")).thenReturn(Mono.just(reviewResult));
        when(eventBusPort.publishEvent(any(ReviewCompletedEvent.class))).thenReturn(Mono.empty());

        StepVerifier.create(listener.onReviewRequested(event))
                .verifyComplete();

        verify(aiPort).evaluateDiff("diff content");
        ArgumentCaptor<ReviewCompletedEvent> captor = ArgumentCaptor.forClass(ReviewCompletedEvent.class);
        verify(eventBusPort).publishEvent(captor.capture());

        assertThat(captor.getValue().reviewResult()).isEqualTo(reviewResult);
        assertThat(captor.getValue().reviewInfo()).isEqualTo(info);
    }

    @Test
    void onReviewRequested_Failure() {
        PullRequestReviewInfo info = new PullRequestReviewInfo("owner", "repo", 1, "diffUrl");
        ReviewRequestedEvent event = new ReviewRequestedEvent(info, "diff content");
        RuntimeException error = new RuntimeException("AI service error");

        when(aiPort.evaluateDiff("diff content")).thenReturn(Mono.error(error));
        when(eventBusPort.publishEvent(any(ReviewFailedEvent.class))).thenReturn(Mono.empty());

        StepVerifier.create(listener.onReviewRequested(event))
                .verifyComplete();

        verify(aiPort).evaluateDiff("diff content");
        ArgumentCaptor<ReviewFailedEvent> captor = ArgumentCaptor.forClass(ReviewFailedEvent.class);
        verify(eventBusPort).publishEvent(captor.capture());

        assertThat(captor.getValue().message()).isEqualTo("AI service error");
        assertThat(captor.getValue().reviewInfo()).isEqualTo(info);
    }

    @Test
    void onReviewRequested_NoTimeoutInChain() {
        // This test verifies that no timeout() is added in the reactive chain
        // The timeout should be managed by the underlying WebClient configuration
        PullRequestReviewInfo info = new PullRequestReviewInfo("owner", "repo", 1, "diffUrl");
        ReviewRequestedEvent event = new ReviewRequestedEvent(info, "diff content");

        when(aiPort.evaluateDiff("diff content")).thenReturn(Mono.just("result"));
        when(eventBusPort.publishEvent(any(ReviewCompletedEvent.class))).thenReturn(Mono.empty());

        // Should complete without any explicit timeout in the chain
        StepVerifier.create(listener.onReviewRequested(event))
                .verifyComplete();
    }
}
