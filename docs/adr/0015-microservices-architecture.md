# ADR-0015: Event-Driven Microservices with Kafka

## Context
Webhook work must finish in <10s; AI review is slow. A monolith blocked on AI would time out. Different parts have different resource profiles.

## Decision
Split into services and offload via Kafka: webhook (fast ingress) → context (fetch/validate diff) → review (AI) → integration (post comment).

## Consequences
- ✅ Webhook responds immediately; failures isolated; each part can scale independently (even in dev).
- ❌ More moving parts (Kafka + services), harder debugging than a monolith; overkill for small scope but acceptable for learning/experimentation.
