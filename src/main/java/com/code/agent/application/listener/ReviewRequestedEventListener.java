package com.code.agent.application.listener;

import com.code.agent.application.event.ReviewCompletedEvent;
import com.code.agent.application.event.ReviewFailedEvent;
import com.code.agent.application.event.ReviewRequestedEvent;
import com.code.agent.application.port.out.AiPort;
import com.code.agent.application.port.out.EventBusPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewRequestedEventListener {
    private final AiPort aiPort;
    private final EventBusPort eventBusPort;

    @EventListener
    public Mono<Void> onReviewRequested(ReviewRequestedEvent event) {

        return aiPort.evaluateDiff(event.diff())
                .map(result -> {
                    log.debug("Pull request {} review completed with result: {}", event.reviewInfo().pullRequestNumber(), result);
                    return new ReviewCompletedEvent(event.reviewInfo(), result);
                })
                .publishOn(Schedulers.boundedElastic())
                .flatMap(eventBusPort::publishEvent)
                .onErrorResume(error -> {
                    log.debug("Pull request {} review failed", event.reviewInfo().pullRequestNumber(), error);
                    return eventBusPort.publishEvent(
                            new ReviewFailedEvent(event.reviewInfo(), error.getMessage()));
                })
                .timeout(Duration.ofMinutes(10));
    }
}
