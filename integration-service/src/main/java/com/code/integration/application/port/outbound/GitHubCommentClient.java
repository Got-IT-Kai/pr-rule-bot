package com.code.integration.application.port.outbound;

import com.code.integration.domain.model.ReviewComment;
import reactor.core.publisher.Mono;

public interface GitHubCommentClient {

    Mono<Long> postComment(ReviewComment comment);
}
