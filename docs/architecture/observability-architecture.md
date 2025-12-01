# Observability (Local Dev Stack)

Local-only setup to see metrics and traces while developing:

```
Services → Actuator → Prometheus (metrics)
        → OTLP → Jaeger (traces)
```

## Components
- Prometheus (localhost:9090): scrapes Actuator metrics.
- Jaeger (localhost:16686): receives OTLP traces.
- Grafana (localhost:3000): dashboards over Prometheus/Jaeger.

## Config snapshot
- Services expose `health,prometheus` via Actuator; tracing exports to Jaeger OTLP (4318). No OTLP metrics push; Prometheus pull model only.

## What we observe
- Metrics: JVM, HTTP, Kafka consumer lag, custom counters/timers.
- Traces: webhook → Kafka → context/review/integration.

## Scope
- Dev/learning only; not production-grade hardening or SLOs.
