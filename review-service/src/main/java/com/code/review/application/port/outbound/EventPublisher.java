package com.code.review.application.port.outbound;

import com.code.events.review.ReviewCompletedEvent;
import com.code.events.review.ReviewFailedEvent;
import com.code.events.review.ReviewStartedEvent;
import reactor.core.publisher.Mono;

public interface EventPublisher {

    Mono<Void> publish(ReviewStartedEvent event);

    Mono<Void> publish(ReviewCompletedEvent event);

    Mono<Void> publish(ReviewFailedEvent event);
}