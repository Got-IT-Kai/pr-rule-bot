# ADR-0006: Observability Strategy with OpenTelemetry

**Date:** 2025-10-05
**Status:** Proposed

## Context

The application currently has OpenTelemetry (OTEL) Java agent configured in `build.gradle`, but lacks proper backend configuration and environment-specific setup. This causes several critical issues:

**Current problems:**
1. OTEL agent tries to connect to non-existent backend, flooding logs with errors
2. Cannot analyze performance or trace issues during CI runs
3. Unit tests unnecessarily load OTEL agent (slower execution)
4. No observability infrastructure for production
5. Difficult to debug reactive pipelines and identify bottlenecks

**Why observability is critical for this application:**
- **Reactive pipelines:** Need distributed tracing to understand async flow
- **AI API integration:** Must track latency and costs
- **CI performance:** Track performance regression between PRs
- **Production:** Monitor real-world performance and diagnose issues
- **Cost optimization:** Analyze where AI API calls happen

**Related Issue:** OTEL connection errors, performance debugging difficulty

## Decision

Implement comprehensive observability strategy with environment-specific OpenTelemetry configuration.

**Strategy:**
- **Unit Tests:** Disable OTEL (fast execution)
- **Integration Tests:** Enable OTEL with logging exporter (trace verification)
- **Performance Tests:** Enable OTEL with OTLP to Tempo via Docker Compose (E2E load testing)
- **CI:** Enable OTEL with file exporter, save as artifacts (regression detection)
- **Production:** Enable OTEL with OTLP to backend (monitoring)

**Observability stack:**
- **Traces:** Tempo (production) or Jaeger (dev/test)
- **Metrics:** Prometheus
- **Logs:** Structured JSON with trace correlation
- **Visualization:** Grafana

## Consequences

### Positive

- Clear performance bottlenecks visible through distributed tracing
- Reactive pipeline debugging becomes much easier
- Performance regression detection in CI (artifact-based analysis)
- Production monitoring and alerting capability
- Cost optimization through AI API call analysis
- Better incident response with correlated traces/logs/metrics
- Environment-appropriate observability overhead

### Negative

- Additional infrastructure required (Tempo, Prometheus, Grafana, Redis)
- Slight performance overhead in production (~2-5%)
- Storage requirements for traces and metrics
- Learning curve for OTEL/Tempo/Grafana
- CI pipeline complexity increases
- Must maintain OTEL collector configuration

## Alternatives Considered

### Alternative 1: Logging only

**Pros:**
- Simple to implement
- Low overhead
- No additional infrastructure

**Cons:**
- Cannot trace request flow through reactive pipeline
- Difficult to correlate events across async operations
- No visualization of bottlenecks

**Why rejected:** Insufficient for debugging reactive/async systems.

### Alternative 2: Spring Boot Actuator metrics only

**Pros:**
- Built into Spring Boot
- Simple Prometheus integration

**Cons:**
- No distributed tracing
- No request-level visibility
- Cannot correlate metrics with specific operations

**Why rejected:** Metrics alone don't provide enough context.

### Alternative 3: Commercial APM (Datadog, New Relic, Dynatrace)

**Pros:**
- Fully managed
- Rich features
- Great UX

**Cons:**
- Expensive ($100-500/month minimum)
- Vendor lock-in
- Overkill for current scale

**Why rejected:** Cost prohibitive for early-stage project.

## Implementation Strategy

### Environment-specific OTEL configuration

```gradle
// build.gradle
ext {
    otelEnabled = project.property('otel.enabled') ?: 'false'
}

bootRun {
    if (otelEnabled == 'true') {
        jvmArgs += ["-javaagent:${otelAgentPath}"]
        environment 'OTEL_SERVICE_NAME', 'pr-review-bot'
        environment 'OTEL_TRACES_EXPORTER', System.getenv('OTEL_TRACES_EXPORTER') ?: 'none'
    }
}
```

### Environment configuration matrix

| Environment | OTEL Agent | Exporter | Storage | Purpose |
|-------------|------------|----------|---------|---------|
| Unit Test | ❌ | None | N/A | Fast execution |
| Integration Test | ✅ | Logging | Console | Flow verification |
| Performance Test | ✅ | OTLP → Tempo | Docker volume | E2E load testing |
| CI | ✅ | OTLP → File | Artifacts | Regression detection |
| Local Dev | Optional | OTLP → Tempo | Docker volume | Debugging |
| Production | ✅ | OTLP → Tempo | Persistent storage | Monitoring |

### CI integration approach

```yaml
# GitHub Actions
services:
  otel-collector:
    image: otel/opentelemetry-collector:latest

steps:
  - name: Run with OTEL
    run: ./gradlew bootRun -Potel.enabled=true
    env:
      OTEL_EXPORTER_OTLP_ENDPOINT: http://localhost:4317

  - name: Upload trace artifacts
    uses: actions/upload-artifact@v4
    with:
      name: otel-traces-${{ github.run_number }}
      path: traces/
```

### Key metrics to track

**Application:**
- `ai.review.duration` - AI API call latency
- `ai.review.success/error` - Success/error rates
- `github.api.calls` - GitHub API usage
- `review.total.duration` - End-to-end time

**Infrastructure:**
- JVM heap/non-heap memory
- GC pause time
- HTTP request rate/latency
- Reactor scheduler queue size

## Implementation Tasks

1. Update Gradle configuration for environment-specific OTEL
2. Add Spring Boot Actuator and Micrometer dependencies
3. Configure application.yml for each profile (unit/integration/ci/prod)
4. Set up CI workflow with OTEL collector and artifact upload
5. Create docker-compose for local observability stack
6. Implement custom instrumentation for AI clients
7. Create Grafana dashboards and Prometheus alerts
8. Production deployment and load testing

## Monitoring

Track:
- OTEL connection errors (alert if > 10/hour)
- Trace export failures
- Missing spans in critical paths
- Performance regression in CI artifacts

## v1.0 Update: Defer OTEL to v2.0

**Date:** 2025-10-05
**Decision:** Remove OpenTelemetry from v1.0 release

**Rationale:**
- OTEL configuration errors blocking development
- v1.0 focuses on core functionality stabilization
- Observability infrastructure adds significant complexity
- Can rely on basic logging and metrics for v1.0
- Full observability stack better suited for v2.0 after core stability

**v1.0 Actions:**
1. Remove OTEL Java agent from build.gradle
2. Remove OTEL configuration from application.yml
3. Clean up OTEL-related errors in logs
4. Document decision in v1.0 release notes

**v2.0 Plan:**
- Revisit full OTEL implementation per this ADR
- Implement environment-specific configuration
- Set up observability infrastructure (Tempo, Prometheus, Grafana)
- Enable distributed tracing for production

## References

- [OpenTelemetry Java](https://opentelemetry.io/docs/instrumentation/java/)
- [Grafana Tempo](https://grafana.com/docs/tempo/latest/)
- [Prometheus](https://prometheus.io/docs/)
- Related: [ADR-0001: Non-Blocking I/O](./0001-non-blocking-io.md)
- Related: [v1.0 Release Plan](../release/v1.0-plan.md)
- Design Document: [Observability Architecture](../design/observability-architecture.md) (deferred to v2.0)
