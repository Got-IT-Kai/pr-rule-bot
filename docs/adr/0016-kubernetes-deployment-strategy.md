# ADR-0016: Kubernetes Deployment Strategy

**Date**: 2025-10-16
**Status**: Proposed

---

## Context

With microservices architecture adopted (ADR-0015), we need a robust deployment strategy that ensures:

**Reliability requirements:**
- Zero-downtime deployments
- Automatic failure recovery
- Service health monitoring
- Resource isolation

**Scaling requirements:**
- Auto-scaling based on load
- Resource limits to prevent resource exhaustion
- Independent scaling per service

**Operational requirements:**
- Simple rollback mechanism
- Canary deployments for risky changes
- Configuration management without rebuild
- Secret management

**Current challenges without orchestration:**
- Manual service management (start, stop, restart)
- No automatic health checks
- No auto-scaling
- Difficult service discovery
- Manual load balancing

## Decision

Use **Kubernetes** as the container orchestration platform with the following deployment strategy:

**Core components:**
- Deployments for stateless services
- StatefulSets for stateful components (Kafka, PostgreSQL)
- HorizontalPodAutoscaler (HPA) for auto-scaling
- ConfigMaps and Secrets for configuration
- Services for internal communication
- Ingress for external access

**Deployment strategy:**
- Rolling updates (default)
- Canary deployments for risky changes
- Pod Disruption Budgets (PDB) for availability
- Health checks (liveness, readiness)

### Resource Allocation Strategy

**Service classification:**

**1. Latency-sensitive (webhook-service):**
- Priority: High
- Resources: requests=limits (guaranteed QoS)
- Scaling: Aggressive (based on request rate)

**2. CPU-intensive (review-service):**
- Priority: Medium
- Resources: High CPU, high memory
- Scaling: Moderate (based on queue depth)

**3. I/O-bound (context-service, integration-service):**
- Priority: Medium
- Resources: Low CPU, moderate memory
- Scaling: Moderate (based on queue depth)

**4. Lightweight (policy-service):**
- Priority: Medium
- Resources: Low CPU, low memory
- Scaling: Conservative

**Resource requests vs limits:**
```yaml
# Guaranteed QoS (latency-sensitive)
resources:
  requests:
    cpu: 100m
    memory: 256Mi
  limits:
    cpu: 100m      # Same as requests
    memory: 256Mi  # Same as requests

# Burstable QoS (normal services)
resources:
  requests:
    cpu: 250m
    memory: 512Mi
  limits:
    cpu: 1000m     # Allow bursting
    memory: 1Gi    # Allow bursting
```

### Health Check Strategy

**Liveness probe**: Detect if container is healthy
- Failure action: Restart container
- Check: Application-specific health endpoint

**Readiness probe**: Detect if container is ready for traffic
- Failure action: Remove from service load balancer
- Check: Database connection, Kafka connection, dependencies

**Startup probe**: Allow slow-starting containers
- Use for services with long initialization (context-service loading ADRs)

**Example:**
```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10
  failureThreshold: 3

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 20
  periodSeconds: 5
  failureThreshold: 3

startupProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 0
  periodSeconds: 10
  failureThreshold: 30  # 5 minutes max startup time
```

### Auto-Scaling Strategy

**HorizontalPodAutoscaler:**

**webhook-service:**
```yaml
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

**review-service:**
```yaml
minReplicas: 1
maxReplicas: 5
metrics:
- type: Resource
  resource:
    name: cpu
    target:
      type: Utilization
      averageUtilization: 80
- type: Pods
  pods:
    metric:
      name: kafka_consumer_lag
    target:
      type: AverageValue
      averageValue: "100"  # Scale up if lag > 100 per pod
```

**context-service:**
```yaml
minReplicas: 1
maxReplicas: 5
metrics:
- type: Pods
  pods:
    metric:
      name: kafka_consumer_lag
    target:
      type: AverageValue
      averageValue: "50"
```

### Deployment Update Strategy

**Rolling Update (default):**
```yaml
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxUnavailable: 0      # No downtime
    maxSurge: 1            # One extra pod during update
```

**Process:**
1. Create new pod with new version
2. Wait for readiness probe to pass
3. Add to service load balancer
4. Remove old pod from load balancer
5. Terminate old pod
6. Repeat for remaining pods

**Canary Deployment (risky changes):**
```yaml
# Initial deployment (10% traffic)
replicas: 1
labels:
  version: canary

# Existing deployment (90% traffic)
replicas: 9
labels:
  version: stable

# Service routes to both
selector:
  app: review-service  # No version selector
```

**Process:**
1. Deploy canary with 1 replica (10% traffic)
2. Monitor metrics for 1 hour
3. If metrics good: Scale canary to 10, scale old to 0
4. If metrics bad: Scale canary to 0, rollback

### Pod Disruption Budget

**Maintain availability during voluntary disruptions:**

```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: webhook-service-pdb
spec:
  minAvailable: 1  # Always keep at least 1 pod running
  selector:
    matchLabels:
      app: webhook-service
```

**Purpose:**
- Prevent kubectl drain from removing all pods
- Maintain service during node upgrades
- Ensure availability during cluster operations

### Configuration Management

**ConfigMaps for non-sensitive config:**
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: webhook-service-config
data:
  KAFKA_BOOTSTRAP_SERVERS: kafka:9092
  LOG_LEVEL: INFO
  OTEL_EXPORTER_OTLP_ENDPOINT: http://otel-collector:4318
```

**Secrets for sensitive data:**
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: github-secrets
type: Opaque
data:
  webhook-secret: <base64-encoded>
  app-private-key: <base64-encoded>
