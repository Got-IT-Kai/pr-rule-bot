package com.code.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Event representing a Pull Request action from GitHub.
 * This event is published by webhook-service and consumed by other services.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PullRequestEvent {

    /**
     * Unique event ID
     */
    private String eventId;

    /**
     * Repository owner (e.g., "Got-IT-Kai")
     */
    private String repositoryOwner;

    /**
     * Repository name (e.g., "pr-rule-bot")
     */
    private String repositoryName;

    /**
     * Pull request number
     */
    private Integer pullRequestNumber;

    /**
     * Action that triggered the event (opened, synchronize, reopened, etc.)
     */
    private String action;

    /**
     * Pull request title
     */
    private String title;

    /**
     * Pull request author
     */
    private String author;

    /**
     * SHA of the commit
     */
    private String commitSha;

    /**
     * Timestamp when event was created
     */
    private Instant timestamp;
}
