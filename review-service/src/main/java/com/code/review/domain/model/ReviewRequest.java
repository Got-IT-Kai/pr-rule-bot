package com.code.review.domain.model;

import java.time.Instant;
import java.util.List;

public record ReviewRequest(
        String reviewId,
        String contextId,
        String repositoryOwner,
        String repositoryName,
        Integer pullRequestNumber,
        String prTitle,
        String diff,
        List<FileChange> files,
        String correlationId,
        Instant requestedAt
) {
    public boolean hasValidDiff() {
        return diff != null && !diff.isBlank();
    }

    public int fileCount() {
        return files != null ? files.size() : 0;
    }

    public PrContext prContext() {
        return PrContext.from(prTitle);
    }
}
