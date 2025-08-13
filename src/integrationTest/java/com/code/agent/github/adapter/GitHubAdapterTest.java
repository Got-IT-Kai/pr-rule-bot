package com.code.agent.github.adapter;

import com.code.agent.domain.model.PullRequestReviewInfo;
import com.code.agent.infra.config.GitHubProperties;
import com.code.agent.infra.github.adapter.GitHubAdapter;
import com.code.agent.infra.github.util.GitHubRetryUtil;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.test.StepVerifier;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@ExtendWith(MockitoExtension.class)
class GitHubAdapterTest {
    private ConnectionProvider connectionProvider;
    private MockWebServer mockWebServer;
    private GitHubAdapter gitHubAdapter;

    @BeforeEach
    void setMockWebServer() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        connectionProvider = ConnectionProvider.builder("test-" + System.nanoTime())
                .maxConnections(1)
                .disposeTimeout(Duration.ofMillis(100))
                .build();

        String baseUrl = String.format("http://localhost:%s", mockWebServer.getPort());
        WebClient testWebClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create(connectionProvider)))
                .build();

        GitHubProperties gitHubProperties = new GitHubProperties(
                baseUrl,
                "test-token",
                "/repos/{repositoryOwner}/{repositoryName}/pulls/{pullRequestNumber}/reviews"
        );

        gitHubAdapter = new GitHubAdapter(testWebClient, gitHubProperties,
                Duration.ofSeconds(1), Retry.fixedDelay(3, Duration.ofMillis(5)),
                Duration.ofSeconds(1), Retry.fixedDelay(1, Duration.ofMillis(5)).filter(GitHubRetryUtil::isRetryableError));
    }

    @AfterEach
    void downMockWebServer() throws IOException {
        if (mockWebServer != null) {
            mockWebServer.shutdown();
        }
        if (connectionProvider != null) {
            connectionProvider.disposeLater().block();
        }
    }

    @Nested
    @DisplayName("Get Diff Tests")
    class Get {
        @Test
        void getDiff_Success() throws InterruptedException {
            String diffUrl = mockWebServer.url("/custom-diff-path").toString();
            String expected = "diff";

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody(expected));

            Mono<String> mono = gitHubAdapter.getDiff(new PullRequestReviewInfo("owner", "repo", 1, diffUrl));
            StepVerifier.create(mono)
                    .expectNext(expected)
                    .verifyComplete();


            assertThat(mockWebServer.getRequestCount()).isEqualTo(1);

            mockWebServer.takeRequest(10, TimeUnit.MILLISECONDS);
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
                "503,503,503",
                "503,503,504",
                "503,504,503",
                "504,503,503",
                "504,504,503",
                "504,503,504",
                "504,504,504"
        })
        void getDiff_Retry(int status1, int status2, int status3) {
            String diffUrl = "/retry";
            String expected = "Retry test success";

            mockWebServer.enqueue(new MockResponse().setResponseCode(status1));
            mockWebServer.enqueue(new MockResponse().setResponseCode(status2));
            mockWebServer.enqueue(new MockResponse().setResponseCode(status3));
            mockWebServer.enqueue(new MockResponse().setResponseCode(200)
                    .setBodyDelay(100, TimeUnit.MILLISECONDS)
                    .setBody(expected));

            Mono<String> diff = gitHubAdapter.getDiff(new PullRequestReviewInfo("owner", "repo", 1, mockWebServer.url(diffUrl).toString()));
            StepVerifier.create(diff)
                    .expectNext(expected)
                    .expectComplete()
                    .verify(Duration.ofSeconds(1));

            assertThat(mockWebServer.getRequestCount()).isEqualTo(4);
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
                    .verifyComplete();


        }

        static Stream<Arguments> retryFailureProvider() {
            return Stream.of(
                    Arguments.of(400, 503, 504, 0),
                    Arguments.of(503, 400, 504, 1));
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

            assertThat(mockWebServer.getRequestCount()).isEqualTo(1 + retryCount);
        }
    }
}