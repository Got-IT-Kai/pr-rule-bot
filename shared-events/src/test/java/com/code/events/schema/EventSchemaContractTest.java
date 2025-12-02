package com.code.events.schema;

import com.code.events.context.ContextCollectedEvent;
import com.code.events.context.ContextCollectionStatus;
import com.code.events.integration.CommentPostingFailedEvent;
import com.code.events.review.ReviewCompletedEvent;
import com.code.events.review.ReviewFailedEvent;
import com.code.events.review.ReviewStartedEvent;
import com.code.events.webhook.PullRequestReceivedEvent;
import com.code.events.webhook.WebhookAction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EventSchemaContractTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final JsonSchemaFactory SCHEMA_FACTORY =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

    private static final Path SCHEMA_ROOT = Path.of("..", "event-schemas", "v1");

    @ParameterizedTest
    @MethodSource("eventProvider")
    void eventShouldConformToSchema(Object event, String schemaRelativePath) throws Exception {
        String json = OBJECT_MAPPER.writeValueAsString(event);

        Path schemaPath = SCHEMA_ROOT.resolve(schemaRelativePath);
        JsonSchema schema = SCHEMA_FACTORY.getSchema(OBJECT_MAPPER.readTree(Files.newInputStream(schemaPath)));

        Set<ValidationMessage> errors = schema.validate(OBJECT_MAPPER.readTree(json));
        assertTrue(errors.isEmpty(), () -> "Schema validation failed for " + schemaRelativePath + ": " + errors);
    }

    static Stream<Arguments> eventProvider() {
        return Stream.of(
                Arguments.of(createPullRequestReceivedEvent(), Path.of("webhook", "pull-request-received.json").toString()),
                Arguments.of(createContextCollectedEvent(), Path.of("context", "context-collected.json").toString()),
                Arguments.of(createReviewStartedEvent(), Path.of("review", "review-started.json").toString()),
                Arguments.of(createReviewCompletedEvent(), Path.of("review", "review-completed.json").toString()),
                Arguments.of(createReviewFailedEvent(), Path.of("review", "review-failed.json").toString()),
                Arguments.of(createCommentPostingFailedEvent(), Path.of("integration", "comment-posting-failed.json").toString())
        );
    }

    private static PullRequestReceivedEvent createPullRequestReceivedEvent() {
        return new PullRequestReceivedEvent(
                uuid(),
                "owner",
                "repo",
                1,
                WebhookAction.OPENED,
                "PR title",
                "author",
                "a".repeat(40),
                Instant.now(),
                uuid(),
                "github",
                "install-123"
        );
    }

    private static ContextCollectedEvent createContextCollectedEvent() {
        return new ContextCollectedEvent(
                uuid(),
                uuid(),
                "owner",
                "repo",
                1,
                "PR title",
                "diff --git a/file b/file\n@@ -1 +1 @@\n-foo\n+bar",
                ContextCollectionStatus.COMPLETED,
                uuid(),
                Instant.now()
        );
    }

    private static ReviewStartedEvent createReviewStartedEvent() {
        return new ReviewStartedEvent(
                uuid(),
                uuid(),
                uuid(),
                "owner",
                "repo",
                1,
                uuid(),
                Instant.now()
        );
    }

    private static ReviewCompletedEvent createReviewCompletedEvent() {
        return new ReviewCompletedEvent(
                uuid(),
                uuid(),
                uuid(),
                "owner",
                "repo",
                1,
                "## Review\n- Looks good.",
                "ollama",
                "qwen2.5",
                uuid(),
                Instant.now()
        );
    }

    private static ReviewFailedEvent createReviewFailedEvent() {
        return new ReviewFailedEvent(
                uuid(),
                uuid(),
                uuid(),
                "owner",
                "repo",
                1,
                "LLM call failed",
                uuid(),
                Instant.now()
        );
    }

    private static CommentPostingFailedEvent createCommentPostingFailedEvent() {
        return new CommentPostingFailedEvent(
                uuid(),
                uuid(),
                "owner",
                "repo",
                1,
                "GitHub API 500",
                "WebClientResponseException",
                uuid(),
                Instant.now()
        );
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }
}
