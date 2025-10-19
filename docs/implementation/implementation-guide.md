# Implementation Guide

**Status**: Proposed
**Architecture**: Microservices (Planned)
**Last Updated**: 2025-10-18

> **⚠️ NOTE**: This document describes the **future implementation plan** for microservices architecture.
> The milestones (M1-M8) listed here are planned phases that have not yet been executed.
> The current system is a monolith application. See ADR-0015 for the microservices proposal.

---

## Overview

The system will implement microservices architecture with Kafka event-driven communication and Kubernetes orchestration.

**Core principles:**
- Context Understanding: Learn from history, ADRs, conventions
- Evidence-Based Feedback: Every finding has reasoning and references
- Safety by Design: Human-in-the-loop with explicit approvals

---

## Architecture

### Services

1. **webhook-service**: Receive webhooks, verify signatures, publish events
2. **context-service**: Analyze history, parse ADRs, learn conventions
3. **policy-service**: Evaluate rules, generate policy findings
4. **review-service**: AI analysis, aggregate results, generate SARIF
5. **integration-service**: GitHub Checks, SARIF upload, comments

### Infrastructure

- **Kafka** (KRaft mode, 3 brokers)
- **Kubernetes** (orchestration, scaling)
- **PostgreSQL** (per-service databases)
- **Redis** (shared caching)
- **OpenTelemetry Collector** (observability)

---

## Technology Stack

### Core Framework
- **Language**: Java 21
- **Framework**: Spring Boot 3.5.3
- **Reactive**: Spring WebFlux (webhook, review, integration services)

### Infrastructure
- **Event Bus**: Apache Kafka 3.7.0 (KRaft mode)
- **Orchestration**: Kubernetes 1.28+
- **Database**: PostgreSQL 16+
- **Cache**: Redis 7+
- **API Gateway**: Spring Cloud Gateway

### External Services
- **AI Provider**: Google Gemini API
- **VCS Platform**: GitHub (Webhooks, REST API, GraphQL, Checks API)
- **Secret Detection**: Gitleaks CLI

### Observability
- **Metrics & Traces**: OpenTelemetry
- **Export Protocol**: OTLP
- **Backends**: Prometheus, Jaeger, Grafana

---

## Implementation Phases

### M1: Service Scaffolding

**Objective**: Set up microservices structure and infrastructure.

**Tasks:**
1. Create service projects (5 services)
2. Set up Kafka cluster (KRaft mode)
3. Configure Kubernetes manifests
4. Implement basic event flow (webhook → kafka → integration)
5. Add OpenTelemetry instrumentation

**Success Criteria:**
- All services deploy to Kubernetes
- Kafka cluster operational (3 brokers)
- End-to-end event flow working
- Basic metrics exported

---

### M2: Webhook & Integration

**Objective**: Implement GitHub integration.

**Tasks:**
1. Webhook signature verification (HMAC-SHA256)
2. Event parsing and publishing
3. GitHub Checks API integration
4. SARIF upload to Code Scanning
5. Action button handling

**Success Criteria:**
- Webhook events received and validated
- Check Runs created successfully
- SARIF uploaded to GitHub
- Action buttons functional

---

### M3: Context Collection

**Objective**: Build context intelligence.

**Tasks:**
1. GitHub GraphQL client (historical PRs)
2. ADR parser (Markdown)
3. Convention learner (code patterns)
4. Evidence storage (PostgreSQL)
5. Context retrieval API

**Success Criteria:**
- Historical PRs fetched and analyzed
- ADRs parsed and indexed
- Conventions detected automatically
- Evidence stored and retrievable

---

### M4: Policy Engine

**Objective**: Implement rule-based review.

**Tasks:**
1. Policy definition format (YAML)
2. Rule evaluation engine
3. Built-in policies (security, quality)
4. Policy storage (PostgreSQL)
5. Finding generation

**Success Criteria:**
- Policies loaded and evaluated
- Findings generated correctly
- Built-in policies cover common issues
- Custom policies supported

---

### M5: AI Review

**Objective**: Integrate LLM-based analysis.

**Tasks:**
1. Gemini API client
2. Prompt templates with context injection
3. Response parsing
4. SARIF generation
5. Prompt injection protection

