# ADR-0014: OpenTelemetry from Day 1

**Date**: 2025-10-16
**Status**: Proposed

---

## Context

Observability is often added as an afterthought, leading to:
- Blind spots when issues occur in production
- Difficulty debugging distributed operations
- No baseline metrics for quality validation
- Hard to measure impact of changes

**Problems with delayed observability:**
- **Reactive debugging**: No data to diagnose issues after they occur
- **Quality metrics unmeasurable**: Can't validate precision, recall, adoption targets
- **Performance unknown**: No baseline to optimize against
- **Operational blindness**: Can't tell if system is healthy or degrading

**Quality Metrics Requirements:**
- Finding Precision: ≥ 75%
- Finding Recall: ≥ 70% (policy), ≥ 50% (bugs)
- Adoption Rate: ≥ 40%
- Lead Time Impact: -20%
- False Alarm Rate: ≤ 5%

**These metrics require instrumentation from the start.**

## Decision

Implement **OpenTelemetry (OTEL)** instrumentation from initial launch, not as a later addition.

**Core capabilities:**
1. **Metrics**: Request rate, latency, error rate, business metrics (finding quality)
2. **Traces**: End-to-end request flow across modules
3. **Logs**: Structured logging correlated with traces
4. **Export**: OTLP protocol to any compatible backend

**Integration approach:**
- Auto-instrumentation for HTTP, database, external APIs
- Manual instrumentation for business operations
- Trace context propagation across async boundaries
- Zero vendor lock-in (OTLP standard protocol)

### Architecture

```
┌─────────────────────────────────────────────────────────┐
│              Application Components                      │
│                                                          │
│  ┌──────────────┐  ┌─────────────┐  ┌───────────────┐ │
│  │  Webhook     │  │   Context   │  │  AI Review    │ │
│  │  Handler     │  │   Engine    │  │   Engine      │ │
│  └──────┬───────┘  └──────┬──────┘  └───────┬───────┘ │
│         │                 │                  │          │
│         └─────────────────┴──────────────────┘          │
│                          │                               │
│                          ▼                               │
│              ┌───────────────────────┐                  │
│              │  OpenTelemetry SDK    │                  │
│              │  • Metrics            │                  │
│              │  • Traces             │                  │
│              │  • Logs               │                  │
│              └───────────┬───────────┘                  │
└──────────────────────────┼─────────────────────────────┘
                           │
                           ▼
                ┌──────────────────────┐
                │  OTLP Exporter       │
                │  (gRPC or HTTP)      │
                └──────────┬───────────┘
                           │
                           ▼
                ┌──────────────────────┐
                │  OTLP Collector      │
                │  (Optional)          │
                └──────────┬───────────┘
                           │
           ┌───────────────┼───────────────┐
           │               │               │
           ▼               ▼               ▼
    ┌──────────┐   ┌──────────┐   ┌──────────┐
    │Prometheus│   │  Jaeger  │   │  Loki    │
    │(Metrics) │   │ (Traces) │   │  (Logs)  │
    └──────────┘   └──────────┘   └──────────┘
```

**Backend Flexibility:**
- Prometheus + Jaeger + Loki (open source)
- Grafana Cloud (managed)
- Datadog, New Relic, Honeycomb (commercial)
- Any OTLP-compatible backend

### Instrumentation Levels

**Level 1: Auto-Instrumentation (Free)**
- Spring WebFlux HTTP requests/responses
- R2DBC database queries
- WebClient external API calls
- JVM metrics (heap, GC, threads)

**Level 2: Manual Business Metrics**
- Review completion rate
- Finding counts by type (policy vs AI)
- Context retrieval latency
- Evidence quality scores
- Action button usage (Apply fix, Dismiss)

**Level 3: Custom Spans**
- Context collection flow
- Policy evaluation steps
- AI prompt building and parsing
- SARIF generation

### Key Metrics

**RED Method (Requests, Errors, Duration):**
```kotlin
// Auto-instrumented by WebFlux
- http.server.requests (count)
- http.server.request.duration (histogram)
- http.server.errors (count)
```

