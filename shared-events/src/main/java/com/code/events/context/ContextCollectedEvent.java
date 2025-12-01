package com.code.events.context;

import java.time.Instant;

public record ContextCollectedEvent(
        String eventId,
        String contextId,
        String repositoryOwner,
        String repositoryName,
        Integer pullRequestNumber,
        String title,
        String diff,
        ContextCollectionStatus status,
        String correlationId,
        Instant timestamp
) {
}
