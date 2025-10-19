# System Architecture

**Status**: Proposed
**Architecture**: Microservices (Planned)
**Last Updated**: 2025-10-18

> **⚠️ NOTE**: This document describes the **planned** microservices architecture that has not yet been implemented.
> The current implementation is a monolith application. See ADR-0015 for the microservices proposal.

---

## Overview

The system plans to adopt microservices architecture with event-driven communication via Kafka, orchestrated by Kubernetes.

**Design principles:**
- Service per bounded context
- Asynchronous event-driven communication
- Independent deployment and scaling
- Failure isolation

---

## System Context

```
┌──────────────────────────────────────────────────────┐
│                  GitHub Platform                      │
│  Webhooks, REST API, GraphQL, Checks API, SARIF      │
└─────────────────────┬────────────────────────────────┘
                      │ HTTPS
                      ▼
┌──────────────────────────────────────────────────────┐
│                   API Gateway                         │
│             (Spring Cloud Gateway)                    │
└─────────────────────┬────────────────────────────────┘
                      │
              ┌───────┴────────┐
              ▼                ▼
    ┌─────────────┐    ┌──────────────┐
    │  Webhook    │    │ Integration  │
    │  Service    │    │  Service     │
    └──────┬──────┘    └──────▲───────┘
           │                  │
           ▼                  │
      ┌─────────┐             │
      │  Kafka  │─────────────┘
      └────┬────┘
           │
    ┌──────┴─────┬────────────┐
    ▼            ▼            ▼
┌────────┐  ┌────────┐  ┌─────────┐
│Context │  │ Policy │  │ Review  │
│Service │  │Service │  │ Service │
└────────┘  └────────┘  └─────────┘
```

---

## Services

### 1. Webhook Service

**Responsibility**: Receive and validate GitHub webhooks

**Operations:**
- Verify HMAC-SHA256 signature
- Parse webhook payload
- Publish event to Kafka
- Return 200 OK immediately

**Technology**: Spring Boot WebFlux, Kafka Producer

**Scaling**: HPA based on request rate (2-10 replicas)

**Resources**: CPU 100m-500m, Memory 256Mi-512Mi

---

### 2. Context Service

**Responsibility**: Collect project context for reviews

**Operations:**
- Fetch historical PRs (GitHub GraphQL)
- Parse Architecture Decision Records
- Learn code conventions
- Store evidence in PostgreSQL

**Technology**: Spring Boot, Kafka Consumer/Producer, PostgreSQL

**Scaling**: HPA based on Kafka queue depth (1-5 replicas)

**Resources**: CPU 250m-1000m, Memory 512Mi-1Gi

**Database**: PostgreSQL (evidence, conventions, historical patterns)

---

### 3. Policy Service

**Responsibility**: Evaluate policy rules

**Operations:**
- Load policy definitions
- Evaluate rules against code changes
- Generate policy violation findings
- Publish results to Kafka

**Technology**: Spring Boot, Kafka Consumer/Producer, PostgreSQL

**Scaling**: HPA based on Kafka queue depth (1-3 replicas)

**Resources**: CPU 100m-500m, Memory 256Mi-512Mi

**Database**: PostgreSQL (policy definitions, rule results)

---

### 4. Review Service

**Responsibility**: AI-powered review and orchestration

**Operations:**
- Build prompts with context
- Call AI provider (Gemini API)
- Parse AI responses
- Aggregate policy + AI findings
- Generate SARIF output
- Publish review results

**Technology**: Spring Boot WebFlux, Kafka Consumer/Producer, PostgreSQL

**Scaling**: HPA based on AI API latency (1-5 replicas)

**Resources**: CPU 500m-2000m, Memory 2Gi-4Gi

**Database**: PostgreSQL (review results, findings)

---

### 5. Integration Service

**Responsibility**: GitHub API integration

**Operations:**
- Create/update Check Runs
- Upload SARIF to Code Scanning
- Post review comments
- Handle action button clicks

**Technology**: Spring Boot WebFlux, Kafka Consumer, GitHub API

**Scaling**: HPA based on GitHub API rate (1-3 replicas)

**Resources**: CPU 250m-1000m, Memory 512Mi-1Gi

---

## Event Flow

### Pull Request Review Workflow

