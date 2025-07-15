package com.code.agent.infra.github.event;

public record GitHubReviewEvent(
        String body,
        String event
) {
    public static GitHubReviewEvent simpleReviewEvent(String comment) {
        return new GitHubReviewEvent(comment, "COMMENT");
    }
}
