package com.code.context.infrastructure.adapter.outbound.event;

import com.code.context.infrastructure.config.KafkaTopicProperties;
import com.code.events.context.ContextCollectedEvent;
import com.code.events.context.ContextCollectionStatus;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("KafkaEventPublisher")
class KafkaEventPublisherTest {

    @Mock
    KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    KafkaTopicProperties topicProperties;

    KafkaEventPublisher publisher;

    static final String TOPIC = "context-collected";
    static final String OWNER = "test-owner";
    static final String REPO = "test-repo";

    @BeforeEach
    void setUp() {
        publisher = new KafkaEventPublisher(kafkaTemplate, topicProperties);
        when(topicProperties.contextCollected()).thenReturn(TOPIC);
    }

    ContextCollectedEvent createEvent() {
        return new ContextCollectedEvent(
                "event-123",
                "context-456",
                OWNER,
                REPO,
                42,
                "Test PR",
                "diff content",
                ContextCollectionStatus.COMPLETED,
                "correlation-789",
                Instant.now()
        );
    }

    @Nested
    @DisplayName("when publishing event successfully")
    class WhenPublishingSuccessfully {

        @BeforeEach
        void setUp() {
            RecordMetadata metadata = new RecordMetadata(
                    new TopicPartition(TOPIC, 0), 0, 0, 0, 0, 0);
            SendResult<String, Object> sendResult = new SendResult<>(null, metadata);
            when(kafkaTemplate.send(eq(TOPIC), any(String.class), any()))
                    .thenReturn(CompletableFuture.completedFuture(sendResult));
        }

        @Test
        @DisplayName("should send event to correct topic with repository key")
        void shouldSendToCorrectTopic() {
            ContextCollectedEvent event = createEvent();
            String expectedKey = OWNER + "/" + REPO;

            StepVerifier.create(publisher.publish(event))
                    .verifyComplete();

            verify(kafkaTemplate).send(TOPIC, expectedKey, event);
        }
    }

    @Nested
    @DisplayName("when publishing fails")
    class WhenPublishingFails {

        @BeforeEach
        void setUp() {
            when(kafkaTemplate.send(eq(TOPIC), any(String.class), any()))
                    .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka error")));
        }

        @Test
        @DisplayName("should propagate error")
        void shouldPropagateError() {
            ContextCollectedEvent event = createEvent();

            StepVerifier.create(publisher.publish(event))
                    .verifyError(RuntimeException.class);
        }
    }
}