**Business Metrics:**
```kotlin
// Manual instrumentation
- review.completed (counter) by status, repository
- review.duration (histogram)
- finding.generated (counter) by type, severity
- finding.precision (gauge) - % accepted
- finding.recall (gauge) - % actual issues found
- action.clicked (counter) by action_type
- context.retrieval.duration (histogram)
- ai.token.usage (counter) by model
```

**Database Metrics:**
```kotlin
// Auto-instrumented by R2DBC
- db.query.duration (histogram)
- db.connection.pool.usage (gauge)
- db.errors (counter)
```

**External API Metrics:**
```kotlin
// Auto-instrumented by WebClient
- github.api.requests (counter) by endpoint
- github.api.duration (histogram)
- github.api.errors (counter) by status_code
- gemini.api.requests (counter)
- gemini.api.duration (histogram)
- gemini.api.token.usage (counter)
```

### Trace Structure

**Example: Pull Request Review Trace**
```
┌─ webhook.received (root span)
│  ├─ signature.verified
│  ├─ event.validated
│  └─ event.published
│
├─ review.started
│  ├─ check_run.created (GitHub API call)
│  │
│  ├─ context.collected
│  │  ├─ historical_prs.fetched (GraphQL)
│  │  ├─ adrs.parsed
│  │  └─ conventions.retrieved (Redis)
│  │
│  ├─ policy.evaluated
│  │  ├─ policies.loaded
│  │  └─ rules.matched
│  │
│  ├─ ai.reviewed
│  │  ├─ prompt.built
│  │  ├─ gemini.api.called
│  │  └─ response.parsed
│  │
│  ├─ findings.aggregated
│  │  └─ sarif.generated
│  │
│  └─ check_run.updated (GitHub API call)
│
└─ review.completed (end span)
   Duration: 3.2s
   Status: success
   Findings: 5
```

**Trace Context Propagation:**
- Trace ID carried through all operations
- Logs tagged with trace ID for correlation
- Async operations maintain parent span context

## Consequences

### Positive

**Proactive Issue Detection**
- Know about issues before users report them
- Alert on error rate spikes, high latency
- Identify bottlenecks early

**Quality Validation**
- Measure precision, recall, adoption rate objectively
- Track metrics against quality targets
- Data-driven improvement decisions

**Performance Optimization**
- Identify slow operations (context collection, AI calls)
- Optimize based on actual data, not guesses
- Track impact of optimizations

**Debugging Efficiency**
- Trace full request flow across modules
- Correlate logs with traces
- Reproduce issues with trace context

**Production Confidence**
- Know system is healthy (or not)
- Understand typical behavior patterns
- Detect anomalies automatically

**Business Insights**
- Which repositories use the system most
- What types of findings are generated
- How often action buttons are clicked
- AI token usage and costs

### Negative

**Runtime Overhead**
- CPU: ~1-3% for instrumentation
- Memory: ~50-100MB for SDK and buffering
- Network: OTLP export traffic

**Configuration Complexity**
- Must configure exporter endpoint
- Set sampling rates for high-traffic scenarios
- Manage metric cardinality (avoid label explosion)

**Backend Dependency**
- Requires OTLP collector or backend
- Storage costs for metrics, traces, logs
- Backend maintenance (if self-hosted)

**Learning Curve**
- Understand OTEL concepts (traces, spans, attributes)
- Write custom instrumentation correctly
- Query and analyze telemetry data

**Data Volume**
- Metrics: ~1KB/min per instance
- Traces: ~10-100KB per review (sampled)
- Logs: ~1-10MB/day per instance

## Alternatives Considered

### Alternative 1: Application Performance Monitoring (APM) Agent

Use commercial APM (Datadog, New Relic) agent.

**Pros:**
- Easy setup (single agent install)
- Rich UI and dashboards out of box
- Auto-instrumentation comprehensive

**Cons:**
- Vendor lock-in
- Proprietary instrumentation
- Higher cost ($15-50/host/month)
- Can't switch backends easily

**Why rejected:** OTEL provides same capabilities without lock-in

### Alternative 2: Metrics Only (Micrometer)

Use Spring Boot Micrometer for metrics, no traces.

**Pros:**
- Built into Spring Boot
- Lightweight
- Simple setup

**Cons:**
- No distributed tracing
- No log correlation
- Limited debugging capability
- Can't trace end-to-end flows

