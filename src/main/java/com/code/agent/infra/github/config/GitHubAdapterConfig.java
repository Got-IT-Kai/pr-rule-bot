package com.code.agent.infra.github.config;

import com.code.agent.infra.github.adapter.GitHubAdapter;
import com.code.agent.infra.github.util.GitHubRetryUtil;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;

@Configuration
public class GitHubAdapterConfig {

    @Bean
    public GitHubAdapter gitHubAdapter(@Qualifier("gitHubWebClient") WebClient gitHubWebClient,
                                       GitHubProperties gitHubProperties) {
        Duration timeout = Duration.ofSeconds(10);
        Retry retryGet = Retry.fixedDelay(3, Duration.ofSeconds(2));
        Retry retryPost = Retry.fixedDelay(1, Duration.ofSeconds(2))
                .filter(GitHubRetryUtil::isRetryableError);

        return new GitHubAdapter(gitHubWebClient, gitHubProperties, timeout, retryGet, timeout, retryPost);

    }


}
