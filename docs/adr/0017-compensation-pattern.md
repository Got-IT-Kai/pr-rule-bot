# ADR-0017: Compensation Instead of Retry for GitHub Comments

## Context
Posting PR comments to GitHub can fail (5xx, timeouts, rate limits). Automatic retries add latency, burn quota, and hide failure signals. For the current single-instance, local stack we prefer fast failure and visibility.

## Decision
Use a compensation path instead of retries: on failure, emit `CommentPostingFailedEvent` and ACK the message. No retry/backoff in the comment client.

## Consequences
- ✅ Visible failures via events/metrics; simple code path; no quota spent on bad retries.
- ❌ Transient errors are not auto-recovered; operators must replay externally if needed.

## Notes
- Retry filters still exist elsewhere to skip validation errors (e.g., InvalidDiffException) so validation issues do not loop.
- Future scaling could add DLT/replay if automatic recovery becomes necessary.
