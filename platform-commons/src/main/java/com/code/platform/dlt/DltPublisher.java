package com.code.platform.dlt;

import com.code.platform.idempotency.IdempotencyStore;
import com.code.platform.metrics.MetricsHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class DltPublisher {

    private static final Duration DLT_TIMEOUT = Duration.ofSeconds(5);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final IdempotencyStore idempotencyStore;
    private final MetricsHelper metrics;

    public void forwardToDlt(String dltTopic, String eventId, Object event, Acknowledgment ack) {
        if (eventId == null) {
            log.error("Cannot forward to DLT with null eventId, dropping event");
            metrics.incrementCounter("dlt.publish", "status", "dropped-null-id", "topic", dltTopic);
            ack.acknowledge();
            return;
        }

        Mono.fromFuture(kafkaTemplate.send(dltTopic, eventId, event))
                .timeout(DLT_TIMEOUT)
                .doOnSuccess(result -> {
                    idempotencyStore.markProcessed(eventId);
                    ack.acknowledge();
                    metrics.incrementCounter("dlt.publish", "status", "success", "topic", dltTopic);
                })
                .doOnError(err -> {
                    log.error("DLT publish failed, dropping event: {}", err.getMessage());
                    metrics.incrementCounter("dlt.publish", "status", "failed", "topic", dltTopic);
                    ack.acknowledge();
                })
                .subscribe(
                        unused -> {},
                        error -> log.error("Unexpected error in DLT publish reactive chain: {}",
                                error.getMessage(), error)
                );
    }
}
