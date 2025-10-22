package com.code.policy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Policy Service - Rule-based policy evaluation
 *
 * Responsibilities:
 * - Evaluate policy rules against code changes
 * - Detect violations (security, quality, conventions)
 * - Generate policy findings
 * - Support custom and built-in policies
 *
 * Architecture: Microservices (ADR-0015)
 * Technology: Spring Boot (lightweight computation)
 */
@SpringBootApplication
public class PolicyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PolicyServiceApplication.class, args);
    }
}
