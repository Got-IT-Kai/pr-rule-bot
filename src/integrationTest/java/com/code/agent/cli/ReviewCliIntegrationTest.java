package com.code.agent.cli;

import com.code.agent.application.port.out.AiPort;
import com.code.agent.config.CliProperties;
import com.code.agent.domain.model.Repository;
import com.code.agent.infra.github.service.GitHubReviewService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

/**
 * Integration tests for {@link ReviewCli}.
 * Tests the complete CLI flow with MockWebServer for GitHub API.
 */
@DisplayName("ReviewCli Integration Tests")
class ReviewCliIntegrationTest {

    private MockWebServer mockWebServer;
    private ReviewCli reviewCli;
    private AiPort aiPort;

    private static final String OWNER = "test-owner";
    private static final String REPO = "test-repo";
    private static final int PR_NUMBER = 123;
    private static final String DIFF = "diff --git a/file.txt b/file.txt\n@@ -1 +1 @@\n-old\n+new";
    private static final String AI_REVIEW = "Code looks good!";

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .build();

        aiPort = mock(AiPort.class);
        GitHubReviewService gitHubReviewService = new GitHubReviewService(webClient);
        ConfigurableApplicationContext applicationContext = mock(ConfigurableApplicationContext.class);

        Repository repository = new Repository(OWNER, REPO);
        CliProperties cliProperties = new CliProperties(repository, PR_NUMBER, 1);

        reviewCli = new ReviewCli(aiPort, gitHubReviewService, cliProperties, applicationContext);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (mockWebServer != null) {
            mockWebServer.shutdown();
        }
    }

    @Nested
    @DisplayName("when review succeeds")
    class WhenReviewSucceeds {

        @Test
        @DisplayName("should complete full review flow with GitHub API")
        void shouldCompleteFullReviewFlowWithGitHubApi() throws Exception {
            // given
            given(aiPort.evaluateDiff(DIFF)).willReturn(Mono.just(AI_REVIEW));

            // Mock GitHub API responses
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .setBody("[]")); // No existing reviews

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, "application/vnd.github.v3.diff")
                    .setBody(DIFF));

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(201)
                    .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .setBody("{\"id\": 1}")); // Comment posted

            // when
            reviewCli.run(null);

            // then
            then(aiPort).should(times(1)).evaluateDiff(DIFF);
        }

        @Test
        @DisplayName("should skip when review already exists")
        void shouldSkipWhenReviewAlreadyExists() throws Exception {
            // given
            String existingReviewJson = """
                [
                  {
                    "id": 1,
                    "user": {"login": "bot"},
                    "body": "Previous review",
                    "state": "COMMENTED"
                  }
                ]
                """;

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .setBody(existingReviewJson));

            // when
            reviewCli.run(null);

            // then
            then(aiPort).should(times(0)).evaluateDiff(DIFF);
        }

        @Test
        @DisplayName("should skip when diff is empty")
        void shouldSkipWhenDiffIsEmpty() throws Exception {
            // given
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .setBody("[]")); // No existing reviews

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, "application/vnd.github.v3.diff")
                    .setBody("")); // Empty diff

            // when
            reviewCli.run(null);

            // then
            then(aiPort).should(times(0)).evaluateDiff(DIFF);
        }
    }

    @Nested
    @DisplayName("when review fails")
    class WhenReviewFails {

        @Test
        @DisplayName("should handle GitHub API errors when fetching diff")
        void shouldHandleGitHubApiErrorsWhenFetchingDiff() {
            // given
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .setBody("[]")); // No existing reviews

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(500)
                    .setBody("Internal Server Error")); // Error fetching diff

            // when & then
            assertThatThrownBy(() -> reviewCli.run(null))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("should handle AI service errors")
        void shouldHandleAiServiceErrors() {
            // given
            given(aiPort.evaluateDiff(DIFF)).willReturn(Mono.error(new RuntimeException("AI service down")));

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .setBody("[]")); // No existing reviews

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, "application/vnd.github.v3.diff")
                    .setBody(DIFF));

            // when & then
            assertThatThrownBy(() -> reviewCli.run(null))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("AI service down");
        }

        @Test
        @DisplayName("should handle GitHub API errors when posting comment")
        void shouldHandleGitHubApiErrorsWhenPostingComment() {
            // given
            given(aiPort.evaluateDiff(DIFF)).willReturn(Mono.just(AI_REVIEW));

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .setBody("[]")); // No existing reviews

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, "application/vnd.github.v3.diff")
                    .setBody(DIFF));

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(500)
                    .setBody("Internal Server Error")); // Error posting comment

            // when & then
            assertThatThrownBy(() -> reviewCli.run(null))
                    .isInstanceOf(Exception.class);
        }
    }
}
