# ADR-0004: Bot Identity Management

**Date:** 2025-10-05
**Status:** Proposed

## Context

The current duplicate review detection logic has a critical flaw: it checks if ANY review exists on a PR, not specifically if THIS bot has already reviewed. This causes two problems:

1. **Legitimate human reviews block bot reviews:** If a human reviewer has already reviewed the PR, the bot will skip adding its automated review
2. **Bot identity is ambiguous:** No clear way to identify which reviews were created by the bot vs humans

Current implementation in `GitHubReviewService.java:50-62` checks for any review:

```java
.map(reviews -> reviews.isArray() && !reviews.isEmpty())  // Wrong: checks ANY review
```

This was originally added to prevent API cost abuse from repeated reviews, but the implementation is too broad.

**Related Issue:** Code Review CI-3 (High)
**Component:** `GitHubReviewService.java`

## Decision

Implement proper bot identity management to distinguish bot-generated reviews from human reviews.

**Strategy:**
- **Primary:** Filter reviews by GitHub user identity (check user type and login)
- **Fallback:** Add HTML comment marker in review body for resilience

**Bot identification criteria:**
1. User type is "Bot" AND login ends with "[bot]" (e.g., `github-actions[bot]`)
2. User login matches configured bot username (for PAT authentication)
3. Review body contains hidden marker comment (backup)

## Consequences

### Positive

- Bot reviews no longer blocked by human reviews
- Humans can review without preventing automated reviews
- Better audit trail of bot vs human reviews
- Prevents duplicate bot reviews correctly
- Supports future multi-bot scenarios
- Works across different authentication methods (Actions, GitHub App, PAT)

### Negative

- Must fetch and parse all reviews (not just count)
- Slightly higher GitHub API usage
- Need to handle bot identity changes when switching auth methods
- Must maintain marker format if used as fallback

## Alternatives Considered

### Alternative 1: Body marker only

**Description:** Search for unique marker string in review bodies

**Pros:**
- Independent of GitHub user identity
- Works across all authentication methods

**Cons:**
- Brittle (marker could be accidentally included by humans)
- Review body can be edited to remove marker
- Relies on string matching

**Why rejected:** Too fragile as sole mechanism. Better as fallback.

### Alternative 2: PR labels

**Description:** Add "ai-reviewed" label after bot review

**Pros:**
- Simple boolean flag
- Visible in PR UI
- Easy to query

**Cons:**
- Labels can be manually removed
- Requires additional API call
- Doesn't track multiple reviews on same PR
- Not semantically correct use of labels

**Why rejected:** Labels are user-facing metadata, not review tracking mechanism.

### Alternative 3: External database

**Description:** Store reviewed PR IDs in application database

**Pros:**
- Complete control over tracking
- Can store additional metadata
- Fast lookups

**Cons:**
- Requires database infrastructure
- State synchronization complexity
- Doesn't work across multiple deployments
- Data can become stale or orphaned

**Why rejected:** Over-engineered; GitHub API already provides this data.

## Implementation Strategy

### Bot detection logic

```
For each review in PR reviews:
  1. Check if user.type == "Bot" AND user.login.endsWith("[bot]")
     → GitHub Actions or GitHub App bot

  2. Check if user.login == configured_bot_username
     → PAT-based authentication

  3. Check if review.body contains "<!-- ai-review-bot:v1: -->"
     → Fallback marker

  If any match: This is a bot review
```

### Configuration

```yaml
# application.yml
github:
  bot-username: ${GITHUB_BOT_USERNAME:github-actions[bot]}
```

### Review marker format

```html
<!-- ai-review-bot:v1:2025-10-05T10:00:00Z -->
```

Hidden HTML comment, includes:
- Version (v1)
- Timestamp (for debugging)

### Testing approach

- Verify detection of GitHub Actions bot reviews
- Verify detection of GitHub App bot reviews
- Verify detection of PAT-based bot reviews
- Verify marker detection as fallback
- Ensure human reviews don't match any criteria

## Bot Identity Scenarios

| Auth Method | User Login | User Type | Detection Method |
|-------------|------------|-----------|------------------|
| GitHub Actions | `github-actions[bot]` | `Bot` | Type + login suffix |
| GitHub App | `my-app[bot]` | `Bot` | Type + login suffix |
| Personal Access Token | `ai-reviewer` | `User` | Configured username |
| Future: Multiple bots | Various | Various | Marker in body |

## Implementation Tasks

1. Update review detection logic to filter by user identity
2. Add configuration for bot username (with default)
3. Implement marker-based fallback detection
4. Write comprehensive tests (Actions, App, PAT scenarios)
5. Deploy to staging and verify with real PRs
6. Monitor false positives/negatives
7. Deploy to production

## Monitoring

Track:
- Bot review detection success rate
- False positives (human reviews detected as bot)
- False negatives (bot reviews not detected)

## References

- [GitHub API: List Reviews](https://docs.github.com/en/rest/pulls/reviews#list-reviews-for-a-pull-request)
- [GitHub API: Review Object](https://docs.github.com/en/rest/pulls/reviews#review-object)
- [GitHub Actions Bot User](https://docs.github.com/en/actions/security-guides/automatic-token-authentication)
- Code Review Report: [2025-10-05 Comprehensive Review](../code-review/2025-10-05-comprehensive-review.md#ci-3-duplicate-review-detection-flaw-high)
- Related: [ADR-0003: Webhook Security](./0003-webhook-security.md)
