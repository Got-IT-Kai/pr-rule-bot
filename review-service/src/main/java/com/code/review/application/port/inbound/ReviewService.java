package com.code.review.application.port.inbound;

import com.code.review.domain.model.ReviewResult;
import reactor.core.publisher.Mono;

public interface ReviewService {

    Mono<ReviewResult> perform(
            String contextId,
            String repositoryOwner,
            String repositoryName,
            Integer pullRequestNumber,
            String prTitle,
            String diff,
            String correlationId
    );
}
