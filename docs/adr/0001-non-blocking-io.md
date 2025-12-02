# ADR-0001: Non-Blocking I/O (WebFlux) for Webhooks/GitHub Calls

## Context
Webhook responses must be quick; GitHub/AI calls are slow/IO-bound. Blocking would exhaust threads and risk webhook timeouts.

## Decision
Use Spring WebFlux and WebClient for external HTTP paths (webhook-service, integration-service; also fits review-service AI calls).

## Consequences
- ✅ Handles more concurrent requests with fewer threads; safe against webhook timeouts.
- ❌ Reactive model is harder to debug; discipline needed to avoid blocking.
