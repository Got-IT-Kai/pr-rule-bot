package com.code.context.application.port.inbound;

import com.code.context.domain.model.PullRequestContext;
import reactor.core.publisher.Mono;

public interface ContextCollectionService {

    Mono<PullRequestContext> collect(
            String repositoryOwner,
            String repositoryName,
            Integer prNumber,
            String title,
            String diffUrl,
            String correlationId
    );
}