**Why rejected:** Traces are essential for debugging distributed operations

### Alternative 3: Logs Only

Rely on structured logging without metrics/traces.

**Pros:**
- Simplest approach
- Low overhead
- No additional dependencies

**Cons:**
- No aggregated metrics (must query logs)
- No visual traces
- Hard to detect trends
- Expensive to query at scale

**Why rejected:** Can't measure quality metrics effectively

### Alternative 4: Defer Observability

Add observability later when needed.

**Pros:**
- Simpler initial implementation
- No initial overhead

**Cons:**
- **Critical issue**: Can't validate quality metrics from launch
- No baseline data for comparison
- Harder to add later (retrofit instrumentation)
- Blind to production issues during launch

**Why rejected:** Quality metrics are requirement from initial launch, need data from day 1

## Implementation

### Phase 1: Dependency Setup

**Gradle Dependencies:**
```kotlin
dependencies {
    // OpenTelemetry BOM
    implementation(platform("io.opentelemetry:opentelemetry-bom:1.32.0"))

    // Core SDK
    implementation("io.opentelemetry:opentelemetry-api")
    implementation("io.opentelemetry:opentelemetry-sdk")

    // Auto-instrumentation
    implementation("io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter")

    // Exporters
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

    // Micrometer bridge (for Spring Boot Actuator)
    implementation("io.micrometer:micrometer-registry-otlp")
}
```

### Phase 2: Configuration

**application.yml:**
```yaml
management:
  otlp:
    metrics:
      export:
        url: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318/v1/metrics}
        step: 60s # Export interval
    tracing:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318/v1/traces}

  tracing:
    sampling:
      probability: 1.0 # 100% sampling for initial implementation (low traffic)

otel:
  service:
    name: ai-code-reviewer
    version: ${APPLICATION_VERSION:dev}
  resource:
    attributes:
      environment: ${ENVIRONMENT:development}
      deployment.region: ${REGION:local}
```

**Environment Variables:**
```bash
# OTLP Exporter
OTEL_EXPORTER_OTLP_ENDPOINT=http://collector:4318
OTEL_SERVICE_NAME=ai-code-reviewer
OTEL_RESOURCE_ATTRIBUTES=environment=production,region=us-east-1

# Sampling (adjust for high traffic)
OTEL_TRACES_SAMPLER=parentbased_traceidratio
OTEL_TRACES_SAMPLER_ARG=1.0
```

### Phase 3: Custom Instrumentation

**Metrics:**
```kotlin
@Component
class ReviewMetrics(
    private val meterRegistry: MeterRegistry
) {
    private val reviewCounter = meterRegistry.counter(
        "review.completed",
        Tags.of("status", "success")
    )

    private val findingCounter = meterRegistry.counter(
        "finding.generated",
        Tags.of("type", "policy", "severity", "error")
    )

    private val adoptionGauge = meterRegistry.gauge(
        "finding.adoption_rate",
        Tags.empty(),
        AtomicDouble(0.0)
    )

    fun recordReviewCompleted(status: String, repository: String) {
        reviewCounter.increment()
        // Also record with tags
        Counter.builder("review.completed")
            .tag("status", status)
            .tag("repository", repository)
            .register(meterRegistry)
            .increment()
    }

    fun recordFindingGenerated(type: String, severity: String) {
        Counter.builder("finding.generated")
            .tag("type", type)
            .tag("severity", severity)
            .register(meterRegistry)
            .increment()
    }

    fun updateAdoptionRate(rate: Double) {
        adoptionGauge?.set(rate)
    }
}
```

**Traces:**
```kotlin
@Component
class ContextCollector(
    private val tracer: Tracer
) {
    fun collectContext(repository: String): Mono<Context> {
        val span = tracer.spanBuilder("context.collected")
            .setAttribute("repository", repository)
            .startSpan()

        return Mono.using(
            { span.makeCurrent() },
            { scope ->
                fetchHistoricalPrs(repository)
                    .flatMap { prs -> parseAdrs(repository) }
                    .flatMap { adrs -> retrieveConventions(repository) }
                    .map { conventions ->
                        Context(prs, adrs, conventions)
                    }
                    .doOnSuccess { context ->
                        span.setAttribute("prs_count", context.prs.size)
                        span.setAttribute("adrs_count", context.adrs.size)
                        span.setStatus(StatusCode.OK)
                    }
                    .doOnError { error ->
                        span.recordException(error)
                        span.setStatus(StatusCode.ERROR, error.message ?: "Unknown error")
                    }
            },
            { scope ->
                scope.close()
                span.end()
            }
        )
    }

    private fun fetchHistoricalPrs(repository: String): Mono<List<PullRequest>> {
        val span = tracer.spanBuilder("historical_prs.fetched")
            .setAttribute("repository", repository)
            .startSpan()

        return githubClient.fetchPullRequests(repository)
            .doFinally { span.end() }
    }
}
```

