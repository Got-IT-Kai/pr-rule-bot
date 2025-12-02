package com.code.context.application.service;

import com.code.context.application.port.outbound.EventPublisher;
import com.code.context.application.port.outbound.GitHubClient;
import com.code.context.domain.model.CollectionStatus;
import com.code.context.domain.model.PullRequestContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@DisplayName("ContextCollectionServiceImpl")
class ContextCollectionServiceImplTest {

    @Mock
    GitHubClient gitHubClient;

    @Mock
    EventPublisher eventPublisher;

    ObjectMapper objectMapper = new ObjectMapper();

    ContextCollectionServiceImpl service;

    static final String OWNER = "test-owner";
    static final String REPO = "test-repo";
    static final Integer PR_NUMBER = 123;
    static final String TITLE = "Test PR Title";
    static final String DIFF_URL = "https://api.github.com/repos/test-owner/test-repo/pulls/123";
    static final String CORRELATION_ID = "test-correlation-id";
    static final String DIFF = "diff --git a/test.java b/test.java";
    static final String METADATA = """
            [
              {
                "filename": "test.java",
                "status": "modified",
                "additions": 10,
                "deletions": 5,
                "patch": "@@ -1,5 +1,10 @@"
              }
            ]
            """;

    @BeforeEach
    void setUp() {
        service = new ContextCollectionServiceImpl(gitHubClient, eventPublisher, objectMapper);
    }

    @Nested
    @DisplayName("when collecting context successfully")
    class WhenCollectingSuccessfully {

        @BeforeEach
        void setUp() {
            when(gitHubClient.getDiff(DIFF_URL)).thenReturn(Mono.just(DIFF));
            when(gitHubClient.getFileMetadata(OWNER, REPO, PR_NUMBER)).thenReturn(Mono.just(METADATA));
            when(eventPublisher.publish(any())).thenReturn(Mono.empty());
        }

        @Test
        @DisplayName("should collect diff and metadata")
        void shouldCollectDiffAndMetadata() {
            StepVerifier.create(service.collect(OWNER, REPO, PR_NUMBER, TITLE, DIFF_URL, CORRELATION_ID))
                    .assertNext(context -> {
                        assertThat(context.repositoryOwner()).isEqualTo(OWNER);
                        assertThat(context.repositoryName()).isEqualTo(REPO);
                        assertThat(context.pullRequestNumber()).isEqualTo(PR_NUMBER);
                        assertThat(context.diffUrl()).isEqualTo(DIFF_URL);
                        assertThat(context.diff()).isEqualTo(DIFF);
                        assertThat(context.correlationId()).isEqualTo(CORRELATION_ID);
                    })
                    .verifyComplete();

            verify(gitHubClient).getDiff(DIFF_URL);
            verify(gitHubClient).getFileMetadata(OWNER, REPO, PR_NUMBER);
        }

