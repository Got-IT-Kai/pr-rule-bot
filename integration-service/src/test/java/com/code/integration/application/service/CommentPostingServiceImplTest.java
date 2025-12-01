package com.code.integration.application.service;

import com.code.integration.application.port.outbound.GitHubCommentClient;
import com.code.integration.domain.model.ReviewComment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentPostingServiceImplTest {

    @Mock
    private GitHubCommentClient gitHubCommentClient;

    private CommentPostingServiceImpl commentPostingService;

    @BeforeEach
    void setUp() {
        commentPostingService = new CommentPostingServiceImpl(gitHubCommentClient);
    }

    @Test
    void shouldPostCommentSuccessfully() {
        // Given
        ReviewComment comment = new ReviewComment(
                "owner",
                "repo",
                123,
                "Review completed successfully",
                null
        );

        when(gitHubCommentClient.postComment(any(ReviewComment.class)))
                .thenReturn(Mono.just(456L));

        // When & Then
        StepVerifier.create(commentPostingService.postComment(comment))
                .verifyComplete();

        verify(gitHubCommentClient).postComment(comment);
    }

    @Test
    void shouldHandleErrorWhenPostingComment() {
        // Given
        ReviewComment comment = new ReviewComment(
                "owner",
                "repo",
                123,
                "Review completed successfully",
                null
        );

        when(gitHubCommentClient.postComment(any(ReviewComment.class)))
                .thenReturn(Mono.error(new RuntimeException("GitHub API error")));

        // When & Then
        StepVerifier.create(commentPostingService.postComment(comment))
                .expectError(RuntimeException.class)
                .verify();

        verify(gitHubCommentClient).postComment(comment);
    }
}
