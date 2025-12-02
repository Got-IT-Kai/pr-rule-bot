package com.code.integration.application.service;

import com.code.integration.application.port.inbound.CommentPostingService;
import com.code.integration.application.port.outbound.GitHubCommentClient;
import com.code.integration.domain.model.ReviewComment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
public class CommentPostingServiceImpl implements CommentPostingService {

    private final GitHubCommentClient gitHubCommentClient;

    @Override
    public Mono<Void> postComment(ReviewComment comment) {
        log.info("Posting comment to PR #{} in {}/{}",
                comment.pullRequestNumber(),
                comment.repositoryOwner(),
                comment.repositoryName());

        return gitHubCommentClient.postComment(comment)
                .doOnSuccess(commentId ->
                        log.info("Successfully posted comment {} to PR #{} in {}/{}",
                                commentId,
                                comment.pullRequestNumber(),
                                comment.repositoryOwner(),
                                comment.repositoryName()))
                .doOnError(error ->
                        log.error("Failed to post comment to PR #{} in {}/{}",
                                comment.pullRequestNumber(),
                                comment.repositoryOwner(),
                                comment.repositoryName(),
                                error))
                .then();
    }
}
