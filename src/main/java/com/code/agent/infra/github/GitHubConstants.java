package com.code.agent.infra.github;

/**
 * Constants for GitHub API, webhook handling, and integration.
 */
public final class GitHubConstants {

    private GitHubConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ========== API Headers ==========
    public static final String API_ACCEPT_HEADER = "application/vnd.github+json";
    public static final String API_VERSION_HEADER = "X-GitHub-Api-Version";
    public static final String API_VERSION = "2022-11-28";
    public static final String DIFF_ACCEPT_HEADER = "application/vnd.github.v3.diff";
    public static final String AUTHORIZATION_PREFIX = "Bearer ";

    // ========== Webhook Headers ==========
    public static final String WEBHOOK_SIGNATURE_HEADER = "X-Hub-Signature-256";
    public static final String WEBHOOK_EVENT_HEADER = "X-GitHub-Event";
    public static final String WEBHOOK_DELIVERY_HEADER = "X-GitHub-Delivery";

    // ========== Webhook Signature Verification ==========
    public static final String SIGNATURE_PREFIX = "sha256=";
    public static final String HMAC_ALGORITHM = "HmacSHA256";

    // ========== Error Messages ==========
    public static final String ERROR_TOKEN_REQUIRED = "GitHub token is required but not configured";

    // ========== Log Messages - Webhook ==========
    public static final String LOG_VALIDATION_FAILED_NULL = "Webhook signature validation failed: null parameter detected";
    public static final String LOG_VALIDATION_FAILED_FORMAT = "Webhook signature validation failed: invalid signature format (missing '{}' prefix)";
    public static final String LOG_VALIDATION_FAILED_CRYPTO = "Failed to validate webhook signature due to cryptographic error";
    public static final String LOG_INVALID_SIGNATURE = "Invalid webhook signature from IP: {}";
    public static final String LOG_PARSE_ERROR = "Failed to parse GitHub webhook payload";
    public static final String LOG_EVENT_RECEIVED = "Received verified GitHub pull request event: {}";
}
