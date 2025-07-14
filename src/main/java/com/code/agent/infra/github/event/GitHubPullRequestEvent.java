package com.code.agent.infra.github.event;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GitHubPullRequestEvent(
        Action action,
        Repository repository,
        Integer number,
        PullRequest pullRequest
) {
    public enum Action {
        @JsonProperty("opened")
        OPENED,
        @JsonProperty("closed")
        CLOSED,
        @JsonProperty("reopened")
        REOPENED,
        @JsonProperty("synchronize")
        SYNCHRONIZE,
        @JsonProperty("ready_for_review")
        READY_FOR_REVIEW
    }
    public record Repository(
            Owner owner,
            String name
    ) {
        public record Owner(String login) {}
    }

    public record PullRequest(
            String title,
            String htmlUrl,
            String diffUrl
    ) {}
    public boolean isReviewTriggered() {
        return action == Action.OPENED || action == Action.REOPENED || action == Action.SYNCHRONIZE || action == Action.READY_FOR_REVIEW;
    }
}
