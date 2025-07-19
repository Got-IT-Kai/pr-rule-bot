package com.code.agent.application.port.out;

import com.code.agent.domain.model.PullRequestReviewInfo;
import reactor.core.publisher.Mono;

public interface GitHubPort {
    Mono<String> getDiff(PullRequestReviewInfo reviewInfo);
    Mono<Void> postReviewComment(PullRequestReviewInfo reviewInfo, String comment);
}
