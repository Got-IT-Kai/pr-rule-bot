package com.code.events.webhook;

import java.time.Instant;

public record PullRequestReceivedEvent(
        String eventId,
        String repositoryOwner,
        String repositoryName,
        Integer pullRequestNumber,
        WebhookAction action,
        String title,
        String author,
        String commitSha,
        Instant timestamp,
        String correlationId,
        String platform,
        String installationId
) {
    public boolean triggersReview() {
        return action.triggersReview();
    }
}