```
1. GitHub → Webhook Service
   POST /webhooks/github

2. Webhook Service → Kafka
   Publish: pull-request.opened

3. Kafka → Context Service + Policy Service (parallel)
   Consume: pull-request.opened

4. Context Service → Kafka
   Publish: context.collected

5. Policy Service → Kafka
   Publish: policy.evaluated

6. Kafka → Review Service
   Consume: context.collected, policy.evaluated

7. Review Service → Gemini API
   POST generateContent

8. Review Service → Kafka
   Publish: review.completed

9. Kafka → Integration Service
   Consume: review.completed

10. Integration Service → GitHub
    PATCH /repos/{owner}/{repo}/check-runs/{id}
    POST /repos/{owner}/{repo}/code-scanning/sarifs
```

**Latency breakdown:**
- Webhook → Kafka: ~50ms
- Context collection: ~1-2s
- Policy evaluation: ~200-500ms
- AI review: ~2-5s
- Integration: ~500ms
- **Total**: ~4-9s (p95)

---

## Kafka Topics

### Topic Configuration

```yaml
pull-request.opened:
  partitions: 3
  replication-factor: 2
  retention: 7 days

context.collected:
  partitions: 3
  replication-factor: 2
  retention: 7 days

policy.evaluated:
  partitions: 3
  replication-factor: 2
  retention: 7 days

ai.reviewed:
  partitions: 3
  replication-factor: 2
  retention: 7 days

review.completed:
  partitions: 3
  replication-factor: 2
  retention: 7 days
```

**Partitioning strategy**: By repository (maintains ordering per repo)

**Consumer groups**:
- context-service-group
- policy-service-group
- review-service-group
- integration-service-group

---

## Data Model

### Service Databases

**Context Service (PostgreSQL)**:
```sql
CREATE TABLE evidence (
    id UUID PRIMARY KEY,
    repository VARCHAR(255),
    source_type VARCHAR(50),
    source_reference VARCHAR(255),
    category VARCHAR(50),
    excerpt TEXT,
    learned_at TIMESTAMP
);

CREATE TABLE conventions (
    id UUID PRIMARY KEY,
    repository VARCHAR(255),
    type VARCHAR(50),
    pattern VARCHAR(1024),
    confidence_score DOUBLE PRECISION,
    occurrences INT
);
```

**Policy Service (PostgreSQL)**:
```sql
CREATE TABLE policies (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255),
    severity VARCHAR(20),
    condition TEXT,
    message_template TEXT,
    enabled BOOLEAN
);
```

**Review Service (PostgreSQL)**:
```sql
CREATE TABLE reviews (
    id UUID PRIMARY KEY,
    repository VARCHAR(255),
    pr_number INT,
    commit_sha VARCHAR(40),
    status VARCHAR(50),
    findings_count INT,
    created_at TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE TABLE findings (
    id UUID PRIMARY KEY,
    review_id UUID REFERENCES reviews(id),
    type VARCHAR(50),
    severity VARCHAR(20),
    message TEXT,
    file_path VARCHAR(1024),
    start_line INT,
    suggested_fix TEXT
);
```

---

## Kubernetes Deployment

### Namespace

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: ai-code-reviewer
```

### Service Deployment Example

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: webhook-service
  namespace: ai-code-reviewer
spec:
  replicas: 2
  selector:
    matchLabels:
      app: webhook-service
  template:
    metadata:
      labels:
        app: webhook-service
    spec:
      containers:
      - name: webhook-service
        image: ai-code-reviewer/webhook-service:latest
        ports:
        - containerPort: 8080
        env:
        - name: KAFKA_BOOTSTRAP_SERVERS
          value: kafka:9092
        - name: GITHUB_WEBHOOK_SECRET
          valueFrom:
            secretKeyRef:
              name: github-secrets
              key: webhook-secret
        resources:
          requests:
            cpu: 100m
            memory: 256Mi
          limits:
            cpu: 500m
            memory: 512Mi
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
---
apiVersion: v1
kind: Service
metadata:
  name: webhook-service
  namespace: ai-code-reviewer
spec:
  selector:
    app: webhook-service
  ports:
  - port: 80
    targetPort: 8080
```

### Kafka StatefulSet (KRaft Mode)

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: kafka
  namespace: ai-code-reviewer
