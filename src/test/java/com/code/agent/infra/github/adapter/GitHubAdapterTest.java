package com.code.agent.infra.github.adapter;

import com.code.agent.domain.model.PullRequestReviewInfo;
import com.code.agent.infra.config.GitHubProperties;
import com.code.agent.infra.github.util.GitHubRetryUtil;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@ExtendWith(MockitoExtension.class)
class GitHubAdapterTest {
    private static MockWebServer mockWebServer;
    private GitHubAdapter gitHubAdapter;

    @BeforeEach
    void setMockWebServer() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String baseUrl = String.format("http://localhost:%s", mockWebServer.getPort());
        WebClient testWebClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .build();

        GitHubProperties gitHubProperties = new GitHubProperties(
                baseUrl,
                "test-token",
                "/repos/{repositoryOwner}/{repositoryName}/pulls/{pullRequestNumber}/reviews"
        );

        gitHubAdapter = new GitHubAdapter(testWebClient, gitHubProperties,
                Duration.ofSeconds(1), Retry.backoff(3, Duration.ofMillis(5)).jitter(0.5),
                Duration.ofSeconds(1), Retry.backoff(3, Duration.ofMillis(5)).filter(GitHubRetryUtil::isRetryableError).jitter(0.5));
    }

    @AfterEach
    void downMockWebServer() throws IOException {
        if (mockWebServer != null) {
            mockWebServer.shutdown();
        }
    }

    @Nested
    @DisplayName("Get Diff Tests")
    class Get {
        @Test
        void getDiff_Success() {
            String diffUrl = mockWebServer.url("/custom-diff-path").toString();
            String expected = "diff";

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody(expected));

            Mono<String> mono = gitHubAdapter.getDiff(new PullRequestReviewInfo("owner", "repo", 1, diffUrl));
            StepVerifier.create(mono)
                    .expectNext(expected)
                    .then(() -> {
                        try {
                            RecordedRequest recordedRequest = mockWebServer.takeRequest();
                            assertThat(recordedRequest.getMethod()).isEqualTo("GET");
                            assertThat(recordedRequest.getPath()).isEqualTo("/custom-diff-path");
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .verifyComplete();


        }

        @Test
        void getDiff_TimeOut() {
            String diffUrl = "/timeout-diff";


            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody("Time out test")
                    .setBodyDelay(2, TimeUnit.SECONDS));

            Mono<String> mono = gitHubAdapter.getDiff(new PullRequestReviewInfo("owner", "repo", 1, mockWebServer.url(diffUrl).toString()));

            StepVerifier.create(mono)
                    .expectErrorMatches(Exceptions::isRetryExhausted)
                    .verify();

            assertThat(mockWebServer.getRequestCount()).isEqualTo(4);
        }

        @ParameterizedTest
        @CsvSource({
                "503,503",
                "503,504",
                "504,503",
                "504,504"
        })
        void getDiff_Retry(int status1, int status2) {
            String diffUrl = "/retry";
            String expected = "Retry test success";

            mockWebServer.enqueue(new MockResponse().setResponseCode(status1));
            mockWebServer.enqueue(new MockResponse().setResponseCode(status2));
            mockWebServer.enqueue(new MockResponse().setResponseCode(200)
                    .setBody(expected));

            Mono<String> diff = gitHubAdapter.getDiff(new PullRequestReviewInfo("owner", "repo", 1, mockWebServer.url(diffUrl).toString()));
            StepVerifier.create(diff)
                    .expectNext(expected)
                    .then(() -> {
                        try {
                            RecordedRequest recordedRequest = mockWebServer.takeRequest();
                            assertThat(recordedRequest.getMethod()).isEqualTo("GET");
                            assertThat(recordedRequest.getPath()).isEqualTo(diffUrl);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }})
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Post Review Comment Tests")
    class Post {
        @Test
        void postReviewComment_Success() {
            String expected = "comment";
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody(expected));

            Mono<Void> mono = gitHubAdapter.postReviewComment(
                    new PullRequestReviewInfo("owner",
                            "repo",
                            1,
                            "diffUrl"), "comment");

            StepVerifier.create(mono)
                    .then(() -> {
                        try {
                            RecordedRequest recordedRequest = mockWebServer.takeRequest();
                            assertThat(recordedRequest.getMethod()).isEqualTo("POST");
                            assertThat(recordedRequest.getPath()).isEqualTo("/repos/owner/repo/pulls/1/reviews");
                            assertThat(recordedRequest.getBody().readUtf8()).contains("comment");
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .verifyComplete();


        }

        static Stream<Arguments> retryFailureProvider() {
            return Stream.of(
                    Arguments.of(400, 503, 504, 1),
                    Arguments.of(503, 400, 504, 2),
                    Arguments.of(503, 503, 400, 3)
            );
        }

        @ParameterizedTest
        @MethodSource("retryFailureProvider")
        void post_RetryFailure(int status1, int status2, int status3, int retryCount) {
            String diffUrl = "/retry-failure";

            mockWebServer.enqueue(new MockResponse().setResponseCode(status1));
            mockWebServer.enqueue(new MockResponse().setResponseCode(status2));
            mockWebServer.enqueue(new MockResponse().setResponseCode(status3));

            Mono<Void> diff = gitHubAdapter.postReviewComment(new PullRequestReviewInfo("owner", "repo", 1, mockWebServer.url(diffUrl).toString()),
                    "retry failure test");
            StepVerifier.create(diff)
                    .expectErrorMatches(throwable -> throwable instanceof WebClientResponseException)
                    .verify();

            assertThat(mockWebServer.getRequestCount()).isEqualTo(retryCount);
        }
    }
}