# Architecture Decision Records (ADR)

This directory contains architectural decisions made for the PR Rule Bot project, following the ADR pattern to document important technical choices.

## What is an ADR?

An Architecture Decision Record (ADR) is a document that captures an important architectural decision made along with its context and consequences. ADRs help understand why certain technical choices were made and serve as a reference for future decisions.

## ADR Format

Each ADR follows this structure:

```markdown
# ADR-XXX: [Title]

**Date:** YYYY-MM-DD
**Status:** [Proposed | Accepted | Deprecated | Superseded]

## Context

What is the issue that we're seeing that is motivating this decision or change?

## Decision

What is the change that we're proposing and/or doing?

## Consequences

What becomes easier or more difficult to do because of this change?

### Positive
- Benefit 1
- Benefit 2

### Negative
- Trade-off 1
- Trade-off 2

## Alternatives Considered

What other options were evaluated?

### Alternative 1: [Name]
- Description
- Pros/Cons
- Why rejected

## Implementation

How will this decision be implemented?

## References

- Related ADRs
- External resources
- Related issues/PRs
```

## ADR Index

### Architecture & Design

**[ADR-0001: Non-Blocking I/O](./0001-non-blocking-io.md)** (Accepted)
- Use Spring WebFlux for reactive programming
- Full non-blocking architecture
- Status: Implemented

**[ADR-0002: Automate PR Reviews via GitHub Actions](./0002-automate-pr-reviews-via-github-actions.md)** (Accepted)
- Use GitHub Actions for CI/CD automation
- Switch from self-hosted to managed AI API
- Status: Implemented

### Infrastructure & Security

**[ADR-0003: Webhook Security](./0003-webhook-security.md)** (Proposed)
- Implement GitHub webhook signature verification
- Add security layer to prevent unauthorized API access
- Related to: Code Review CI-1

**[ADR-0004: Bot Identity Management](./0004-bot-identity-management.md)** (Proposed)
- Fix duplicate review detection
- Properly identify bot-generated reviews
- Related to: Code Review CI-3

**[ADR-0005: Rate Limiting Strategy](./0005-rate-limiting.md)** (Deferred to v2.0)
- Rate limiting deferred for personal project context
- Not needed for single-user v1.0 deployment
- Kafka provides backpressure in v2.0
- Will reconsider for multi-user/public deployment
- Related to: Code Review CI-2

### Observability & Operations

**[ADR-0006: Observability Strategy with OpenTelemetry](./0006-observability-strategy.md)** (Proposed)
- Environment-specific OTEL configuration
- Distributed tracing with Tempo/Jaeger
- Metrics with Prometheus and Grafana
- CI pipeline trace analysis
- Production monitoring infrastructure
- v1.0 Update: Remove OTEL, defer to v2.0

### Resilience & Scalability

**[ADR-0007: Circuit Breaker Pattern](./0007-circuit-breaker-pattern.md)** (Deferred to v2.0)
- Circuit breaker deferred for personal project context
- Not needed for single-user v1.0 deployment
- Simple retry sufficient for transient failures
- Will reconsider for high-availability requirements
- Related to: Code Review I-3

**[ADR-0008: Token Chunking Strategy](./0008-token-chunking-strategy.md)** (Proposed)
- Smart chunking for large files exceeding token limits
- Context-aware splitting at logical boundaries
- Support for any file size
- Related to: Code Review Section 6.5

## ADR Lifecycle

### Status Definitions

- **Proposed:** Decision under consideration
- **Accepted:** Decision approved and ready for implementation
- **Implemented:** Decision fully implemented in codebase
- **Deprecated:** Decision no longer relevant but kept for historical context
- **Superseded:** Replaced by a newer ADR

### When to Create an ADR

Use this decision tree to determine if an ADR is needed:

```
1. Are there multiple technical options to choose from?
   NO → ADR not needed, create task issue only
   YES → Continue to 2

2. Does each option have clear trade-offs?
   NO → ADR not needed, obvious choice
   YES → Continue to 3

3. Will this decision impact future architecture?
   YES → ADR needed
   NO → Task issue is sufficient
```

### ADR Required

Create an ADR when making architectural decisions with multiple viable options:

**Examples:**
- Technology selection (Resilience4j vs Hystrix vs custom)
- Design patterns (Circuit breaker vs retry logic vs queue)
- Data strategies (Chunking vs summarization vs rejection)
- Security approaches (HMAC vs API key vs IP whitelist)
- Storage decisions (Redis vs in-memory vs database)

**Key indicator:** "Why did I choose X over Y?" requires detailed explanation

### ADR Not Required

Skip ADR for implementation tasks with obvious solutions:

**Examples:**
- Test coverage improvements (just do it)
- JavaDoc additions (standard practice)
- Bug fixes (memory leaks, subscription issues)
- Code quality (extract magic numbers, remove wildcards)
- Security patches (prompt injection defense)

**Key indicator:** "This is the right thing to do" without alternatives

### Gray Area Examples

**Token Chunking:** ✅ ADR needed
- Multiple strategies: naive, smart, summarization, rejection
- Clear trade-offs: cost vs quality vs complexity
- Architectural impact: affects all large file processing