**Success Criteria:**
- AI analysis completes successfully
- Findings in valid SARIF format
- Context integrated into prompts
- Injection attacks prevented

---

### M6: Quality & Polish

**Objective**: Improve reliability and performance.

**Tasks:**
1. Error handling improvements
2. Performance optimization
3. Comprehensive logging
4. Test coverage improvements (target: 80%)

**Success Criteria:**
- Error conditions handled gracefully
- Latency within targets (< 10s p95)
- Logs actionable
- Test coverage > 80%

---

### M7: Beta Testing

**Objective**: Validate with real usage.

**Tasks:**
1. Deploy to production environment
2. Integrate with repositories
3. Monitor metrics
4. Fix bugs

**Success Criteria:**
- System runs stably for 4 weeks
- Quality metrics meet targets
- Critical bugs fixed

---

### M8: Launch

**Objective**: Prepare for public release.

**Tasks:**
1. Finalize documentation
2. Prepare repository for public
3. Create release artifacts
4. Launch announcement

**Success Criteria:**
- Documentation complete
- Repository public-ready
- System released

---

## Local Development

### Prerequisites

```bash
# Required
- Docker Desktop 4.0+
- Kubernetes enabled (Minikube or Docker Desktop)
- kubectl 1.28+
- Java 21+
- Gradle 8.0+

# Optional
- Helm 3.0+ (for simplified K8s deployment)
- k9s (Kubernetes CLI UI)
```

### Setup

**1. Clone repository:**
```bash
git clone https://github.com/Got-IT-Kai/pr-rule-bot.git
cd pr-rule-bot
```

**2. Start infrastructure (Docker Compose):**
```bash
cd infrastructure/docker-compose
docker-compose up -d
```

This starts: Kafka (3 brokers), PostgreSQL (5 databases), Redis, OTEL Collector

**3. Build services:**
```bash
./gradlew build
```

**4. Run services:**
```bash
# Option A: Run locally (for development)
cd services/webhook-service
./gradlew bootRun

# Option B: Deploy to Kubernetes (for testing)
kubectl apply -f infrastructure/k8s/
```

**5. Verify:**
```bash
# Check all pods running
kubectl get pods -n ai-code-reviewer

# Check Kafka topics
kubectl exec -it kafka-0 -n ai-code-reviewer -- \
  kafka-topics.sh --list --bootstrap-server localhost:9092
```

---

## Configuration

### Environment Variables

**webhook-service:**
```bash
KAFKA_BOOTSTRAP_SERVERS=kafka:9092
GITHUB_WEBHOOK_SECRET=<secret>
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318
```

**context-service:**
```bash
KAFKA_BOOTSTRAP_SERVERS=kafka:9092
GITHUB_APP_ID=<app-id>
GITHUB_APP_PRIVATE_KEY=<base64-encoded-key>
DATABASE_URL=postgresql://localhost:5432/context
REDIS_URL=redis://localhost:6379
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318
```

**review-service:**
```bash
KAFKA_BOOTSTRAP_SERVERS=kafka:9092
GEMINI_API_KEY=<api-key>
DATABASE_URL=postgresql://localhost:5432/review
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318
```

**integration-service:**
```bash
KAFKA_BOOTSTRAP_SERVERS=kafka:9092
GITHUB_APP_ID=<app-id>
GITHUB_APP_PRIVATE_KEY=<base64-encoded-key>
DATABASE_URL=postgresql://localhost:5432/integration
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318
```

---

## Quality Metrics

### Finding Precision
- **Target**: ≥ 75%
- **Measurement**: Manual review of sample findings

### Finding Recall
- **Policy**: ≥ 70%
- **Bugs**: ≥ 50%
- **Measurement**: Comparison with manual review

### Adoption Rate
- **Target**: ≥ 40%
- **Measurement**: Action button usage analytics

### Lead Time Impact
- **Target**: -20% reduction
- **Measurement**: PR open to merge time comparison

### System Performance
- **End-to-end latency**: < 10s (p95)
- **Webhook response**: < 100ms (p99)
- **Context collection**: < 2s (p95)
- **AI review**: < 5s (p95)

