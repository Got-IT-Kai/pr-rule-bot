package com.code.agent.application.event;

import com.code.agent.domain.model.PullRequestReviewInfo;
import com.code.agent.domain.model.ReviewResult;

public record ReviewCompletedEvent(
        PullRequestReviewInfo reviewInfo,
        ReviewResult reviewResult) {
}
