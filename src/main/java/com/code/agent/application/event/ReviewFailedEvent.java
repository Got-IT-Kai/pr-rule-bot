package com.code.agent.application.event;

import com.code.agent.domain.model.PullRequestReviewInfo;

public record ReviewFailedEvent(PullRequestReviewInfo reviewInfo, String message) {}