**JavaDoc Documentation:** ❌ ADR not needed
- Standard practice, no alternatives
- Style guide is separate documentation
- No architectural decision involved

**OTEL Configuration:** ✅ ADR update needed
- Decision to remove vs minimal implementation
- Trade-offs: observability vs complexity
- Update existing ADR-0006 with v1.0 decision

## Creating a New ADR

1. Verify ADR is needed (use decision tree above)
2. Copy the template from this document
3. Number sequentially (0001, 0002, etc.)
4. Fill in all sections
5. Create PR for review
6. Update this index
7. Link to related code reviews or issues

### Updating Existing ADRs

- ADRs are immutable once accepted
- To change a decision, create a new ADR that supersedes the old one
- Mark the old ADR as "Superseded by ADR-XXX"

## Decision Categories

### Security
- Authentication and authorization
- Secrets management
- API security
- Rate limiting

### Performance
- Scalability decisions
- Resource optimization
- Caching strategies

### Architecture
- Design patterns
- Service boundaries
- Data models

### Integration
- External services
- API contracts
- Event schemas

## Relationship with Code Reviews

ADRs are often created as a result of code review findings:

1. **Code Review identifies issue** → Document in code review report
2. **Propose solution** → Create ADR with alternatives
3. **Review and approve** → Accept ADR
4. **Implement** → Reference ADR in implementation
5. **Update status** → Mark ADR as implemented

## Quick Reference

| ADR | Title | Status | Priority | Complexity |
|-----|-------|--------|----------|------------|
| 0001 | Non-Blocking I/O | Accepted | - | High |
| 0002 | Automate PR Reviews | Accepted | - | Medium |
| 0003 | Webhook Security | Proposed | Critical | Medium |
| 0004 | Bot Identity Management | Proposed | High | Low |
| 0005 | Rate Limiting Strategy | Deferred | High | Medium |
| 0006 | Observability Strategy | Proposed | Critical | High |
| 0007 | Circuit Breaker Pattern | Deferred | High | Medium |
| 0008 | Token Chunking Strategy | Proposed | High | Medium |

## Best Practices

1. **Be Concise:** Focus on the decision, not implementation details
2. **Provide Context:** Explain the problem being solved
3. **Consider Alternatives:** Document why other options were rejected
4. **Track Consequences:** Both positive and negative impacts
5. **Reference Evidence:** Link to benchmarks, research, or discussions
6. **Keep Updated:** Mark status changes as decisions evolve

## ADR vs Implementation Guide

### ADR (Architecture Decision Record)
**Purpose:** Record the **what** and **why** of architectural decisions

**Contains:**
- Problem context and motivation
- Decision statement (what was chosen)
- Alternatives considered and why rejected
- Trade-offs (positive and negative consequences)
- High-level design concepts
- Validation strategy (how to verify it works)

**Does NOT contain:**
- Actual implementation code
- Step-by-step coding instructions
- Detailed API usage examples
- Configuration file contents

### Implementation Guide
**Purpose:** Explain **how** to implement the decision

**Contains:**
- Step-by-step implementation instructions
- Code examples and snippets
- Configuration file examples
- API usage patterns
- Integration points
- Testing procedures

**Location:** `/docs/implementation/` directory

### Example: Webhook Security

**ADR-0003 should contain:**
```
Decision: Use HMAC-SHA256 signature verification

Validation approach:
1. Extract X-Hub-Signature-256 header
2. Compute HMAC-SHA256(payload, secret)
3. Constant-time comparison
4. Reject on mismatch
```

**Implementation guide should contain:**
```java
// Actual code examples
public boolean validateSignature(String payload, String signature) {
    // Full implementation here
}

// Configuration examples
webhook:
  secret: ${GITHUB_WEBHOOK_SECRET}
```

## Writing ADRs: What to Include

### Minimal Code Examples

Use only when absolutely necessary for clarity:
- **Pseudocode** for algorithms (e.g., HMAC validation logic)
- **Conceptual examples** for design patterns (e.g., circuit breaker states)
- **Data structures** for key decisions (e.g., event schema)
- Keep it under 10 lines

### Design Over Code

Focus on:
- Component relationships and boundaries
- Data flow between components
- State transitions (for state machines)
- Integration points with external systems
- Configuration strategy (not actual values)

### Good ADR Example

```
Circuit Breaker States:
- CLOSED → Normal operation, track failures
- OPEN → Fail fast, return cached/fallback
- HALF_OPEN → Test recovery with limited requests

Threshold: 50% failure rate over 10 requests
```

### Bad ADR Example

```java
@Bean
public CircuitBreakerRegistry circuitBreakerRegistry() {
    CircuitBreakerConfig config = CircuitBreakerConfig.custom()
        .failureRateThreshold(50)
        .slidingWindowSize(10)
        .build();
    // 20+ more lines of Spring configuration
}
```

## Related Documentation

- [Code Review Reports](../code-review/README.md)
- [Lessons Learned](../lessons/README.md)
- [Contributing Guidelines](../../CONTRIBUTING.md)

## Contact

For questions about the ADR process or specific decisions:
- Create an issue with the `architecture` label
- Discuss in architecture review meetings
- Reference this documentation
