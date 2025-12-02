package com.code.context.application.service;

import com.code.context.application.port.inbound.ContextCollectionService;
import com.code.context.application.port.outbound.EventPublisher;
import com.code.context.application.port.outbound.GitHubClient;
import com.code.context.domain.model.CollectionStatus;
import com.code.context.domain.model.FileChange;
import com.code.context.domain.model.PullRequestContext;
import com.code.events.context.ContextCollectedEvent;
import com.code.events.context.ContextCollectionStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class ContextCollectionServiceImpl implements ContextCollectionService {

    private static final int MAX_DIFF_SIZE_BYTES = 512_000; // 500KB limit to stay under Kafka 1MB message limit

    private final GitHubClient gitHubClient;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<PullRequestContext> collect(
            String repositoryOwner,
            String repositoryName,
            Integer prNumber,
            String title,
            String diffUrl,
            String correlationId) {

        if (repositoryOwner == null || repositoryOwner.isBlank()) {
            return Mono.error(new IllegalArgumentException("repositoryOwner must not be blank"));
        }
        if (repositoryName == null || repositoryName.isBlank()) {
            return Mono.error(new IllegalArgumentException("repositoryName must not be blank"));
        }
        if (prNumber == null || prNumber <= 0) {
            return Mono.error(new IllegalArgumentException("prNumber must be positive"));
        }
        if (diffUrl == null || diffUrl.isBlank()) {
            return Mono.error(new IllegalArgumentException("diffUrl must not be blank"));
        }

        String contextId = UUID.randomUUID().toString();
        log.info("Starting context collection for PR #{} (contextId: {}, correlationId: {})",
                prNumber, contextId, correlationId);

        return gitHubClient.getDiff(diffUrl)
                .flatMap(diff -> {
                    int diffSizeBytes = diff.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;

                    // Check if diff exceeds Kafka message size limit
                    if (diffSizeBytes > MAX_DIFF_SIZE_BYTES) {
                        log.warn("Diff size ({} bytes) exceeds limit ({} bytes) for PR #{} (contextId: {})",
                                diffSizeBytes, MAX_DIFF_SIZE_BYTES, prNumber, contextId);

                        PullRequestContext skippedContext = new PullRequestContext(
                                contextId,
                                repositoryOwner,
                                repositoryName,
                                prNumber,
                                title,
                                diffUrl,
                                null,
                                List.of(),
                                createSkipMetadata("Diff size (%d bytes) exceeds limit (%d bytes)"
                                        .formatted(diffSizeBytes, MAX_DIFF_SIZE_BYTES)),
                                CollectionStatus.SKIPPED,
                                correlationId,
                                Instant.now()
                        );
                        return publishEvent(skippedContext).thenReturn(skippedContext);
                    }

                    // VALID: diff exists and within size limit, proceed with normal flow
                    return gitHubClient.getFileMetadata(repositoryOwner, repositoryName, prNumber)
                            .defaultIfEmpty("[]")
                            .map(metadata -> {
                                List<FileChange> files = parseFileChanges(metadata);
                                return new PullRequestContext(
                                        contextId,
                                        repositoryOwner,
                                        repositoryName,
                                        prNumber,
                                        title,
                                        diffUrl,
                                        diff,
                                        files,
                                        metadata,
                                        CollectionStatus.COMPLETED,
                                        correlationId,
                                        Instant.now()
                                );
                            })
                            .flatMap(context -> publishEvent(context).thenReturn(context));
                })
                // SKIP: diff is empty (validation returned Mono.empty())
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("Diff skipped for PR #{} (contextId: {}), creating skipped context",
                            prNumber, contextId);
                    PullRequestContext skippedContext = new PullRequestContext(
                            contextId,
                            repositoryOwner,
                            repositoryName,
                            prNumber,
                            title,
                            diffUrl,
                            null,
                            List.of(),
                            createSkipMetadata("Diff validation determined review not needed"),
                            CollectionStatus.SKIPPED,
                            correlationId,
                            Instant.now()
                    );
                    return publishEvent(skippedContext).thenReturn(skippedContext);
                }))
                .doOnSuccess(ctx -> log.info("Context collection completed for PR #{} (contextId: {}, status: {})",
                        prNumber, ctx.contextId(), ctx.status()))
                .doOnError(err -> log.error("Context collection failed for PR #{} (contextId: {})",
                        prNumber, contextId, err))
                .onErrorResume(err -> {
                    PullRequestContext failedContext = new PullRequestContext(
                            contextId,
                            repositoryOwner,
                            repositoryName,
                            prNumber,
                            title,
                            diffUrl,
                            null,
                            List.of(),
                            createErrorMetadata(err),
                            CollectionStatus.FAILED,
                            correlationId,
                            Instant.now()
                    );
                    return publishEvent(failedContext)
                            .thenReturn(failedContext)
                            .onErrorResume(publishErr -> {
                                log.error("Failed to publish FAILED event for PR #{}", prNumber, publishErr);
                                return Mono.just(failedContext);
                            });
                });
    }

    private String createSkipMetadata(String reason) {
        try {
            return objectMapper.writeValueAsString(Map.of("skip_reason", reason));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize skip metadata", e);
            return "{\"skip_reason\": \"serialization_failed\"}";
        }
    }

    private String createErrorMetadata(Throwable err) {
        try {
            String errorMessage = err.getMessage() != null ? err.getMessage() : err.getClass().getSimpleName();
            return objectMapper.writeValueAsString(Map.of("error", errorMessage));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize error metadata", e);
            return "{\"error\": \"serialization_failed\"}";
        }
    }

    private List<FileChange> parseFileChanges(String metadata) {
        try {
            JsonNode root = objectMapper.readTree(metadata);
            List<FileChange> changes = new ArrayList<>();

            if (root.isArray()) {
                for (JsonNode node : root) {
                    changes.add(new FileChange(
                            node.path("filename").asText(),
                            node.path("status").asText(),
                            node.path("additions").asInt(),
                            node.path("deletions").asInt(),
                            node.path("patch").asText(null)
                    ));
                }
            }

            return changes;
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse file metadata", e);
            return List.of();
        }
    }

    private Mono<Void> publishEvent(PullRequestContext context) {
        ContextCollectedEvent event = new ContextCollectedEvent(
                UUID.randomUUID().toString(),
                context.contextId(),
                context.repositoryOwner(),
                context.repositoryName(),
                context.pullRequestNumber(),
                context.title(),
                context.diff(),
                mapStatus(context.status()),
                context.correlationId(),
                Instant.now()
        );

        return eventPublisher.publish(event);
    }

    private ContextCollectionStatus mapStatus(CollectionStatus status) {
        return ContextCollectionStatus.valueOf(status.name());
    }
}
