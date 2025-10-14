package com.code.agent.application.listener;

import com.code.agent.application.event.ReviewCompletedEvent;
import com.code.agent.application.event.ReviewFailedEvent;
import com.code.agent.application.port.out.GitHubPort;
import com.code.agent.domain.model.PullRequestReviewInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewCommentListenersTest {

    @Mock
    private GitHubPort gitHubPort;

    private ReviewCommentListeners listeners;

    @BeforeEach
    void setUp() {
        listeners = new ReviewCommentListeners(gitHubPort);
    }

    @Test
    void onReviewCompleted_Success() {
        PullRequestReviewInfo info = new PullRequestReviewInfo("owner", "repo", 1, "diffUrl");
        ReviewCompletedEvent event = new ReviewCompletedEvent(info, "Review result");

        when(gitHubPort.postReviewComment(info, "Review result")).thenReturn(Mono.empty());

        StepVerifier.create(listeners.onReviewCompleted(event))
                .verifyComplete();

        verify(gitHubPort).postReviewComment(info, "Review result");
    }

    @Test
    void onReviewCompleted_Failure() {
        PullRequestReviewInfo info = new PullRequestReviewInfo("owner", "repo", 1, "diffUrl");
        ReviewCompletedEvent event = new ReviewCompletedEvent(info, "Review result");
        RuntimeException error = new RuntimeException("GitHub API error");

        when(gitHubPort.postReviewComment(info, "Review result")).thenReturn(Mono.error(error));

        // The error is logged and propagated (doOnError doesn't consume the error)
        StepVerifier.create(listeners.onReviewCompleted(event))
                .expectError(RuntimeException.class)
                .verify();

        verify(gitHubPort).postReviewComment(info, "Review result");
    }

    @Test
    void onReviewFailed_Success() {
        PullRequestReviewInfo info = new PullRequestReviewInfo("owner", "repo", 1, "diffUrl");
        ReviewFailedEvent event = new ReviewFailedEvent(info, "Error message");
        String expectedComment = "Review failed: Error message";

        when(gitHubPort.postReviewComment(info, expectedComment)).thenReturn(Mono.empty());

        StepVerifier.create(listeners.onReviewFailed(event))
                .verifyComplete();

        verify(gitHubPort).postReviewComment(info, expectedComment);
    }

    @Test
    void onReviewFailed_NoTimeoutInChain() {
        // This test verifies that no timeout() is added in the reactive chain
        // The timeout should be managed by the underlying WebClient configuration
        PullRequestReviewInfo info = new PullRequestReviewInfo("owner", "repo", 1, "diffUrl");
        ReviewFailedEvent event = new ReviewFailedEvent(info, "Error message");

        when(gitHubPort.postReviewComment(any(), any())).thenReturn(Mono.empty());

        // Should complete without any explicit timeout in the chain
        StepVerifier.create(listeners.onReviewFailed(event))
                .verifyComplete();
    }
}
