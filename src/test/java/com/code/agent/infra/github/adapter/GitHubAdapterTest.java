package com.code.agent.infra.github.adapter;

import com.code.agent.domain.model.PullRequestReviewInfo;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;

@ExtendWith(MockitoExtension.class)
class GitHubAdapterTest {
    private static MockWebServer mockWebServer;
    private GitHubAdapter gitHubAdapter;

    @BeforeAll
    static void setUpAll() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterAll
    static void tearDownAll() throws IOException {
        if (mockWebServer != null) {
            mockWebServer.shutdown();
        }
    }

    @BeforeEach
    void setMockWebServer() {
        String baseUrl = String.format("http://localhost:%s", mockWebServer.getPort());
        WebClient testWebClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .build();

        gitHubAdapter = new GitHubAdapter(testWebClient);
    }

    @Test
    void getDiff_Success() throws InterruptedException {
        String diffUrl = mockWebServer.url("/custom-diff-path").toString();
        String expected = "diff";

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(expected));

        String actual = gitHubAdapter.getDiff(new PullRequestReviewInfo("owner", "repo", 1, diffUrl));
        assertThat(actual).isEqualTo(expected);

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getMethod()).isEqualTo("GET");
        assertThat(recordedRequest.getPath()).isEqualTo("/custom-diff-path");
    }

    @Test
    void postReviewComment_Success() throws InterruptedException {
        String expected = "comment";
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(expected));

       assertThatCode(() -> gitHubAdapter.postReviewComment(
               new PullRequestReviewInfo("owner",
                       "repo",
                       1,
                       "diffUrl"), "comment")
        ).doesNotThrowAnyException();

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getMethod()).isEqualTo("POST");
        assertThat(recordedRequest.getPath()).isEqualTo("/repos/owner/repo/pulls/1/reviews");
        assertThat(recordedRequest.getBody().readUtf8()).contains("comment");
    }



}