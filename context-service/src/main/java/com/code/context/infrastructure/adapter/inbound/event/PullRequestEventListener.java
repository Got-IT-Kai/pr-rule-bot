package com.code.context.infrastructure.adapter.inbound.event;

import com.code.context.application.port.inbound.ContextCollectionService;
import com.code.context.infrastructure.config.KafkaTopicProperties;
import com.code.events.webhook.PullRequestReceivedEvent;
import com.code.platform.dlt.DltPublisher;
import com.code.platform.idempotency.IdempotencyStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PullRequestEventListener {

    private final ContextCollectionService contextCollectionService;
    private final IdempotencyStore idempotencyStore;
    private final DltPublisher dltPublisher;
    private final KafkaTopicProperties topicProperties;

    @KafkaListener(
        topics = "${kafka.topics.pull-request-received}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onPullRequestReceived(PullRequestReceivedEvent event, Acknowledgment ack) {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("correlationId", event.correlationId())) {
            log.info("Received PullRequestReceived event: eventId={}, repo={}/{}, PR #{}, platform={}, installationId={}",
                event.eventId(), event.repositoryOwner(), event.repositoryName(), event.pullRequestNumber(),
                event.platform(), event.installationId());

            if (!idempotencyStore.tryStart(event.eventId())) {
                log.info("Duplicate event detected: {}, skipping", event.eventId());
                ack.acknowledge();
                return;
            }

            if (!event.triggersReview()) {
                log.debug("Event does not trigger review, skipping: action={}", event.action());
                ack.acknowledge();
                return;
            }

            String diffUrl = String.format("/repos/%s/%s/pulls/%d",
                event.repositoryOwner(), event.repositoryName(), event.pullRequestNumber());

            log.debug("Starting context collection for PR #{}: diffUrl={}", event.pullRequestNumber(), diffUrl);

            contextCollectionService.collect(
                    event.repositoryOwner(),
                    event.repositoryName(),
                    event.pullRequestNumber(),
                    event.title(),
                    diffUrl,
                    event.correlationId()
            ).subscribe(
                    context -> {
                        log.info("Context collection completed for PR #{}", event.pullRequestNumber());
                        idempotencyStore.markProcessed(event.eventId());
                        ack.acknowledge();
                    },
                    error -> {
                        log.error("Context collection failed for PR #{}", event.pullRequestNumber(), error);
                        String dltTopic = topicProperties.pullRequestReceived() + ".dlt";
                        dltPublisher.forwardToDlt(dltTopic, event.eventId(), event, ack);
                    }
                );
        }
    }
}