spec:
  serviceName: kafka-headless
  replicas: 3
  selector:
    matchLabels:
      app: kafka
  template:
    metadata:
      labels:
        app: kafka
    spec:
      containers:
      - name: kafka
        image: apache/kafka:3.7.0
        ports:
        - containerPort: 9092
        - containerPort: 9093
        env:
        - name: KAFKA_PROCESS_ROLES
          value: "broker,controller"
        - name: KAFKA_CONTROLLER_QUORUM_VOTERS
          value: "1@kafka-0:9093,2@kafka-1:9093,3@kafka-2:9093"
        volumeMounts:
        - name: data
          mountPath: /var/lib/kafka/data
  volumeClaimTemplates:
  - metadata:
      name: data
    spec:
      accessModes: ["ReadWriteOnce"]
      resources:
        requests:
          storage: 10Gi
```

### HPA (Horizontal Pod Autoscaler)

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: webhook-service-hpa
  namespace: ai-code-reviewer
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: webhook-service
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
```

---

## Security

### Authentication

**GitHub Webhook**: HMAC-SHA256 signature verification

**GitHub API**: JWT + Installation access token

**Kafka**: No authentication (internal cluster network)

**PostgreSQL**: Username/password from Kubernetes Secrets

### Secrets Management

**Kubernetes Secrets:**
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: github-secrets
  namespace: ai-code-reviewer
type: Opaque
data:
  webhook-secret: <base64-encoded>
  app-private-key: <base64-encoded>

---
apiVersion: v1
kind: Secret
metadata:
  name: ai-secrets
  namespace: ai-code-reviewer
type: Opaque
data:
  gemini-api-key: <base64-encoded>
```

### Network Policies

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: webhook-service-policy
  namespace: ai-code-reviewer
spec:
  podSelector:
    matchLabels:
      app: webhook-service
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: ingress-nginx
  egress:
  - to:
    - podSelector:
        matchLabels:
          app: kafka
```

---

## Observability

### OpenTelemetry Integration

**Architecture:**
```
Services → OTEL SDK → OTEL Collector → Backends
                                      ├─ Prometheus (metrics)
                                      ├─ Jaeger (traces)
                                      └─ Loki (logs)
```

**Metrics per service:**
- Request rate, latency, error rate
- Kafka consumer lag
- Database connection pool usage
- AI API token usage (review service)

**Distributed tracing:**
- Trace ID propagated via Kafka headers
- End-to-end trace from webhook to GitHub update
- Span per service operation

---

## Scaling Strategy

### Horizontal Scaling

**Webhook Service:**
- Trigger: Request rate > 100 req/min per pod
- Scale: 2 → 10 replicas

**Context Service:**
- Trigger: Kafka consumer lag > 100 messages
- Scale: 1 → 5 replicas

**Review Service:**
- Trigger: AI API latency > 10s
- Scale: 1 → 5 replicas

### Vertical Scaling

**Review Service**: May require larger pods for large PRs
- Standard: 2Gi RAM, 500m CPU
- Large PRs: 4Gi RAM, 2000m CPU

---

## Failure Handling

### Service Failures

**Webhook Service down:**
- GitHub retries webhook delivery
- No data loss

**Context Service down:**
- Review continues with policy-only findings
- Graceful degradation

**Review Service down:**
- Events remain in Kafka queue
- Processing resumes when service restarts

**Integration Service down:**
- Review results stored in database
- Retried when service restarts

### Kafka Failures

**Broker down:**
- Replication factor 2 ensures availability
- Automatic leader election

**Consumer lag:**
- HPA scales consumer replicas
- Alert on lag > threshold

---

## Deployment Model

### Development

```bash
docker-compose up
```

Includes: All services, Kafka (single broker), PostgreSQL, Redis

### Staging/Production

**Kubernetes cluster:**
- 3 nodes minimum (1 per Kafka broker)
- LoadBalancer for Ingress
- Persistent volumes for Kafka and PostgreSQL

**Monitoring:**
- Prometheus + Grafana
- Jaeger for tracing
- Elasticsearch + Kibana for logs (optional)

---

## Related Documentation

- [ADR-0015: Microservices Architecture](../adr/0015-microservices-architecture.md)
- [ADR-0016: Kubernetes Deployment Strategy](../adr/0016-kubernetes-deployment-strategy.md)
- [ADR-0014: OpenTelemetry from Day 1](../adr/0014-opentelemetry-from-day-1.md)
- [Security Architecture](./security-architecture.md)
- [Implementation Guide](../implementation/implementation-guide.md)

---

**Last Updated**: 2025-10-16
