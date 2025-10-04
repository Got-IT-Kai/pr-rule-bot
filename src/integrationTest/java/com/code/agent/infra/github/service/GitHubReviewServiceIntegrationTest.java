package com.code.agent.infra.github.service;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link GitHubReviewService}.
 * Tests actual HTTP communication using MockWebServer to simulate GitHub API responses.
 */
@DisplayName("GitHubReviewService Integration Tests")
class GitHubReviewServiceIntegrationTest {

    private MockWebServer mockWebServer;
    private GitHubReviewService gitHubReviewService;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .build();

        gitHubReviewService = new GitHubReviewService(webClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (mockWebServer != null) {
            mockWebServer.shutdown();
        }
    }

    @Test
    @DisplayName("should successfully detect existing reviews via HTTP")
    void shouldDetectExistingReviewsViaHttp() {
        // Given
        String reviewsJson = """
            [
              {
                "id": 80,
                "user": {
                  "login": "octocat",
                  "id": 1
                },
                "body": "Here is the body for the review.",
                "state": "APPROVED",
                "commit_id": "ecdd80bb57125d7ba9641ffaa4d7d2c19d3f3091"
              }
            ]
            """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody(reviewsJson));

        // When & Then
        StepVerifier.create(gitHubReviewService.hasExistingReview("owner", "repo", 1))
                .assertNext(hasReview -> assertThat(hasReview).isTrue())
                .verifyComplete();
    }

    @Test
    @DisplayName("should handle empty reviews array via HTTP")
    void shouldHandleEmptyReviewsArrayViaHttp() {
        // Given
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("[]"));

        // When & Then
        StepVerifier.create(gitHubReviewService.hasExistingReview("owner", "repo", 1))
                .assertNext(hasReview -> assertThat(hasReview).isFalse())
                .verifyComplete();
    }

    @Test
    @DisplayName("should handle server errors gracefully via HTTP")
    void shouldHandleServerErrorsGracefully() {
        // Given
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));

        // When & Then
        StepVerifier.create(gitHubReviewService.hasExistingReview("owner", "repo", 1))
                .assertNext(hasReview -> assertThat(hasReview).isFalse())
                .verifyComplete();
    }

    @Test
    @DisplayName("should handle 404 not found via HTTP")
    void shouldHandle404NotFound() {
        // Given
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(404)
                .setBody("Not Found"));

        // When & Then
        StepVerifier.create(gitHubReviewService.hasExistingReview("owner", "repo", 1))
                .assertNext(hasReview -> assertThat(hasReview).isFalse())
                .verifyComplete();
    }

    @Test
    @DisplayName("should handle malformed JSON gracefully")
    void shouldHandleMalformedJsonGracefully() {
        // Given
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{invalid json"));

        // When & Then
        StepVerifier.create(gitHubReviewService.hasExistingReview("owner", "repo", 1))
                .assertNext(hasReview -> assertThat(hasReview).isFalse())
                .verifyComplete();
    }
}
