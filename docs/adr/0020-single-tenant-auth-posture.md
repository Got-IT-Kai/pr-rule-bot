# ADR-0020: Single-Tenant PAT, No Service-to-Service Auth

## Context
Local, single-org scope: services run inside a trusted network. GitHub access uses a PAT. Adding service-to-service auth (mTLS/JWT) or GitHub App auth adds complexity without immediate benefit for this deployment model.

## Decision
- Use PAT for GitHub (single organization).
- No additional auth between internal services (assume trusted network).
- Harden later if scope changes.

## Consequences
- ✅ Minimal setup for local/dev use; fewer moving parts.
- ❌ Not safe for untrusted networks; no per-service identity or RBAC.
- ❌ PAT limits multi-tenant scenarios; rotation must be manual.

## When to revisit
If exposed beyond a trusted network or adding multi-tenant/org support, introduce GitHub App auth and service-to-service authentication (mTLS/JWT).
