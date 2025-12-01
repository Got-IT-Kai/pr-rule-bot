# Development Notes

Concise list of current constraints and follow-ups.

- Self-review not supported: cannot review its own repo via webhook. Workarounds (lightweight CI mode/hybrid/API trigger) deferred.
- Local/dev scope: Docker Compose only; single PAT org; no service-to-service auth; not production-hardened.
- Observability: Prometheus/Jaeger/Grafana for dev; no SLOs or production tuning.
- Idempotency: In-memory Caffeine caches (per instance, lost on restart).
- Comment posting: No retry; failures emit `comment.failed` events for manual follow-up.
- Skip reasons: Diff validation skips non-reviewable diffs; downstream comments are generic (no detailed reason propagation).
