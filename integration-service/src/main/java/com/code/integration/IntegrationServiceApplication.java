package com.code.integration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Integration Service - GitHub API integration
 *
 * Responsibilities:
 * - Create and update GitHub Check Runs
 * - Upload SARIF to GitHub Code Scanning
 * - Post review comments
 * - Handle action buttons
 * - Manage GitHub API rate limits
 *
 * Architecture: Microservices (ADR-0015)
 * Technology: Spring Boot WebFlux (I/O bound GitHub API calls)
 */
@SpringBootApplication
public class IntegrationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntegrationServiceApplication.class, args);
    }
}
