# ADR-0023: GKE Autopilot for Self-Review

## Context
The bot reviews its own PRs. After MSA migration, self-review requires Kafka and all services running simultaneously. CI runners are ephemeral and cannot host the full pipeline.

## Decision
Deploy to GKE Autopilot for always-on self-review infrastructure.

Observability:
- Traces: OTLP push to Tempo
- Metrics: Actuator pull by Prometheus
- Logs: stdout

## Consequences
- ✅ Bot can review its own PRs continuously
- ✅ GKE Autopilot handles node provisioning and scaling
- ❌ Cloud cost for always-on infrastructure
- ❌ More operational complexity than CI-only setup

## Notes
OpenTelemetry SDK auto-enables OTLP export for all signals. Tempo only accepts traces, so metrics/logs exporters are disabled via environment variables.
