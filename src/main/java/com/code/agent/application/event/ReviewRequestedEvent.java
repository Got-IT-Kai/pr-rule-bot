package com.code.agent.application.event;

import com.code.agent.domain.model.PullRequestReviewInfo;

public record ReviewRequestedEvent(
        PullRequestReviewInfo reviewInfo,
        String diff
) {}
