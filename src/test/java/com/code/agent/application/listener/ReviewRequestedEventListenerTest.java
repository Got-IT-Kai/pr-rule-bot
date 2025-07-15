package com.code.agent.application.listener;

import com.code.agent.application.event.ReviewCompletedEvent;
import com.code.agent.application.event.ReviewRequestedEvent;
import com.code.agent.application.port.out.AiPort;
import com.code.agent.application.port.out.GitHubPort;
import com.code.agent.domain.model.PullRequestReviewInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

@SpringBootTest
@RecordApplicationEvents
class ReviewRequestedEventListenerTest {

    @Autowired
    private ReviewRequestedEventListener reviewRequestedEventListener;

    @MockitoBean
    private AiPort aiPort;

    @MockitoBean
    private GitHubPort gitHubPort;

    @Test
    void checkEventPublishing(ApplicationEvents applicationEvents) {
        when(aiPort.evaluateDiff("mock diff")).thenReturn("mock comment");
        PullRequestReviewInfo reviewInfo = new PullRequestReviewInfo("owner", "name", 1, "diffUrl");
        ReviewRequestedEvent event = new ReviewRequestedEvent(reviewInfo, "mock diff");

        reviewRequestedEventListener.handleReviewRequestedEvent(event);

        await().atMost(5, SECONDS).untilAsserted(() -> assertThat(applicationEvents.stream(ReviewCompletedEvent.class)).hasSize(1));

    }

}