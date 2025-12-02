# System Architecture

AI-assisted PR review built to survive webhook timeouts by moving work off the request path. 

Webhooks enqueue events to Kafka; downstream services fetch diffs, run AI review, and post back to GitHub.

## Runtime flow
```
GitHub webhook
   │
   ▼
webhook-service ──► Kafka ──► context-service ──► review-service ──► integration-service ──► GitHub comment
```

## Service roles
- webhook-service: validate HMAC, parse PR event, emit pull-request.received, return 202 quickly.
- context-service: fetch diff + file metadata, validate diff, emit context.collected (COMPLETED/SKIPPED/FAILED).
- review-service: split diff by file, enforce token budget, call Gemini/Ollama, emit review.completed or review.failed.
- integration-service: post PR comments for completed reviews or for failed/skipped contexts; no retry, uses compensation event on failure.

## Intentional constraints
- Local/Docker Compose, single Kafka broker (KRaft).
- Stateless; GitHub is the source of truth (no DB, no history/analytics).
- Single-tenant PAT auth; service-level auth hardening not added (internal/dev use).
- Idempotency is per-instance cache (Caffeine); restarts can reprocess duplicates.

## Why this shape
- Webhooks must respond within ~10s → offload to Kafka.
- AI review is slow/expensive → isolate in review-service and keep webhook/context lean.
- Diff validation up front → skip binary/rename/permission-only/no-hunk diffs to save AI cycles.
- Separate integration → keep GitHub posting failures contained and observable via compensation events.

## Known gaps to revisit
- No retry/durable queue for comment posting; transient GitHub failures emit comment.failed only.
- Kafka message size tuning not applied; large diffs/markdown could exceed defaults.
- Skip reasons are generic; not propagated in events/comments.

## References
- docs/adr/0001-non-blocking-io.md
- docs/adr/0003-webhook-security.md
- docs/adr/0015-microservices-architecture.md
- docs/notes.md