        @Test
        @DisplayName("should parse file changes from metadata")
        void shouldParseFileChanges() {
            StepVerifier.create(service.collect(OWNER, REPO, PR_NUMBER, TITLE, DIFF_URL, CORRELATION_ID))
                    .assertNext(context -> {
                        assertThat(context.files()).hasSize(1);
                        assertThat(context.files().get(0).filename()).isEqualTo("test.java");
                        assertThat(context.files().get(0).status()).isEqualTo("modified");
                        assertThat(context.files().get(0).additions()).isEqualTo(10);
                        assertThat(context.files().get(0).deletions()).isEqualTo(5);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should set status to COMPLETED")
        void shouldSetStatusCompleted() {
            StepVerifier.create(service.collect(OWNER, REPO, PR_NUMBER, TITLE, DIFF_URL, CORRELATION_ID))
                    .assertNext(context -> {
                        assertThat(context.status()).isEqualTo(CollectionStatus.COMPLETED);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should generate context ID")
        void shouldGenerateContextId() {
            StepVerifier.create(service.collect(OWNER, REPO, PR_NUMBER, TITLE, DIFF_URL, CORRELATION_ID))
                    .assertNext(context -> {
                        assertThat(context.contextId()).isNotNull();
                        assertThat(context.contextId()).isNotEmpty();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should publish ContextCollected event")
        void shouldPublishEvent() {
            StepVerifier.create(service.collect(OWNER, REPO, PR_NUMBER, TITLE, DIFF_URL, CORRELATION_ID))
                    .expectNextCount(1)
                    .verifyComplete();

            verify(eventPublisher).publish(any());
        }
    }

    @Nested
    @DisplayName("when GitHub client fails")
    class WhenGitHubClientFails {

        @BeforeEach
        void setUp() {
            when(gitHubClient.getDiff(DIFF_URL)).thenReturn(Mono.error(new RuntimeException("GitHub error")));
            when(eventPublisher.publish(any())).thenReturn(Mono.empty());
        }

        @Test
        @DisplayName("should return failed context and publish FAILED event")
        void shouldReturnFailedContext() {
            StepVerifier.create(service.collect(OWNER, REPO, PR_NUMBER, TITLE, DIFF_URL, CORRELATION_ID))
                    .assertNext(context -> {
                        assertThat(context.status()).isEqualTo(CollectionStatus.FAILED);
                        assertThat(context.diff()).isNull();
                        assertThat(context.files()).isEmpty();
                        assertThat(context.metadata()).contains("error");
                    })
                    .verifyComplete();

            verify(eventPublisher).publish(any());
        }

        @Test
        @DisplayName("should handle event publishing failure gracefully")
        void shouldHandleEventPublishingFailure() {
            when(eventPublisher.publish(any())).thenReturn(Mono.error(new RuntimeException("Kafka error")));

            StepVerifier.create(service.collect(OWNER, REPO, PR_NUMBER, TITLE, DIFF_URL, CORRELATION_ID))
                    .assertNext(context -> {
                        assertThat(context.status()).isEqualTo(CollectionStatus.FAILED);
                        assertThat(context.diff()).isNull();
                        assertThat(context.files()).isEmpty();
                        assertThat(context.metadata()).contains("error");
                    })
                    .verifyComplete();

            verify(eventPublisher).publish(any());
        }
    }

    @Nested
    @DisplayName("when parsing invalid metadata")
    class WhenParsingInvalidMetadata {

        @BeforeEach
        void setUp() {
            when(gitHubClient.getDiff(DIFF_URL)).thenReturn(Mono.just(DIFF));
            when(gitHubClient.getFileMetadata(OWNER, REPO, PR_NUMBER))
                    .thenReturn(Mono.just("invalid json"));
            when(eventPublisher.publish(any())).thenReturn(Mono.empty());
        }

        @Test
        @DisplayName("should return empty file list")
        void shouldReturnEmptyFileList() {
            StepVerifier.create(service.collect(OWNER, REPO, PR_NUMBER, TITLE, DIFF_URL, CORRELATION_ID))
                    .assertNext(context -> {
                        assertThat(context.files()).isEmpty();
                        assertThat(context.status()).isEqualTo(CollectionStatus.COMPLETED);
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("when metadata is empty array")
    class WhenMetadataIsEmptyArray {

        @BeforeEach
        void setUp() {
            when(gitHubClient.getDiff(DIFF_URL)).thenReturn(Mono.just(DIFF));
            when(gitHubClient.getFileMetadata(OWNER, REPO, PR_NUMBER))
                    .thenReturn(Mono.just("[]"));
            when(eventPublisher.publish(any())).thenReturn(Mono.empty());
        }

        @Test
        @DisplayName("should return empty file list")
        void shouldReturnEmptyFileList() {
            StepVerifier.create(service.collect(OWNER, REPO, PR_NUMBER, TITLE, DIFF_URL, CORRELATION_ID))
                    .assertNext(context -> {
                        assertThat(context.files()).isEmpty();
                        assertThat(context.status()).isEqualTo(CollectionStatus.COMPLETED);
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("when diff exceeds size limit")
    class WhenDiffExceedsSizeLimit {

        @BeforeEach
        void setUp() {
            String largeDiff = "a".repeat(600_000);
            when(gitHubClient.getDiff(DIFF_URL)).thenReturn(Mono.just(largeDiff));
            when(eventPublisher.publish(any())).thenReturn(Mono.empty());
        }

        @Test
        @DisplayName("should return SKIPPED status with null diff")
        void shouldReturnSkippedStatus() {
            StepVerifier.create(service.collect(OWNER, REPO, PR_NUMBER, TITLE, DIFF_URL, CORRELATION_ID))
                    .assertNext(context -> {
                        assertThat(context.status()).isEqualTo(CollectionStatus.SKIPPED);
                        assertThat(context.diff()).isNull();
                        assertThat(context.files()).isEmpty();
                        assertThat(context.metadata()).contains("exceeds limit");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should publish ContextCollected event with SKIPPED status")
        void shouldPublishSkippedEvent() {
            StepVerifier.create(service.collect(OWNER, REPO, PR_NUMBER, TITLE, DIFF_URL, CORRELATION_ID))
                    .expectNextCount(1)
                    .verifyComplete();

            verify(eventPublisher).publish(any());
            verify(gitHubClient, never()).getFileMetadata(any(), any(), any());
        }
    }
}