---

## Deployment

### Production Deployment (Kubernetes)

**1. Create namespace:**
```bash
kubectl create namespace ai-code-reviewer
```

**2. Create secrets:**
```bash
kubectl create secret generic github-secrets \
  --from-literal=webhook-secret=<secret> \
  --from-literal=app-private-key=<base64-key> \
  -n ai-code-reviewer

kubectl create secret generic ai-secrets \
  --from-literal=gemini-api-key=<key> \
  -n ai-code-reviewer

kubectl create secret generic db-secrets \
  --from-literal=username=<user> \
  --from-literal=password=<pass> \
  -n ai-code-reviewer
```

**3. Deploy infrastructure:**
```bash
kubectl apply -f infrastructure/k8s/kafka/
kubectl apply -f infrastructure/k8s/postgres/
kubectl apply -f infrastructure/k8s/redis/
```

**4. Deploy services:**
```bash
kubectl apply -f infrastructure/k8s/services/
```

**5. Configure Ingress:**
```bash
kubectl apply -f infrastructure/k8s/ingress.yaml
```

**6. Verify deployment:**
```bash
kubectl get all -n ai-code-reviewer
kubectl logs -f deployment/webhook-service -n ai-code-reviewer
```

---

## Monitoring

### Metrics

**Prometheus queries:**
```promql
# Request rate
rate(http_server_requests_total[5m])

# Error rate
rate(http_server_errors_total[5m]) / rate(http_server_requests_total[5m])

# Latency (p95)
histogram_quantile(0.95, rate(http_server_request_duration_seconds_bucket[5m]))

# Kafka consumer lag
kafka_consumer_lag{topic="pull-request.opened"}
```

### Distributed Tracing

**Jaeger UI**: http://localhost:16686

**Example queries:**
- Service: webhook-service
- Operation: POST /webhooks/github
- Tags: repository=owner/repo

### Logs

**View logs:**
```bash
# All services
kubectl logs -l app=webhook-service -n ai-code-reviewer

# Specific pod
kubectl logs webhook-service-abc123 -n ai-code-reviewer -f

# With trace ID
kubectl logs -l app=review-service -n ai-code-reviewer | grep trace_id=abc123
```

---

## Testing

### Unit Tests

```bash
./gradlew test
```

### Integration Tests

```bash
# Start test infrastructure
docker-compose -f docker-compose.test.yml up -d

# Run integration tests
./gradlew integrationTest
```

### End-to-End Tests

```bash
# Deploy to test cluster
kubectl apply -f infrastructure/k8s-test/

# Run E2E tests
./gradlew e2eTest
```

---

## Troubleshooting

### Service not starting

```bash
# Check pod status
kubectl describe pod <pod-name> -n ai-code-reviewer

# Check logs
kubectl logs <pod-name> -n ai-code-reviewer

# Common issues:
# - Missing secrets: Create secrets first
# - Kafka not ready: Wait for Kafka pods to be Running
# - Database connection: Check PostgreSQL service
```

### Kafka consumer lag

```bash
# Check consumer lag
kubectl exec -it kafka-0 -n ai-code-reviewer -- \
  kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group context-service-group --describe

# Reset offsets (if needed)
kubectl exec -it kafka-0 -n ai-code-reviewer -- \
  kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group context-service-group --reset-offsets --to-earliest \
  --topic pull-request.opened --execute
```

### High latency

```bash
# Check service metrics
kubectl top pods -n ai-code-reviewer

# Check traces in Jaeger
# Look for slow spans

# Scale service if needed
kubectl scale deployment review-service --replicas=3 -n ai-code-reviewer
```

---

## References

### Architecture
- [ADR-0015: Microservices Architecture](../adr/0015-microservices-architecture.md)
- [ADR-0016: Kubernetes Deployment Strategy](../adr/0016-kubernetes-deployment-strategy.md)
- [System Architecture](../architecture/system-architecture.md)
- [Security Architecture](../architecture/security-architecture.md)

### Related Documentation
- [Core README](../../README.md)
- [ADR Index](../adr/README.md)

---

**Last Updated**: 2025-10-16
