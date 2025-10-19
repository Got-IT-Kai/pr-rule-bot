# ADR-0015: Microservices Architecture with Kafka and Kubernetes

**Date**: 2025-10-16
**Status**: Proposed

---

## Context

The system must handle varying workload patterns with different resource requirements:

**Resource usage patterns:**
- **Webhook handling**: Immediate response required (< 5 seconds), lightweight processing (signature verification, JSON parsing)
- **Context collection**: I/O bound (GitHub GraphQL queries, file reads), network latency dominant, low CPU usage
- **AI review**: CPU/Memory intensive (large diff processing, LLM response parsing), high resource consumption
- **Policy evaluation**: Lightweight computation (rule matching, pattern detection), minimal resources

**Current problems with monolithic deployment:**
- Webhook timeout when AI processing delays response
- Cannot scale webhook handling independently during PR surge
- AI API failures cascade to entire system
- Policy rule updates require full system redeployment
- Resource contention between lightweight and heavy operations

**Failure isolation requirements:**
- AI provider outage should not block webhook reception
- Context collection failure should not prevent policy evaluation
- Database issues in one component should not affect others

**Deployment requirements:**
- Update policy rules without restarting AI service
- Deploy AI model changes without affecting webhook handling
- Independent service versioning and rollback

**Scaling requirements:**
- Scale webhook service during peak PR hours
- Scale review service when AI processing backlog grows
- Scale context service when repository history is large

## Decision

Adopt **Microservices Architecture** with **Event-Driven Architecture (Kafka)** and **Kubernetes** orchestration.

**Architecture principles:**
- Service per bounded context (webhook, context, policy, review, integration)
- Asynchronous communication via Kafka
- Independent scaling and deployment
- Failure isolation with circuit breakers

### Service Boundaries

```
GitHub Webhook
      │
      ▼
┌─────────────────┐
│ Webhook Service │  Lightweight, immediate response
│ • Verify sig    │  Resources: 256MB RAM, 0.1 CPU
│ • Parse event   │  Latency: < 100ms
│ • Publish       │
└────────┬────────┘
         │
         ▼
    ┌────────┐
    │ Kafka  │  Decoupling, backpressure, retry
    └────┬───┘
         │
    ┌────┴────┬──────────┬──────────┐
    ▼         ▼          ▼          ▼
┌────────┐ ┌──────┐  ┌────────┐ ┌──────────┐
│Context │ │Policy│  │Review  │ │Integration│
│Service │ │Svc   │  │Service │ │Service    │
│        │ │      │  │        │ │           │
│I/O     │ │Light │  │CPU     │ │I/O bound  │
│bound   │ │weight│  │intensive│ │          │
│512MB   │ │256MB │  │2GB     │ │512MB      │
└────────┘ └──────┘  └────────┘ └──────────┘
```

**Separation rationale:**

**1. Webhook Service**
- **Why separate**: Must respond immediately, GitHub timeout at 10 seconds
- **Problem solved**: Decouples receipt from processing, prevents timeout
- **Resource profile**: Low CPU, low memory, high concurrency

**2. Context Service**
- **Why separate**: I/O bound operations (GraphQL queries, file reads)
- **Problem solved**: Network latency doesn't block other services
- **Resource profile**: Low CPU, moderate memory, moderate concurrency

**3. Policy Service**
- **Why separate**: Lightweight computation, frequently updated rules
- **Problem solved**: Deploy rule changes without AI service restart
- **Resource profile**: Low CPU, low memory, high throughput

**4. Review Service**
- **Why separate**: CPU/Memory intensive, LLM processing
- **Problem solved**: Scale independently for AI workload, isolate AI API failures
- **Resource profile**: High CPU, high memory, low concurrency

**5. Integration Service**
- **Why separate**: GitHub API rate limits, I/O bound
- **Problem solved**: Rate limit handling isolated, doesn't affect processing
- **Resource profile**: Low CPU, moderate memory, controlled rate

### Kafka Event Flow

**Why Kafka:**
- Asynchronous processing: Webhook responds immediately, processing happens later
- Backpressure: Slow consumers don't block producers
- Retry: Failed processing automatically retried
- Ordering: Events processed in order per partition (repository-based partitioning)

