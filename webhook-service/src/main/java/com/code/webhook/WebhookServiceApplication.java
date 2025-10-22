package com.code.webhook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Webhook Service - GitHub webhook receiver
 *
 * Responsibilities:
 * - Receive GitHub webhook events
 * - Verify HMAC-SHA256 signatures
 * - Publish events to Kafka for downstream processing
 * - Respond immediately (< 100ms) to prevent timeout
 *
 * Architecture: Microservices (ADR-0015)
 * Technology: Spring Boot WebFlux for reactive non-blocking I/O
 */
@SpringBootApplication
public class WebhookServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebhookServiceApplication.class, args);
    }
}
