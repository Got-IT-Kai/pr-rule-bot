# ADR-0017: Compensation Pattern and DLT for Failed Events

## Context
Posting PR comments to GitHub can fail (5xx, timeouts, rate limits). Event processing can also fail due to transient errors. We need a strategy for handling failures without blocking the pipeline or losing visibility.

## Decision
Use a compensation path instead of retries:
1. On failure, forward event to DLT (Dead Letter Topic)
2. If DLT publish succeeds → ACK original message
3. If DLT publish fails → ACK anyway and track via metrics
4. Emit `CommentPostingFailedEvent` for GitHub comment failures

No retry/backoff in the comment client or DLT publisher.

## Consequences
- ✅ Visible failures via DLT + events/metrics; simple code path; no quota spent on bad retries
- ✅ Failed events preserved in DLT for manual replay
- ❌ Transient errors are not auto-recovered; operators must replay externally if needed
- ❌ DLT publish failures result in message loss (tracked by metrics)

## Notes
- Retry filters still exist elsewhere to skip validation errors (e.g., InvalidDiffException) so validation issues do not loop.
- DLT implementation uses in-memory idempotency; upgrade to Redis for multi-instance deployments.
