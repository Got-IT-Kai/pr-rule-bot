package com.code.agent.infra.github.adapter;

import com.code.agent.domain.model.PullRequestReviewInfo;
import com.code.agent.application.port.out.GitHubPort;
import org.springframework.stereotype.Component;

@Component
public class GithubAdapter implements GitHubPort {

    @Override
    public String getDiff(PullRequestReviewInfo reviewInfo) {
        return "";
    }

    @Override
    public void postReviewComment(PullRequestReviewInfo reviewInfo, String comment) {

    }
}
