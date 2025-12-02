package com.code.integration.infrastructure.config;

import com.code.integration.application.port.inbound.CommentPostingService;
import com.code.integration.application.port.outbound.GitHubCommentClient;
import com.code.integration.application.service.CommentPostingServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IntegrationServiceConfiguration {

    @Bean
    public CommentPostingService commentPostingService(GitHubCommentClient gitHubCommentClient) {
        return new CommentPostingServiceImpl(gitHubCommentClient);
    }
}
