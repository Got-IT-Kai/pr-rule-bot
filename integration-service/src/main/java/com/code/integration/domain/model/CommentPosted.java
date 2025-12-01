package com.code.integration.domain.model;

import java.time.Instant;

public record CommentPosted(
        String eventId,
        String reviewId,
        String repositoryOwner,
        String repositoryName,
        Integer pullRequestNumber,
        Integer commentId,
        String correlationId,
        Instant timestamp
) {}
