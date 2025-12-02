package com.code.integration.application.port.inbound;

import com.code.integration.domain.model.ReviewComment;
import reactor.core.publisher.Mono;

public interface CommentPostingService {

    Mono<Void> postComment(ReviewComment comment);
}
