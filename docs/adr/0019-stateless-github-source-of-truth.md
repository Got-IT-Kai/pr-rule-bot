# ADR-0019: Stateless Services, GitHub as Source of Truth

## Context
Reviews run asynchronously and must stay fast/light. Persisting diffs, contexts, or review history would add infra and latency. GitHub already holds PR state and review comments.

## Decision
Stay stateless: do not add a database; rely on GitHub for PR state and comments. Kafka carries events; services do transient work and exit.

## Consequences
- ✅ Simple ops (no DB), faster response, fewer failure modes.
- ❌ No history/analytics/rewind; harder to replay or audit past reviews.
- ❌ Reprocessing depends on re-emitting events or re-fetching from GitHub.

## When to revisit
If we need historical analytics, audit trails, or automatic replay, add a lightweight store (e.g., Postgres/Redis) and evolve schemas accordingly.
