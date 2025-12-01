package com.code.events.integration;

import java.time.Instant;

public record CommentPostingFailedEvent(
        String eventId,
        String reviewId,
        String repositoryOwner,
        String repositoryName,
        Integer pullRequestNumber,
        String errorMessage,
        String errorType,
        String correlationId,
        Instant timestamp
) {}
