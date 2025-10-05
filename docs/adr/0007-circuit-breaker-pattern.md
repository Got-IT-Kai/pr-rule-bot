# ADR-0007: Circuit Breaker Pattern for External Services

**Date:** 2025-10-05
**Status:** Deferred to v2.0

## Context

The application makes synchronous calls to external services without fault tolerance mechanisms:

- **GitHub API:** Fetching PR details, reviews, diffs, posting comments
- **AI Providers:** Gemini and Ollama for code analysis

Current problems:
- External service failures cascade to all requests
- System fails completely when dependencies are unavailable
- No automatic recovery when services return to health
- Slow responses from dependencies block all operations
- GitHub API rate limit exhaustion causes complete system failure
- AI provider downtime blocks all PR reviews indefinitely

Without circuit breakers, cascading failures propagate through the system. When GitHub API is slow or rate-limited, all webhook requests wait indefinitely. When AI providers fail, the system retries endlessly without recovery.

**Related Issue:** Code Review I-3 (Medium Priority)
**Components:** `GitHubClient`, `GeminiAiClient`, `OllamaAiClient`

## Decision

Implement circuit breaker pattern using Resilience4j for all external service calls.

**Key components:**
- Circuit breaker per external service (GitHub API, Gemini, Ollama)
- State machine: CLOSED → OPEN → HALF_OPEN
- Service-specific failure thresholds
- Fallback strategies for graceful degradation
- Metrics and monitoring for circuit state

**Circuit breaker behavior:**
- CLOSED: Normal operation, track failures
- OPEN: Fail fast without calling service
- HALF_OPEN: Limited trial requests to test recovery

## Consequences

### Positive

- Prevents cascading failures when external services fail
- Automatic recovery when services become healthy
- Fail-fast behavior reduces resource exhaustion
- Improved system resilience and availability
- Better observability of dependency health
- Reduced latency during downstream failures (fail fast vs timeout)

### Negative

- Adds Resilience4j dependency
- Increases configuration complexity
- Need to tune thresholds per service
- Fallback logic requires careful design
- False positives possible during temporary glitches

## Alternatives Considered

### Alternative 1: Manual retry logic

**Description:** Implement custom retry loops with exponential backoff

**Pros:**
- No external dependencies
- Full control over retry behavior

**Cons:**
- Does not prevent cascading failures
- No fail-fast mechanism
- Retry storms can worsen problems
- Requires custom implementation for each client

**Why rejected:** Retry logic alone does not provide circuit breaking functionality.

### Alternative 2: Netflix Hystrix

**Description:** Use Hystrix for circuit breaking

**Pros:**
- Battle-tested at scale
- Rich feature set

**Cons:**
- In maintenance mode (no active development)
- More complex than needed
- Heavier dependency

**Why rejected:** Resilience4j is the modern successor with active development.

### Alternative 3: Spring Cloud Circuit Breaker

**Description:** Use Spring Cloud abstraction layer

**Pros:**
- Vendor-neutral abstraction
- Easy to swap implementations

**Cons:**
- Additional abstraction layer overhead
- Requires Spring Cloud dependencies
- Overkill for single implementation choice

**Why rejected:** Direct Resilience4j integration is simpler for single-vendor use.

## Implementation

### Architecture

```
Request → Circuit Breaker Wrapper → External Service
              ↓ (if OPEN)
          Fallback Handler
```

### Service-specific configuration

**GitHub API Circuit Breaker:**
- Failure threshold: 50% (strict, API usually reliable)
- Slow call threshold: 2 seconds
- Wait duration in open state: 10 seconds
- Sliding window: 10 requests

**Gemini API Circuit Breaker:**
- Failure threshold: 60% (lenient for external service)
- Slow call threshold: 5 seconds
- Wait duration in open state: 30 seconds
- Sliding window: 10 requests

**Ollama API Circuit Breaker:**
- Failure threshold: 60% (lenient for self-hosted)
- Slow call threshold: 10 seconds
- Wait duration in open state: 20 seconds
- Sliding window: 10 requests

### Fallback strategies

**GitHub API failures:**
- Return cached PR data if available
- Return error with retry guidance
- Log for manual review

**AI Provider failures:**
- Try alternative provider (Gemini → Ollama or vice versa)
- Queue request for later processing
- Post comment indicating temporary unavailability

### Validation approach

Circuit breaker correctness verification:
1. Simulate service failures (return errors)
2. Verify circuit opens after threshold
3. Verify fail-fast during open state
4. Verify recovery attempt in half-open
5. Verify circuit closes after successful recovery

### Monitoring strategy

Track metrics:
- Circuit state per service (CLOSED/OPEN/HALF_OPEN)
- Failure rate per circuit
- Slow call rate per circuit
- State transition events
- Number of failed calls
- Number of rejected calls (when open)

Alert conditions:
- Circuit OPEN for more than 5 minutes
- Circuit state transitions more than 10 times per hour
- Sustained failure rate above 70%

## v1.0 Update: Defer to v2.0

**Date:** 2025-10-05
**Decision:** Defer circuit breaker implementation to v2.0

**Rationale:**
- **Personal project context:** Single user, failure tolerance less critical
- **Acceptable failure behavior:** If GitHub or AI API fails, request simply fails - no cascading impact
- **Overengineering for v1.0:** Circuit breaker adds complexity for limited benefit in single-user scenario
- **Simple retry sufficient:** WebClient built-in retry or Spring Retry is adequate for transient failures
- **No cascading failure risk:** Single instance, no downstream services to protect

**When circuit breaker becomes necessary:**
- Multiple service instances (prevents thundering herd)
- High-availability requirements (SLA commitments)
- Production multi-tenant deployment
- Actual cascading failure incidents observed

**v1.0 Approach:**
- Use WebClient built-in retry for transient failures
- Or add Spring Retry for simple exponential backoff
- Accept failures gracefully (log and alert)
- Manual intervention acceptable for personal project

**v2.0 Consideration:**
- If production deployment with availability requirements emerges
- If observing actual cascading failures
- Implement with Resilience4j per original ADR design
- Integrate with observability stack (ADR-0006)

**Alternative for v1.0 (if simple retry needed):**
```
Spring Retry with exponential backoff:
- Max 3 attempts
- Initial delay: 1s
- Multiplier: 2x
- Max delay: 10s
- No distributed state needed
```

## References

- [Resilience4j Documentation](https://resilience4j.readme.io/)
- [Circuit Breaker Pattern - Martin Fowler](https://martinfowler.com/bliki/CircuitBreaker.html)
- [Release It! - Michael Nygard](https://pragprog.com/titles/mnee2/release-it-second-edition/)
- [Spring Retry](https://github.com/spring-projects/spring-retry)
- Code Review Report: [2025-10-05 Comprehensive Review](../code-review/2025-10-05-comprehensive-review.md#i-3-missing-circuit-breaker-medium)
- Related: [ADR-0006: Observability Strategy](./0006-observability-strategy.md)
- Related: [v1.0 Release Plan](../release/v1.0-plan.md)
