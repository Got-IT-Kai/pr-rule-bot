package com.code.events.review;

import java.time.Instant;

public record ReviewCompletedEvent(
        String eventId,
        String reviewId,
        String contextId,
        String repositoryOwner,
        String repositoryName,
        Integer pullRequestNumber,
        String reviewMarkdown,
        String aiProvider,
        String aiModel,
        String correlationId,
        Instant timestamp
) {}
