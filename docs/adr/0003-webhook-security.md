# ADR-0003: Webhook Security Implementation

**Date:** 2025-10-05
**Status:** Proposed

## Context

The GitHub webhook endpoint at `/api/v1/webhooks/github/pull_request` currently has no authentication or signature verification. This creates a critical security vulnerability where anyone can send fake webhook payloads to trigger code reviews, potentially leading to:

- Unauthorized API access and abuse
- Excessive costs from AI provider (Gemini) API calls
- Resource exhaustion attacks
- Data manipulation through forged events

GitHub provides webhook security through HMAC-SHA256 signature verification using a shared secret. Every webhook request includes an `X-Hub-Signature-256` header containing a signature that can be validated against the payload and secret.

**Related Issue:** Code Review CI-1 (Critical)
**Component:** `GitHubWebhookController.java:20-35`

## Decision

Implement GitHub webhook signature verification to authenticate all incoming webhook requests using HMAC-SHA256.

**Key components:**
1. WebhookSecurityService to verify HMAC signatures
2. Spring WebFilter to intercept and validate webhook requests
3. Environment-based secret management
4. Reject unauthorized requests with HTTP 401
5. Security audit logging

## Consequences

### Positive

- Prevents unauthorized webhook requests from triggering reviews
- Protects against API cost abuse and resource exhaustion
- Provides audit trail through failed authentication logs
- Aligns with GitHub security best practices
- Minimal performance impact (HMAC validation is fast)

### Negative

- Requires webhook secret configuration in all deployment environments
- Adds filter to request processing pipeline
- Must maintain secret synchronization between GitHub and application
- Rejected requests need investigation (legitimate vs malicious)

## Alternatives Considered

### Alternative 1: IP Allowlist

**Description:** Restrict webhook endpoint to GitHub's public IP ranges

**Pros:**
- Network-level security
- No application code changes

**Cons:**
- GitHub IP ranges change frequently
- Infrastructure-level configuration complexity
- Weaker security than cryptographic verification
- No protection if IP is spoofed within allowed range

**Why rejected:** IP-based security is insufficient and harder to maintain than signature verification.

### Alternative 2: API Key Authentication

**Description:** Use custom API key in request header

**Pros:**
- Simple implementation
- Independent of GitHub

**Cons:**
- Not GitHub's standard
- Vulnerable to replay attacks
- Key rotation is manual
- Less secure than HMAC

**Why rejected:** Deviates from GitHub's security model and provides weaker guarantees.

### Alternative 3: OAuth App Authentication

**Description:** Use GitHub App or OAuth for webhook authentication

**Pros:**
- Token-based security
- Fine-grained permissions

**Cons:**
- Overly complex for webhooks
- OAuth flow not designed for this use case
- Higher latency

**Why rejected:** Signature verification is the standard and appropriate mechanism for webhooks.

## Implementation Strategy

### High-level approach

```
1. Generate shared secret
2. Configure secret in GitHub webhook settings
3. Store secret in application environment
4. Implement HMAC-SHA256 verification
5. Apply verification filter to webhook endpoints
6. Add security monitoring and alerts
```

### Signature verification algorithm

```
received_signature = request.header["X-Hub-Signature-256"]
payload = request.body
expected_signature = "sha256=" + HMAC-SHA256(secret, payload)

if constant_time_compare(received_signature, expected_signature):
    allow request
else:
    reject with 401
```

### Environment configuration

```yaml
# application.yml
github:
  webhook-secret: ${GITHUB_WEBHOOK_SECRET}
```

### Testing approach

- Unit tests: Verify HMAC calculation with known test vectors
- Integration tests: Test filter behavior with valid/invalid signatures
- Security tests: Ensure constant-time comparison (timing attack prevention)

## Implementation Tasks

1. Implement HMAC-SHA256 verification service
2. Create WebFilter for signature validation
3. Add configuration for webhook secret
4. Write comprehensive unit and integration tests
5. Deploy to staging with monitoring
6. Verify no false rejections in staging
7. Deploy to production with gradual rollout

## Monitoring

Track:
- Failed authentication attempts (alert if > 10/hour)
- Failed authentication by IP address
- Signature validation latency

## References

- [GitHub Webhook Security](https://docs.github.com/en/webhooks/using-webhooks/validating-webhook-deliveries)
- [HMAC-SHA256 Specification](https://datatracker.ietf.org/doc/html/rfc2104)
- Code Review Report: [2025-10-05 Comprehensive Review](../code-review/2025-10-05-comprehensive-review.md#ci-1-no-webhook-authentication-critical)
- Related: [ADR-0005: Rate Limiting](./0005-rate-limiting.md)