### Phase 4: Dashboard Setup

**Grafana Dashboard (Prometheus + Jaeger):**

**Panels:**
1. **Overview**
   - Total reviews completed (counter)
   - Average review duration (gauge)
   - Error rate (gauge)

2. **Quality Metrics**
   - Finding precision (gauge)
   - Finding recall (gauge)
   - Adoption rate (gauge)
   - False alarm rate (gauge)

3. **Performance**
   - Request latency (p50, p95, p99)
   - Context retrieval duration
   - AI API latency
   - Database query duration

4. **Business Metrics**
   - Reviews by repository (bar chart)
   - Findings by type (pie chart)
   - Action button usage (time series)

5. **Infrastructure**
   - JVM heap usage
   - GC pause time
   - Database connection pool
   - Redis cache hit rate

**Alerting Rules:**
```yaml
groups:
  - name: ai_code_reviewer
    rules:
      - alert: HighErrorRate
        expr: rate(http_server_errors_total[5m]) > 0.05
        for: 5m
        annotations:
          summary: "Error rate above 5%"

      - alert: SlowReviews
        expr: histogram_quantile(0.95, rate(review_duration_seconds_bucket[5m])) > 30
        for: 10m
        annotations:
          summary: "95th percentile review duration > 30s"

      - alert: LowPrecision
        expr: finding_precision < 0.75
        for: 1h
        annotations:
          summary: "Finding precision below 75% target"
```

## Validation

### Success Criteria

**Initial Launch:**
- All auto-instrumentation working (HTTP, DB, external APIs)
- Custom business metrics exported
- Traces visible in backend
- Dashboard operational
- Alerting configured

**Overhead:**
- CPU overhead: < 3%
- Memory overhead: < 100MB
- Export latency: < 100ms (async)

**Quality Metrics Tracked:**
- Finding Precision: measured and graphed
- Finding Recall: measured and graphed
- Adoption Rate: measured and graphed
- Lead Time Impact: measured and graphed

### Test Strategy

**Unit Tests:**
- Metrics recorded correctly
- Spans created with proper attributes
- Trace context propagated

**Integration Tests:**
- OTLP exporter sends data
- Backend receives telemetry
- Dashboards display metrics

**Load Tests:**
- Overhead under load measured
- No memory leaks from instrumentation
- Export doesn't cause backpressure

## Related Decisions

- [Implementation Guide](../implementation/implementation-guide.md) - Observability requirements
- [System Architecture](../architecture/system-architecture.md) - Observability layer

## References

### OpenTelemetry
- [OpenTelemetry Specification](https://opentelemetry.io/docs/specs/otel/)
- [Java SDK Documentation](https://opentelemetry.io/docs/languages/java/)
- [Spring Boot Integration](https://opentelemetry.io/docs/languages/java/automatic/spring-boot/)

### OTLP Protocol
- [OTLP Specification](https://opentelemetry.io/docs/specs/otlp/)
- [OTLP Exporter Configuration](https://opentelemetry.io/docs/specs/otel/protocol/exporter/)

### Backends
- [Prometheus](https://prometheus.io/)
- [Jaeger](https://www.jaegertracing.io/)
- [Grafana Loki](https://grafana.com/oss/loki/)
- [Grafana Cloud](https://grafana.com/products/cloud/)

### Best Practices
- [RED Method](https://www.weave.works/blog/the-red-method-key-metrics-for-microservices-architecture/)
- [Distributed Tracing Guide](https://opentelemetry.io/docs/concepts/signals/traces/)

---

**Last Updated**: 2025-10-16
