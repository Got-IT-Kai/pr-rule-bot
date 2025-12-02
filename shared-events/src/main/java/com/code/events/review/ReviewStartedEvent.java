package com.code.events.review;

import java.time.Instant;

public record ReviewStartedEvent(
        String eventId,
        String reviewId,
        String contextId,
        String repositoryOwner,
        String repositoryName,
        Integer pullRequestNumber,
        String correlationId,
        Instant timestamp
) {}
