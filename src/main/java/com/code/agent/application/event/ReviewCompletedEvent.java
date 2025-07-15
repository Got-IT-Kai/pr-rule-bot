package com.code.agent.application.event;

import com.code.agent.domain.model.PullRequestReviewInfo;

public record ReviewCompletedEvent(
        PullRequestReviewInfo reviewInfo,
        String reviewResult) {
}
