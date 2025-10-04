package com.code.agent.cli;

import com.code.agent.application.port.out.AiPort;
import com.code.agent.config.CliProperties;
import com.code.agent.domain.model.Repository;
import com.code.agent.infra.github.service.GitHubReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewCli")
class ReviewCliTest {

    @Mock
    private AiPort aiPort;

    @Mock
    private GitHubReviewService gitHubReviewService;

    @Mock
    private ConfigurableApplicationContext applicationContext;

    private ReviewCli reviewCli;
    private ApplicationArguments args;

    private static final String OWNER = "test-owner";
    private static final String REPO = "test-repo";
    private static final int PR_NUMBER = 123;
    private static final String DIFF = "diff --git a/file.txt b/file.txt";
    private static final String REVIEW = "AI review content";
    private static final int TIMEOUT_MINUTES = 5;

    @BeforeEach
    void setUp() {
        Repository repository = new Repository(OWNER, REPO);
        CliProperties cliProperties = new CliProperties(repository, PR_NUMBER, TIMEOUT_MINUTES);
        reviewCli = new ReviewCli(aiPort, gitHubReviewService, cliProperties, applicationContext);
        args = new DefaultApplicationArguments(new String[0]);
    }

    @Nested
    @DisplayName("when review succeeds")
    class WhenReviewSucceeds {

        @Test
        @DisplayName("should complete full review process")
        void shouldCompleteFullReviewProcess() throws Exception {
            // given
            given(gitHubReviewService.hasExistingReview(OWNER, REPO, PR_NUMBER)).willReturn(Mono.just(false));
            given(gitHubReviewService.fetchUnifiedDiff(OWNER, REPO, PR_NUMBER)).willReturn(Mono.just(DIFF));
            given(aiPort.evaluateDiff(DIFF)).willReturn(Mono.just(REVIEW));
            given(gitHubReviewService.postReviewComment(OWNER, REPO, PR_NUMBER, REVIEW)).willReturn(Mono.empty());

            // when
            reviewCli.run(args);

            // then
            then(gitHubReviewService).should(times(1)).hasExistingReview(OWNER, REPO, PR_NUMBER);
            then(gitHubReviewService).should(times(1)).fetchUnifiedDiff(OWNER, REPO, PR_NUMBER);
            then(aiPort).should(times(1)).evaluateDiff(DIFF);
            then(gitHubReviewService).should(times(1)).postReviewComment(OWNER, REPO, PR_NUMBER, REVIEW);
        }

        @Test
        @DisplayName("should skip when review already exists")
        void shouldSkipWhenReviewAlreadyExists() throws Exception {
            // given
            given(gitHubReviewService.hasExistingReview(OWNER, REPO, PR_NUMBER)).willReturn(Mono.just(true));

            // when
            reviewCli.run(args);

            // then
            then(gitHubReviewService).should(times(1)).hasExistingReview(OWNER, REPO, PR_NUMBER);
            then(gitHubReviewService).should(never()).fetchUnifiedDiff(OWNER, REPO, PR_NUMBER);
            then(aiPort).should(never()).evaluateDiff(DIFF);
            then(gitHubReviewService).should(never()).postReviewComment(OWNER, REPO, PR_NUMBER, REVIEW);
        }

        @Test
        @DisplayName("should skip when diff is empty")
        void shouldSkipWhenDiffIsEmpty() throws Exception {
            // given
            given(gitHubReviewService.hasExistingReview(OWNER, REPO, PR_NUMBER)).willReturn(Mono.just(false));
            given(gitHubReviewService.fetchUnifiedDiff(OWNER, REPO, PR_NUMBER)).willReturn(Mono.just(""));

            // when
            reviewCli.run(args);

            // then
            then(gitHubReviewService).should(times(1)).hasExistingReview(OWNER, REPO, PR_NUMBER);
            then(gitHubReviewService).should(times(1)).fetchUnifiedDiff(OWNER, REPO, PR_NUMBER);
            then(aiPort).should(never()).evaluateDiff(DIFF);
            then(gitHubReviewService).should(never()).postReviewComment(OWNER, REPO, PR_NUMBER, REVIEW);
        }

