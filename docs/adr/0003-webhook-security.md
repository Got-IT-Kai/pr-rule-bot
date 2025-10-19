# ADR-0003: Webhook Security Implementation

**Date:** 2025-10-06
**Status:** Implemented
**Implementation Date:** 2025-10-15

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

Implement GitHub webhook signature verification to authenticate all incoming webhook requests using HMAC-SHA256, with validation performed directly in the controller.

**Key components:**
1. `WebhookSignatureValidator` to verify HMAC-SHA256 signatures
2. Controller-level validation before processing webhook payloads
3. Environment-based secret management via `GitHubProperties`
4. Reject unauthorized requests with HTTP 401
5. Security audit logging

**Implementation approach:**
- Validate signatures directly in `GitHubWebhookController`
- Read raw request body using `ServerHttpRequest` and `DataBufferUtils` (WebFlux reactive streams)
- Use `@Component` validator for signature verification logic
- Apply validation before deserializing webhook payload

**Rationale for controller-based approach:**
- Simple and straightforward for single webhook endpoint
- Handles WebFlux reactive request body properly using `DataBufferUtils.join()`
- No need for `ContentCachingRequestWrapper` complexity
- Easier to test and maintain
- Sufficient for current requirements (avoiding over-engineering)

## Consequences

### Positive

- Prevents unauthorized webhook requests from triggering reviews
- Protects against API cost abuse and resource exhaustion
- Provides audit trail through failed authentication logs
- Aligns with GitHub security best practices
- Minimal performance impact (HMAC validation is fast)
- Simple implementation without filter complexity
- No request body stream consumption issues
- Easy to test and debug

### Negative

- Requires webhook secret configuration in all deployment environments
- Validation logic coupled to controller (not reusable if more webhook endpoints added)
- Must maintain secret synchronization between GitHub and application
- Rejected requests need investigation (legitimate vs malicious)
- Manual raw body handling required (bypassing Spring's automatic deserialization)

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

### Alternative 4: WebFilter-based Signature Verification

**Description:** Implement signature validation in a Spring WebFilter that intercepts all webhook requests

**Pros:**
- Centralized security logic
- Reusable across multiple webhook endpoints
- Separates security concerns from business logic

**Cons:**
- Request body can only be read once in reactive stack
- Requires `ContentCachingRequestWrapper` complexity
- Over-engineering for single webhook endpoint
- Filter ordering and configuration overhead
- Harder to test in isolation

**Why rejected:** Controller-based validation is simpler and sufficient for current single webhook endpoint. WebFilter introduces unnecessary complexity around request body handling. Can be revisited if multiple webhook endpoints are added.

## Implementation Strategy

### High-level approach

```
1. Generate shared secret
2. Configure secret in GitHub webhook settings
3. Store secret in application environment (GitHubProperties)
4. Implement HMAC-SHA256 verification utility (WebhookSignatureValidator)
5. Apply validation in webhook controller before processing
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
- Integration tests: Test controller behavior with valid/invalid signatures
- Security tests: Ensure constant-time comparison (timing attack prevention)
- End-to-end tests: Validate with actual GitHub webhook payloads

## Implementation Tasks

1. Add webhook-secret to GitHubProperties configuration
2. Update application.yml with webhook secret configuration
3. Implement WebhookSignatureValidator utility class
4. Modify GitHubWebhookController to validate signatures
5. Write comprehensive unit and integration tests
6. Update documentation and deployment guide

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
