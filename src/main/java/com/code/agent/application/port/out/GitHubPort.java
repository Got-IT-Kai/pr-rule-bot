package com.code.agent.application.port.out;

import com.code.agent.domain.model.PullRequestReviewInfo;

public interface GitHubPort {
    String getDiff(PullRequestReviewInfo reviewInfo);
    void postReviewComment(PullRequestReviewInfo reviewInfo, String comment);
}
