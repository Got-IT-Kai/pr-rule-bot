# ADR-0005: Rate Limiting Strategy

**Date:** 2025-10-05
**Status:** Proposed

## Context

The application currently has no rate limiting on webhook endpoints or AI API calls, creating multiple vulnerabilities:

- **DDoS attacks:** Unlimited requests can overwhelm the system
- **API cost abuse:** Each PR review triggers expensive AI API calls (Gemini)
- **Resource exhaustion:** Uncontrolled concurrent requests exhaust memory and CPU
- **Service degradation:** High load impacts all users

Without rate limiting, a malicious actor could create multiple PRs, repeatedly trigger webhooks, or force-push to cause unbounded AI API costs.

**Related Issue:** Code Review CI-2 (High)
**Component:** `GitHubWebhookController.java`, AI adapters

## Decision

Implement multi-layer rate limiting using Bucket4j with Redis-backed distributed rate limits.

**Strategy:**
- **HTTP layer:** Limit webhook requests per IP/repository (10 req/min)
- **Application layer:** Limit AI API calls per hour/day (30/hour, 100/day)
- **Algorithm:** Token bucket with refill
- **Response:** HTTP 429 with `Retry-After` header
- **Storage:** Redis for distributed rate limiting

**Rate limit tiers:**
- Free: 10 webhook/min, 30 AI calls/hour
- Authenticated: 30 webhook/min, 100 AI calls/hour

## Consequences

### Positive

- Prevents DDoS and resource exhaustion attacks
- Controls AI API costs to predictable levels ($30/month max)
- Protects system stability under high load
- Fair resource allocation among users
- Quota management and monitoring capability

### Negative

- Adds Redis dependency for distributed limits
- Legitimate high-volume users might hit limits
- Requires monitoring and tuning
- Slight latency increase (~1-2ms per request)
- Configuration complexity for different tiers

## Alternatives Considered

### Alternative 1: Spring Cloud Gateway Rate Limiter

**Pros:**
- Native Spring integration
- Redis-backed by default

**Cons:**
- Requires full Spring Cloud Gateway
- Overkill for simple rate limiting needs

**Why rejected:** Adds unnecessary dependency for simple rate limiting needs.

### Alternative 2: In-memory counters

**Pros:**
- No external dependencies
- Very low latency

**Cons:**
- Not distributed (fails with multiple instances)
- Lost on restart
- No persistence

**Why rejected:** Not suitable for production multi-instance deployments.

### Alternative 3: Nginx/Ingress rate limiting

**Pros:**
- Offloads work from application
- Very efficient

**Cons:**
- Requires infrastructure changes
- Only IP-based (less granular)
- No application-level context

**Why rejected:** Lacks application-specific intelligence needed for AI API cost control.

## Implementation Strategy

### Rate limiting layers

```
Layer 1: HTTP Webhook Endpoint
  → Bucket4j filter with Redis backend
  → Key: "webhook:repo:{owner}/{repo}" or "webhook:ip:{ip}"
  → Limit: 10 requests/minute (burst: 20)

Layer 2: AI API Calls
  → Decorator around AI client
  → Key: "ai:calls:hourly:{date-hour}" and "ai:calls:daily:{date}"
  → Limits: 30/hour, 100/day
```

### Configuration approach

```yaml
# application.yml
rate-limit:
  enabled: true
  webhook:
    requests-per-minute: 10
    burst-capacity: 20
  ai:
    calls-per-hour: 30
    calls-per-day: 100

spring:
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
```

### Testing strategy

- Unit tests: Verify bucket refill and consumption logic
- Integration tests: Test 429 responses after limit exceeded
- Load tests: Verify limits hold under high concurrency

## Future Considerations: Kafka Migration

This ADR addresses current HTTP webhook architecture. Post-Kafka migration:

**What stays:**
- HTTP webhook endpoint still needs rate limiting (ingress protection)

**What changes:**
- Kafka provides natural backpressure for event processing
- Consumer-side throttling replaces direct AI API limiting
- More efficient batching possible

**Migration path:**
- Phase 1 (current): HTTP webhook + AI API rate limiting
- Phase 2 (post-Kafka): Keep webhook rate limiting, remove AI limiting, use Kafka backpressure

## Monitoring

Track:
- Rate limit exceeded events (alert if > 100/hour)
- Remaining quota by key (dashboard)
- Rate limit hit ratio
- Redis connection health

## Cost Analysis

**Without rate limiting:** Unlimited AI calls → potential $1000+/month

**With rate limiting:** Max 100 calls/day × 30 days = 3000 calls/month → ~$30/month

**ROI:** Prevents DDoS + saves $970/month potential cost

## Implementation Tasks

1. Add Bucket4j and Redis dependencies
2. Implement webhook rate limiting filter
3. Implement AI API rate limiting decorator
4. Configure Redis connection and limits
5. Write unit and integration tests
6. Deploy to staging with conservative limits
7. Perform load testing and tune thresholds
8. Gradual production rollout with monitoring

## References

- [Bucket4j Documentation](https://bucket4j.com/)
- [Token Bucket Algorithm](https://en.wikipedia.org/wiki/Token_bucket)
- Code Review Report: [2025-10-05 Comprehensive Review](../code-review/2025-10-05-comprehensive-review.md#ci-2-missing-rate-limiting-high)
- Related: [ADR-0003: Webhook Security](./0003-webhook-security.md)
