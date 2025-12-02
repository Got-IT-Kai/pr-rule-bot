package com.code.webhook.infrastructure.adapter.inbound.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubPullRequestEventDto(
        String action,
        Repository repository,
        Integer number,
        @JsonProperty("pull_request") PullRequest pullRequest,
        Installation installation
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Repository(
            Owner owner,
            String name
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Owner(String login) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PullRequest(
            String title,
            User user,
            Head head,
            @JsonProperty("html_url") String htmlUrl,
            @JsonProperty("diff_url") String diffUrl
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record User(String login) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Head(String sha) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Installation(Long id) {}
}