**Event topics:**
```
pull-request.opened       (webhook → all consumers)
context.collected         (context → review)
policy.evaluated          (policy → review)
ai.reviewed              (review → integration)
review.completed         (review → integration)
```

**Flow:**
1. Webhook publishes event, responds 200 OK immediately
2. Services consume in parallel (context, policy)
3. Review service waits for context + policy results
4. Integration service updates GitHub after completion

### Kubernetes Orchestration

**Why Kubernetes:**
- Horizontal auto-scaling: Scale services based on CPU/memory/queue depth
- Rolling updates: Zero-downtime deployment per service
- Health checks: Automatic restart on failure
- Service discovery: Services find each other via DNS
- Resource limits: Prevent one service from starving others

**Deployment strategy:**
- Deployment: Stateless services (webhook, context, policy, review, integration)
- StatefulSet: Stateful components (Kafka, PostgreSQL)
- HPA: Auto-scale based on metrics
- PDB: Maintain minimum replicas during updates

**Resource allocation per service:**
```yaml
webhook-service:
  requests: { cpu: 100m, memory: 256Mi }
  limits:   { cpu: 500m, memory: 512Mi }
  replicas: 2-10 (HPA based on request rate)

context-service:
  requests: { cpu: 250m, memory: 512Mi }
  limits:   { cpu: 1000m, memory: 1Gi }
  replicas: 1-5 (HPA based on queue depth)

policy-service:
  requests: { cpu: 100m, memory: 256Mi }
  limits:   { cpu: 500m, memory: 512Mi }
  replicas: 1-3 (HPA based on queue depth)

review-service:
  requests: { cpu: 500m, memory: 2Gi }
  limits:   { cpu: 2000m, memory: 4Gi }
  replicas: 1-5 (HPA based on AI API latency)

integration-service:
  requests: { cpu: 250m, memory: 512Mi }
  limits:   { cpu: 1000m, memory: 1Gi }
  replicas: 1-3 (HPA based on GitHub API rate)
```

## Consequences

### Positive

**Independent Scaling**
- Webhook service scales during PR surge (morning standup, release day)
- Review service scales when AI processing is slow
- Context service scales for large repository history
- Resource optimization: Pay only for what each service needs

**Zero-Downtime Deployment**
- Update policy rules without restarting AI service
- Deploy AI model changes independently
- Rollback individual services on error
- Rolling update strategy: 1 pod at a time

**Failure Isolation**
- AI API outage: Webhook still accepts PRs, queued for later
- Context service failure: Policy evaluation continues
- Integration service failure: Review results preserved, retried later
- Circuit breaker per service boundary

**Performance Optimization**
- Webhook responds in < 100ms (no processing in request path)
- Parallel processing: Context + Policy run simultaneously
- Backpressure: Kafka prevents memory exhaustion
- Resource allocation matched to workload

**Operational Benefits**
- Clear service ownership and SLIs
- Independent monitoring per service
- Isolated performance tuning
- Simplified troubleshooting (service boundaries match problem domains)

### Negative

**Operational Complexity**
- 5 services to deploy, monitor, and debug
- Kubernetes cluster management
- Distributed tracing required for debugging
- Network communication overhead (~10-50ms per hop)

**Development Overhead**
- Service contracts and versioning
- Event schema management (Avro)
- Local development requires Docker Compose (all services + Kafka)
- Integration testing complexity

**Infrastructure Costs**
- More resources: 5 services + Kafka (3 brokers) + PostgreSQL (5 instances) + K8s overhead
- Estimated: ~20GB RAM, ~8 vCPU total
- Monitoring: Prometheus + Jaeger + Grafana

**Data Consistency**
- Eventual consistency (Kafka async)
- No ACID transactions across services
- Idempotency required for all event handlers
- Duplicate message handling needed

**Latency Trade-off**
- Monolith: ~1-2 seconds end-to-end
- Microservices: ~3-5 seconds (Kafka + network hops)
- Acceptable for code review (non-interactive)

## Alternatives Considered

### Alternative 1: Modular Monolith

Single deployment with internal module boundaries.

