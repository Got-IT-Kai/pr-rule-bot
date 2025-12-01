# ADR-0018: In-Memory Idempotency (Caffeine)

## Context
Webhooks and Kafka can deliver duplicates. We need a cheap guard to avoid duplicate reviews/comments without adding infrastructure.

## Decision
Use in-memory Caffeine caches (24h TTL, 10K entries) per service to short-circuit repeats (deliveryId/eventId/reviewId).

## Consequences
- ✅ Zero external deps, fast lookups, bounded memory, fine for single-instance dev stack.
- ❌ Lost on restart; not safe for multi-instance; no audit of deduped items.

## Future
If scaling, replace with shared store (Redis/DB) or Kafka compacted topic plus startup rewind.
