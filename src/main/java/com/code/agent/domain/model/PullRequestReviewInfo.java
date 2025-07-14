package com.code.agent.domain.model;

public record PullRequestReviewInfo(
        String repositoryOwner,
        String repositoryName,
        int pullRequestNumber,
        String diffUrl
) {}
