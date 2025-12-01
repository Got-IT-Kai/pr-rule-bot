# ADR-0021: Diff Validation and Skip Strategy

## Context
AI review is costly. Many diffs are not reviewable or not worth AI cycles (binary files, pure renames, permission-only changes, empty/HTML/JSON responses). We need a cheap gate before invoking AI.

## Decision
Validate diffs and skip non-reviewable cases using `DiffValidator` in context-service. If validation returns SKIP, emit `context.collected` with status SKIPPED and no diff; review-service does not run.

## Consequences
- ✅ Saves AI calls and time on non-reviewable changes; protects against bad responses.
- ❌ Skip reasons are generic in downstream comments; detailed reason not propagated.
- ❌ Large-but-valid diffs still proceed; Kafka/message size not enforced here.

## When to revisit
- If PR authors need specific skip reasons, propagate validation reason in events/comments.
- If size limits are required, add explicit byte/token checks before publishing.
