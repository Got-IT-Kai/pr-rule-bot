package com.code.events.review;

import java.time.Instant;

public record ReviewFailedEvent(
        String eventId,
        String reviewId,
        String contextId,
        String repositoryOwner,
        String repositoryName,
        Integer pullRequestNumber,
        String errorMessage,
        String correlationId,
        Instant timestamp
) {}
