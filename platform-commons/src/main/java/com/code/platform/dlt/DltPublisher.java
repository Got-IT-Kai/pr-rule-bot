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

    private static final int MAX_DLT_RETRIES = 3;
    private static final Duration DLT_TIMEOUT = Duration.ofSeconds(5);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final DltRetryCounter dltRetryCounter;
    private final IdempotencyStore idempotencyStore;
    private final MetricsHelper metrics;

    public void forwardToDlt(String dltTopic, String eventId, Object event, Acknowledgment ack) {
        if (eventId == null) {
            log.error("Cannot forward to DLT with null eventId, dropping event");
            metrics.incrementCounter("dlt.publish", "status", "dropped-null-id", "topic", dltTopic);
            ack.acknowledge();
            return;
        }

        int retryCount = dltRetryCounter.incrementAndGet(eventId);

        if (retryCount > MAX_DLT_RETRIES) {
            log.error("DLT publish failed {} times, dropping event: {}", MAX_DLT_RETRIES, eventId);
            metrics.incrementCounter("dlt.publish", "status", "dropped", "topic", dltTopic);
            idempotencyStore.markProcessed(eventId);
            ack.acknowledge();
            return;
        }

        Mono.fromFuture(kafkaTemplate.send(dltTopic, eventId, event))
                .timeout(DLT_TIMEOUT)
                .doOnSuccess(result -> {
                    dltRetryCounter.reset(eventId);
                    idempotencyStore.markProcessed(eventId);
                    ack.acknowledge();
                    metrics.incrementCounter("dlt.publish", "status", "success", "topic", dltTopic);
                })
                .doOnError(err -> {
                    log.error("DLT publish failed (attempt {}/{}): {}", retryCount, MAX_DLT_RETRIES, err.getMessage());
                    metrics.incrementCounter("dlt.publish", "status", "retry", "topic", dltTopic);
                    ack.nack(Duration.ofSeconds(5));
                })
                .subscribe(
                        unused -> {},
                        error -> log.error("Unexpected error in DLT publish reactive chain: {}",
                                error.getMessage(), error)
                );
    }
}
