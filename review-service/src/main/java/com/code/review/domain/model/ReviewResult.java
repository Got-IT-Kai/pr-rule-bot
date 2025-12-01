package com.code.review.domain.model;

import java.time.Instant;

public record ReviewResult(
        String reviewId,
        String contextId,
        String repositoryOwner,
        String repositoryName,
        Integer pullRequestNumber,
        String reviewComment,
        ReviewStatus status,
        String aiProvider,
        String aiModel,
        String correlationId,
        Instant completedAt
) {
    public boolean isSuccessful() {
        return status == ReviewStatus.COMPLETED;
    }

    public boolean hasFailed() {
        return status == ReviewStatus.FAILED;
    }
}
