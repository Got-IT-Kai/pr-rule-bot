# ADR-0003: GitHub Webhook HMAC Validation

## Context
Webhook endpoint must reject forged payloads. GitHub signs payloads with `X-Hub-Signature-256` (HMAC-SHA256).

## Decision
Validate the HMAC for each webhook payload in webhook-service; reject if missing or mismatched.

## Consequences
- ✅ Blocks forged requests, standard GitHub practice, minimal overhead.
- ❌ Requires secret configured everywhere; logic is localized to this endpoint.