        @Test
        @DisplayName("should skip posting when AI returns empty review")
        void shouldSkipPostingWhenAiReturnsEmptyReview() throws Exception {
            // given
            given(gitHubReviewService.hasExistingReview(OWNER, REPO, PR_NUMBER)).willReturn(Mono.just(false));
            given(gitHubReviewService.fetchUnifiedDiff(OWNER, REPO, PR_NUMBER)).willReturn(Mono.just(DIFF));
            given(aiPort.evaluateDiff(DIFF)).willReturn(Mono.just(""));

            // when
            reviewCli.run(args);

            // then
            then(gitHubReviewService).should(times(1)).hasExistingReview(OWNER, REPO, PR_NUMBER);
            then(gitHubReviewService).should(times(1)).fetchUnifiedDiff(OWNER, REPO, PR_NUMBER);
            then(aiPort).should(times(1)).evaluateDiff(DIFF);
            then(gitHubReviewService).should(never()).postReviewComment(OWNER, REPO, PR_NUMBER, REVIEW);
        }
    }

    @Nested
    @DisplayName("when review fails")
    class WhenReviewFails {

        @Test
        @DisplayName("should handle error when fetching diff fails")
        void shouldHandleErrorWhenFetchingDiffFails() {
            // given
            RuntimeException error = new RuntimeException("Failed to fetch diff");
            given(gitHubReviewService.hasExistingReview(OWNER, REPO, PR_NUMBER)).willReturn(Mono.just(false));
            given(gitHubReviewService.fetchUnifiedDiff(OWNER, REPO, PR_NUMBER)).willReturn(Mono.error(error));

            // when & then
            assertThatThrownBy(() -> reviewCli.run(args))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Failed to fetch diff");

            then(gitHubReviewService).should(times(1)).hasExistingReview(OWNER, REPO, PR_NUMBER);
            then(gitHubReviewService).should(times(1)).fetchUnifiedDiff(OWNER, REPO, PR_NUMBER);
            then(aiPort).should(never()).evaluateDiff(DIFF);
        }

        @Test
        @DisplayName("should handle error when AI evaluation fails")
        void shouldHandleErrorWhenAiEvaluationFails() {
            // given
            RuntimeException error = new RuntimeException("AI service unavailable");
            given(gitHubReviewService.hasExistingReview(OWNER, REPO, PR_NUMBER)).willReturn(Mono.just(false));
            given(gitHubReviewService.fetchUnifiedDiff(OWNER, REPO, PR_NUMBER)).willReturn(Mono.just(DIFF));
            given(aiPort.evaluateDiff(DIFF)).willReturn(Mono.error(error));

            // when & then
            assertThatThrownBy(() -> reviewCli.run(args))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("AI service unavailable");

            then(gitHubReviewService).should(times(1)).hasExistingReview(OWNER, REPO, PR_NUMBER);
            then(gitHubReviewService).should(times(1)).fetchUnifiedDiff(OWNER, REPO, PR_NUMBER);
            then(aiPort).should(times(1)).evaluateDiff(DIFF);
            then(gitHubReviewService).should(never()).postReviewComment(OWNER, REPO, PR_NUMBER, REVIEW);
        }

        @Test
        @DisplayName("should handle error when posting review fails")
        void shouldHandleErrorWhenPostingReviewFails() {
            // given
            RuntimeException error = new RuntimeException("Failed to post comment");
            given(gitHubReviewService.hasExistingReview(OWNER, REPO, PR_NUMBER)).willReturn(Mono.just(false));
            given(gitHubReviewService.fetchUnifiedDiff(OWNER, REPO, PR_NUMBER)).willReturn(Mono.just(DIFF));
            given(aiPort.evaluateDiff(DIFF)).willReturn(Mono.just(REVIEW));
            given(gitHubReviewService.postReviewComment(OWNER, REPO, PR_NUMBER, REVIEW)).willReturn(Mono.error(error));

            // when & then
            assertThatThrownBy(() -> reviewCli.run(args))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Failed to post comment");

            then(gitHubReviewService).should(times(1)).hasExistingReview(OWNER, REPO, PR_NUMBER);
            then(gitHubReviewService).should(times(1)).fetchUnifiedDiff(OWNER, REPO, PR_NUMBER);
            then(aiPort).should(times(1)).evaluateDiff(DIFF);
            then(gitHubReviewService).should(times(1)).postReviewComment(OWNER, REPO, PR_NUMBER, REVIEW);
        }
    }
}