**Pros:**
- Simpler deployment (one artifact)
- Lower latency (in-process calls)
- Easier debugging (single process)
- Lower infrastructure costs

**Cons:**
- Webhook timeout during AI processing (cannot separate fast/slow paths)
- All-or-nothing deployment (policy update requires full restart)
- No failure isolation (AI API failure crashes entire system)
- Cannot scale components independently (waste resources scaling everything)
- Resource contention (webhook and AI compete for memory)

**Why rejected:**
Webhook timeout is critical issue. GitHub requires response within 10 seconds, but AI review can take 30+ seconds for large PRs.

### Alternative 2: Monolith + Background Jobs

Monolith with async job queue (Redis Queue, Sidekiq).

**Pros:**
- Simpler than microservices
- Asynchronous processing available
- Webhook can respond immediately

**Cons:**
- Still cannot scale components independently
- Single deployment for all changes
- No failure isolation (job failure can crash main process)
- Job queue is single point of failure

**Why rejected:**
Doesn't solve scaling problem. Cannot allocate more resources to AI processing without scaling entire application.

### Alternative 3: Serverless (AWS Lambda)

Functions per operation, event-driven via SQS/EventBridge.

**Pros:**
- No infrastructure management
- Auto-scaling built-in
- Pay per execution

**Cons:**
- Cold start: 5-10 seconds (violates webhook response time)
- 15-minute timeout (insufficient for large PR analysis)
- Vendor lock-in (AWS-specific)
- Kafka consumer pattern difficult with Lambda
- State management complexity

**Why rejected:**
Cold start latency unacceptable for webhook handling. 15-minute timeout insufficient for future enhancements (RAG, vector search).

### Alternative 4: Microservices without Kubernetes

Services on VMs with manual orchestration.

**Pros:**
- No Kubernetes complexity
- Direct control over deployment

**Cons:**
- No auto-scaling (manual intervention during load spikes)
- No automatic health checks and restarts
- Manual service discovery (IP management)
- No rolling updates (downtime during deployment)

**Why rejected:**
Manual operations don't meet production reliability requirements. Auto-scaling essential for varying PR load.

## Implementation

**High-level components:**

**Service Architecture:**
- Domain layer: Business logic, entities, value objects
- Application layer: Use cases, port interfaces
- Infrastructure layer: Adapters (Kafka, PostgreSQL, external APIs)

**Kafka Integration:**
- Event schemas: Avro format with schema registry
- Partitioning: By repository (maintains ordering per repo)
- Consumer groups: One group per service
- Exactly-once semantics: Idempotent event handlers

**Kubernetes Resources:**
- Deployments: Webhook, Context, Policy, Review, Integration services
- StatefulSets: Kafka (KRaft mode, 3 brokers), PostgreSQL (per service)
- Services: ClusterIP for inter-service communication
- Ingress: External access with TLS termination
- HPA: Auto-scaling based on CPU, memory, queue depth
- PDB: Maintain 1 replica minimum during updates

**Kafka (KRaft Mode):**
- No Zookeeper required
- 3 brokers for high availability
- Replication factor: 2
- Partition count: 3 per topic (repository-based sharding)

**Observability:**
- OpenTelemetry: Distributed tracing across services
- Metrics: Request rate, latency, error rate per service
- Logs: Structured JSON with trace ID correlation
- Dashboards: Grafana with service-specific panels

**Note:** Detailed implementation in `/docs/implementation/implementation-guide.md`

## References

**Related ADRs:**
- [ADR-0001: Non-Blocking I/O](0001-non-blocking-io.md)
- [ADR-0014: OpenTelemetry from Day 1](0014-opentelemetry-from-day-1.md)
- [ADR-0016: Kubernetes Deployment Strategy](0016-kubernetes-deployment-strategy.md)

**Microservices:**
- [Microservices Patterns](https://microservices.io/patterns/index.html)
- [Building Microservices](https://samnewman.io/books/building_microservices_2nd_edition/)

**Kafka:**
- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [Kafka KRaft Mode](https://kafka.apache.org/documentation/#kraft)

**Kubernetes:**
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Kubernetes Patterns](https://www.redhat.com/en/resources/oreilly-kubernetes-patterns-book)

---

**Last Updated**: 2025-10-16
