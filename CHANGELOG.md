# Changelog

Currently tracking only the major milestones:

- Initial microservices split (webhook, context, review, integration) with Kafka and WebFlux.
- HMAC-secured GitHub webhook ingress.
- Diff validation + skip to avoid non-reviewable changes.
- AI review orchestration (Gemini/Ollama) with token-budgeted chunking.
- Comment posting with compensation on failure (no retry) and in-memory idempotency per instance.
- Local observability stack (Prometheus/Jaeger/Grafana) for dev.

Future entries can be added here when new milestones land.
