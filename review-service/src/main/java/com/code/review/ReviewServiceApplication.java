package com.code.review;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Review Service - AI-powered code review orchestration
 *
 * Responsibilities:
 * - Aggregate context and policy findings
 * - Orchestrate AI review via Gemini API
 * - Parse and structure AI responses
 * - Generate SARIF output format
 * - Publish review results
 *
 * Architecture: Microservices (ADR-0015)
 * Technology: Spring Boot WebFlux (CPU/Memory intensive processing)
 */
@SpringBootApplication
public class ReviewServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReviewServiceApplication.class, args);
    }
}
