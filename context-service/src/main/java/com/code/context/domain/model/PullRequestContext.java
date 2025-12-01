package com.code.context.domain.model;

import java.time.Instant;
import java.util.List;

public record PullRequestContext(
        String contextId,
        String repositoryOwner,
        String repositoryName,
        Integer pullRequestNumber,
        String title,
        String diffUrl,
        String diff,
        List<FileChange> files,
        String metadata,
        CollectionStatus status,
        String correlationId,
        Instant collectedAt
) {
    public boolean isReadyForReview() {
        return status == CollectionStatus.COMPLETED && diff != null;
    }
}
