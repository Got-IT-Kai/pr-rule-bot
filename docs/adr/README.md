# Architecture Decision Records

Concise records of non-obvious decisions with trade-offs. Keep them short; code examples only when they clarify the decision.

## Index
- [ADR-0001](0001-non-blocking-io.md): WebFlux for webhook responsiveness
- [ADR-0003](0003-webhook-security.md): HMAC validation for GitHub webhooks
- [ADR-0015](0015-microservices-architecture.md): Split to async services with Kafka
- [ADR-0017](0017-compensation-pattern.md): Compensation (no retry) for GitHub comments
- [ADR-0018](0018-in-memory-idempotency.md): Caffeine-based idempotency for single-instance dev
- [ADR-0019](0019-stateless-github-source-of-truth.md): Stateless services, GitHub as source of truth
- [ADR-0020](0020-single-tenant-auth-posture.md): PAT single-tenant auth, no internal mTLS/JWT
- [ADR-0021](0021-diff-validation-and-skip-strategy.md): Diff validation and skip to save AI cycles
- [ADR-0022](0022-event-schema-format.md): JSON Schema now; Avro + registry deferred
