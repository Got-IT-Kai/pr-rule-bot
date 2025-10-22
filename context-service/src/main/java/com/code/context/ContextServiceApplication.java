package com.code.context;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Context Service - Historical PR analysis and convention learning
 *
 * Responsibilities:
 * - Fetch historical PRs via GitHub GraphQL
 * - Parse Architecture Decision Records (ADRs)
 * - Learn and detect code conventions
 * - Provide context for AI review
 *
 * Architecture: Microservices (ADR-0015)
 * Technology: Spring Boot (I/O bound operations)
 */
@SpringBootApplication
public class ContextServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContextServiceApplication.class, args);
    }
}
