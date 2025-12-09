package com.code.context.infrastructure.adapter.outbound.github;

import com.code.context.domain.exception.InvalidDiffException;
import com.code.context.domain.validator.DiffValidator;
import com.code.context.domain.validator.ValidationReason;
import com.code.context.domain.validator.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.retry.Retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GitHubClientAdapter")
class GitHubClientAdapterTest {

    @Mock
    WebClient webClient;

    @Mock
    WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    WebClient.ResponseSpec responseSpec;

    @Mock
    DiffValidator diffValidator;

    GitHubClientAdapter adapter;

    static final String DIFF_URL = "https://api.github.com/repos/owner/repo/pulls/123";
    static final String DIFF = "diff --git a/test.java b/test.java";
    static final String OWNER = "owner";
    static final String REPO = "repo";
    static final Integer PR_NUMBER = 123;
    static final String METADATA = "[{\"filename\":\"test.java\"}]";

    @BeforeEach
    void setUp() {
        Retry retryStrategy = Retry.max(0)
                .filter(throwable -> !(throwable instanceof InvalidDiffException));
        adapter = new GitHubClientAdapter(webClient, retryStrategy, diffValidator);
    }

    @Nested
    @DisplayName("when getting diff")
    class WhenGettingDiff {

        @BeforeEach
        void setUp() {
            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.accept(any(MediaType.class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        }

        @Test
        @DisplayName("should return diff successfully when validation is VALID")
        void shouldReturnDiffSuccessfully() {
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(DIFF));
            when(diffValidator.validate(DIFF)).thenReturn(ValidationResult.valid());

            StepVerifier.create(adapter.getDiff(DIFF_URL))
                    .assertNext(diff -> assertThat(diff).isEqualTo(DIFF))
                    .verifyComplete();

            verify(webClient).get();
            verify(requestHeadersUriSpec).uri(DIFF_URL);
            verify(diffValidator).validate(DIFF);
        }

        @Test
        @DisplayName("should return empty Mono when validation is SKIP")
        void shouldSkipWhenBinaryFile() {
            String binaryDiff = "diff --git a/image.png b/image.png\nBinary files differ";
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(binaryDiff));
            when(diffValidator.validate(binaryDiff))
                    .thenReturn(ValidationResult.skip(ValidationReason.BINARY_FILE));

            StepVerifier.create(adapter.getDiff(DIFF_URL))
                    .verifyComplete();

            verify(diffValidator).validate(binaryDiff);
        }

        @Test
        @DisplayName("should throw InvalidDiffException when validation is INVALID")
        void shouldThrowWhenInvalidDiff() {
            String jsonResponse = "{\"message\": \"Not Found\"}";
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(jsonResponse));
            when(diffValidator.validate(jsonResponse))
                    .thenReturn(ValidationResult.invalid(ValidationReason.JSON_RESPONSE));

            StepVerifier.create(adapter.getDiff(DIFF_URL))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(InvalidDiffException.class);
                        InvalidDiffException ex = (InvalidDiffException) error;
                        assertThat(ex.getValidationResult().status())
                                .isEqualTo(ValidationResult.Status.INVALID);
                        assertThat(ex.getValidationResult().reason())
                                .isEqualTo(ValidationReason.JSON_RESPONSE);
                    })
                    .verify();

            verify(diffValidator).validate(jsonResponse);
        }

        @Test
        @DisplayName("should handle WebClient error")
        void shouldHandleError() {
            when(responseSpec.bodyToMono(String.class))
                    .thenReturn(Mono.error(new WebClientResponseException(500, "Internal Error", null, null, null)));

            StepVerifier.create(adapter.getDiff(DIFF_URL))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).hasRootCauseInstanceOf(WebClientResponseException.class);
                    })
                    .verify();
        }
    }

    @Nested
    @DisplayName("when getting file metadata")
    class WhenGettingFileMetadata {

        @BeforeEach
        void setUp() {
            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString(), any(), any(), any())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        }

        @Test
        @DisplayName("should return metadata successfully")
        void shouldReturnMetadataSuccessfully() {
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(METADATA));

            StepVerifier.create(adapter.getFileMetadata(OWNER, REPO, PR_NUMBER))
                    .assertNext(metadata -> assertThat(metadata).isEqualTo(METADATA))
                    .verifyComplete();

            verify(webClient).get();
            verify(requestHeadersUriSpec).uri(anyString(), eq(OWNER), eq(REPO), eq(PR_NUMBER));
        }

        @Test
        @DisplayName("should use correct URI template")
        void shouldUseCorrectUriTemplate() {
            when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(METADATA));

            StepVerifier.create(adapter.getFileMetadata(OWNER, REPO, PR_NUMBER))
                    .expectNext(METADATA)
                    .verifyComplete();

            verify(requestHeadersUriSpec).uri("/repos/{owner}/{repo}/pulls/{pull_number}/files",
                    OWNER, REPO, PR_NUMBER);
        }

        @Test
        @DisplayName("should handle error")
        void shouldHandleError() {
            when(responseSpec.bodyToMono(String.class))
                    .thenReturn(Mono.error(new WebClientResponseException(404, "Not Found", null, null, null)));

            StepVerifier.create(adapter.getFileMetadata(OWNER, REPO, PR_NUMBER))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).hasRootCauseInstanceOf(WebClientResponseException.class);
                    })
                    .verify();
        }
    }
}
