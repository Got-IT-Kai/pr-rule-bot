# ADR-0005: Rate Limiting Strategy

**Date:** 2025-10-05
**Status:** Deferred to v2.0

## v1.0 Deferral Decision

**Reasoning:** This feature is deferred to v2.0 because it represents overengineering for the current single-user personal project context.

**Why not needed for v1.0:**
- Single-user deployment: No risk of multi-user abuse
- Manual monitoring: AI API costs can be monitored through GCP console
- GitHub rate limits: GitHub itself already rate-limits webhook events
- Simple retry: Backpressure from reactive streams provides natural throttling
- No public endpoint: Bot runs privately, not exposed to internet abuse

**When to reconsider (v2.0):**
- Multi-user/organization deployment
- Public webhook endpoint
- Unpredictable traffic patterns
- Need for cost enforcement policies

See original analysis below for future reference.

---

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

## v1.0 Update: Defer to v2.0

**Date:** 2025-10-05
**Decision:** Defer rate limiting implementation to v2.0

**Rationale:**
- **Personal project context:** Single user (individual developer), not multi-tenant
- **No cost abuse risk:** User has full control over repository activity
- **Overengineering for v1.0:** Redis-backed distributed rate limiting unnecessary for single instance
- **Kafka handles performance:** v2.0 Kafka migration provides natural backpressure for system protection
- **Rate limiting purpose reassessment:**
  - Performance protection → Kafka backpressure sufficient
  - Cost control → Only needed for multi-user/multi-tenant scenarios

**When rate limiting becomes necessary:**
- Open source public deployment (multiple users/repositories)
- Multi-tenant SaaS offering
- User-specific quota management required

**v1.0 Approach:**
- Rely on responsible usage (personal project)
- Monitor AI API costs manually
- No rate limiting implementation

**v2.0 Consideration:**
- If opening to public use: Implement per-user/per-repository quotas
- Use in-memory Bucket4j for single instance
- Add Redis only when scaling to multiple instances
- Integrate with Kafka consumer throttling

**Alternative for v1.0 (if needed):**
- Simple in-memory rate limiting with Bucket4j (no Redis)
- Webhook endpoint protection only
- No distributed state management

## References

- [Bucket4j Documentation](https://bucket4j.com/)
- [Token Bucket Algorithm](https://en.wikipedia.org/wiki/Token_bucket)
- Code Review Report: [2025-10-05 Comprehensive Review](../code-review/2025-10-05-comprehensive-review.md#ci-2-missing-rate-limiting-high)
- Related: [ADR-0003: Webhook Security](./0003-webhook-security.md)
- Related: [v1.0 Release Plan](../release/v1.0-plan.md)