```

**Mounting:**
```yaml
env:
- name: GITHUB_WEBHOOK_SECRET
  valueFrom:
    secretKeyRef:
      name: github-secrets
      key: webhook-secret
- name: KAFKA_BOOTSTRAP_SERVERS
  valueFrom:
    configMapKeyRef:
      name: webhook-service-config
      key: KAFKA_BOOTSTRAP_SERVERS
```

### Namespace Strategy

**Single namespace:**
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: ai-code-reviewer
```

**Rationale:**
- All services in same namespace for simplicity
- Internal communication via service DNS (e.g., kafka.ai-code-reviewer.svc.cluster.local)
- Easier RBAC management

## Consequences

### Positive

**Zero-Downtime Deployment**
- Rolling updates ensure no service interruption
- Readiness probes prevent traffic to unhealthy pods
- Rollback in seconds if issues detected

**Automatic Failure Recovery**
- Liveness probe detects failures, restarts container
- Node failure: Pods rescheduled to healthy nodes
- No manual intervention required

**Auto-Scaling**
- HPA scales based on metrics
- Handles traffic spikes automatically
- Reduces costs during low traffic

**Resource Efficiency**
- Resource requests guarantee minimum resources
- Resource limits prevent resource exhaustion
- QoS classes ensure priority scheduling

**Operational Simplicity**
- Declarative configuration (YAML)
- GitOps compatible (Flux, ArgoCD)
- Rollback is reapplying previous YAML

**Configuration Management**
- ConfigMaps: Update config without rebuild
- Secrets: Centralized secret management
- Environment-specific configs easy to manage

### Negative

**Kubernetes Complexity**
- Steep learning curve
- Complex networking (Services, Ingress, NetworkPolicies)
- Debugging distributed system

**Infrastructure Requirements**
- Minimum 3 nodes for Kafka high availability
- Control plane overhead (~1GB RAM, ~1 CPU)
- Storage for persistent volumes

**Operational Overhead**
- Cluster upgrades and maintenance
- Monitoring Kubernetes metrics
- Certificate management (if using cert-manager)

**Cost**
- Cloud provider: ~$150-300/month for 3-node cluster
- Self-hosted: Hardware + maintenance

**Configuration Sprawl**
- Many YAML files to manage
- Versioning and tracking changes
- Potential for drift between environments

## Alternatives Considered

### Alternative 1: Docker Compose

Run all services with Docker Compose.

**Pros:**
- Simple setup
- No Kubernetes complexity
- Low resource overhead

**Cons:**
- No auto-scaling
- No automatic failure recovery
- Manual service discovery
- Single-node only (no high availability)
- Difficult to manage at scale

**Why rejected:**
Doesn't meet reliability and scaling requirements for production.

### Alternative 2: Docker Swarm

Lightweight container orchestration.

**Pros:**
- Simpler than Kubernetes
- Built into Docker
- Auto-scaling and health checks

**Cons:**
- Less mature ecosystem
- Fewer features than Kubernetes
- Declining community support
- Limited tooling

**Why rejected:**
Kubernetes has better ecosystem, tooling, and community support.

### Alternative 3: Managed Kubernetes (EKS, GKE, AKS)

Use cloud provider's managed Kubernetes.

**Pros:**
- No control plane management
- Integrated with cloud services
- Automatic upgrades

**Cons:**
- Vendor lock-in
- Higher cost ($70-150/month for control plane alone)
- Less control over cluster configuration

**Why deferred:**
Start with self-hosted (Minikube/k3s) for learning and cost. Can migrate to managed later.

### Alternative 4: Nomad

HashiCorp's orchestrator.

**Pros:**
- Simpler than Kubernetes
- Multi-workload support (containers, VMs, binaries)

**Cons:**
- Smaller ecosystem
- Less mature than Kubernetes
- Fewer integrations

**Why rejected:**
Kubernetes is industry standard with better tooling and community.

## Implementation

**High-level steps:**

**1. Cluster Setup:**
- Install Kubernetes (Minikube for local, k3s/kubeadm for production)
- Configure kubectl context
- Create namespace

**2. Deploy Infrastructure:**
- Kafka StatefulSet (3 brokers, KRaft mode)
- PostgreSQL StatefulSets (5 instances, one per service)
- Redis Deployment
- OpenTelemetry Collector Deployment

**3. Deploy Services:**
- Create ConfigMaps and Secrets
- Apply Deployment manifests
- Apply Service manifests
- Apply HPA manifests
- Apply PDB manifests

**4. Configure Ingress:**
- Install ingress controller (nginx-ingress)
- Create Ingress resource
- Configure TLS (cert-manager)

**5. Monitoring:**
- Deploy Prometheus Operator
- Configure ServiceMonitors
- Deploy Grafana with dashboards

**Note:** Detailed manifests in `/infrastructure/k8s/` directory.

## References

**Related ADRs:**
- [ADR-0015: Microservices Architecture](0015-microservices-architecture.md)
- [ADR-0014: OpenTelemetry from Day 1](0014-opentelemetry-from-day-1.md)

**Kubernetes:**
- [Kubernetes Best Practices](https://kubernetes.io/docs/concepts/configuration/overview/)
- [Production Best Practices](https://learnk8s.io/production-best-practices)
- [HPA Documentation](https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/)

**Deployment Strategies:**
- [Rolling Updates](https://kubernetes.io/docs/tutorials/kubernetes-basics/update/update-intro/)
- [Canary Deployments](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#canary-deployments)

---

**Last Updated**: 2025-10-16
