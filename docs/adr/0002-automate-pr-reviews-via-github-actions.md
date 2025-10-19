# ADR-0002: Automate PR Reviews via GitHub Actions and Managed AI API

**Date:** 2025-08-14
**Status:** Implemented
**Implementation Date:** 2025-10-05

## Context

The goal is to provide automated code review comments on pull requests to improve code quality and maintain consistency. The review should only be triggered after CI checks (build, tests) have passed, ensuring the review is performed on stable, valid code.

Initial considerations included using a self-hosted Ollama server for running LLM models locally. However, this approach posed significant challenges:

**Self-hosted Ollama challenges:**
- **Availability:** Requires maintaining a dedicated, managed server (not currently available)
- **Synchronization:** Self-hosted server might not run the latest model version
- **CI Integration:** CI environment needs network access to external server
- **Performance:** Limited by server hardware resources
- **Maintenance:** Requires ongoing updates and monitoring

The automated review system needs to:
- Trigger automatically on PR events
- Analyze code changes efficiently
- Post review comments back to GitHub
- Handle secrets securely
- Work reliably in CI environment

## Decision

Use GitHub Actions to automate PR reviews with a managed, serverless AI API:

1. **Trigger on PR events:** Workflow runs on `pull_request` events (opened, synchronize)
2. **Managed AI provider:** Use Google Gemini API instead of self-hosting
3. **Secure authentication:** Store API keys in GitHub Encrypted Secrets
4. **Review workflow:** Fetch PR diff → Send to AI API → Post review comment
5. **CI integration:** Run as part of GitHub Actions workflow

## Consequences

### Positive

- **No Server Maintenance:** Eliminates operational overhead of managing dedicated AI model server
- **Always Uses Latest Logic:** Review logic defined in repository's workflow file, ensuring up-to-date prompts and process
- **Secure:** API keys and tokens managed by GitHub's secure infrastructure, not exposed in logs or source code
- **Simplified Architecture:** Entire review process self-contained within GitHub repository and Actions
- **Cost-effective:** Free tier sufficient for initial usage (1500 requests/month)
- **Fast response time:** Gemini API responds in 10-30 seconds vs minutes for self-hosted
- **Scalable:** No infrastructure bottlenecks, API scales automatically

### Negative

- **External Dependency:** Depends on availability and terms of third-party AI service (Google)
- **Potential Cost:** High usage may incur costs beyond free tier
- **Network Required:** CI must have internet access to reach API
- **Vendor Lock-in:** Switching providers requires workflow changes
- **API Rate Limits:** Subject to provider's rate limiting policies

## Alternatives Considered

### Alternative 1: Self-hosted Ollama Server

**Description:** Deploy and maintain dedicated server running Ollama for LLM inference

**Pros:**
- Complete control over infrastructure
- Data privacy (no external API calls)
- Predictable costs (hardware-based)
- Offline operation capability

**Cons:**
- Requires dedicated server infrastructure
- Ongoing maintenance burden
- Version synchronization issues
- Network configuration complexity
- Performance limited by hardware
- CI environment needs VPN/network access

**Why rejected:** Infrastructure overhead and maintenance burden outweigh benefits for early-stage project.

### Alternative 2: Ollama in GitHub Actions Container

**Description:** Run Ollama directly in GitHub Actions using Docker

**Pros:**
- No external server needed
- Runs in CI environment
- Free within Actions quota

**Cons:**
- Extremely slow (5-10 minutes per review)
- Frequent timeouts in CI
- Limited to 2-core CPU, 7GB RAM
- Models too large for available resources
- High failure rate (0% success in testing)

**Why rejected:** Performance unacceptable - see [Lesson 01: Ollama to Gemini Migration](../lessons/01-ollama-to-gemini-migration.md)

### Alternative 3: OpenAI GPT API

**Description:** Use OpenAI's GPT-4 API instead of Gemini

**Pros:**
- Mature, well-documented API
- High-quality code understanding
- Large context window

**Cons:**
- Higher cost than Gemini
- Smaller free tier
- More restrictive rate limits
- Privacy concerns with OpenAI

**Why rejected:** Gemini provides comparable quality at lower cost with better free tier.

### Alternative 4: Manual Review Only

**Description:** Rely solely on human code review

**Pros:**
- No external dependencies
- No API costs
- Human judgment and context

**Cons:**
- Inconsistent review quality
- Slower review turnaround
- Reviewer fatigue
- Misses mechanical issues
- Not scalable

**Why rejected:** Automation augments human review, not replaces it. Both are valuable.

## Implementation Strategy

### Workflow architecture

```
PR Event → GitHub Actions → Authenticate (WIF) → Fetch Diff → AI Review → Post Comment
```

**Key components:**
- GitHub Actions workflow triggered on PR events (opened, synchronize)
- Workload Identity Federation for GCP authentication (no stored credentials)
- Spring Boot CLI application for review execution
- Gemini API for code analysis
- GitHub API for PR interaction

### Security approach

**Authentication:**
- GitHub: OIDC-based Workload Identity Federation (keyless)
- Contributor restriction: Only run for repository collaborators
- Secrets management: GitHub encrypted secrets

**Required secrets:**
- `GCP_WIF_PROVIDER` - Workload identity provider resource name
- `GCP_SERVICE_ACCOUNT` - Service account email
- `GCP_PROJECT_ID` - GCP project ID

**Permissions:**
- `contents: read` - Checkout code
- `pull-requests: write` - Post review comments
- `id-token: write` - Generate OIDC tokens

### Review flow

1. PR opened/updated triggers workflow
2. Authenticate to GCP via OIDC token
3. Fetch unified diff from GitHub API
4. Send diff to Gemini API for analysis
5. Parse and format AI response
6. Post review comment to PR

## Migration from Ollama

**Migration completed:** 2025-10-05 (Commit: 4b10546)

**Key changes:**
1. Removed Ollama Docker setup from workflow
2. Added GCP Workload Identity Federation
3. Updated application to use Gemini client
4. Reduced timeout from 10 minutes to 5 minutes
5. Archived Ollama-related files

**Performance improvement:**
- Before: 5-10 minutes (frequent timeout)
- After: 10-30 seconds (99%+ success rate)

See: [Lesson 01: Ollama to Gemini Migration](../lessons/01-ollama-to-gemini-migration.md)

## Security Considerations

### Cost Abuse Prevention

**Risk:** Malicious actors could spam PRs to exhaust API quota and incur costs

**Mitigations implemented:**
1. **Duplicate review check:** Prevent repeated reviews on same PR
2. **Contributor restrictions:** Only run for repository collaborators
3. **Event filtering:** Only trigger on `opened` and `synchronize` (not `reopened`)

**Future enhancements:**
- Rate limiting per user/repository
- API usage monitoring and alerts
- Daily/monthly quota enforcement

See: [ADR-0003: Webhook Security](./0003-webhook-security.md)

## Monitoring and Metrics

**Track:**
- Review completion rate
- Average review time
- API usage and costs
- Failure rate and reasons
- Gemini API latency

**Alerts:**
- Review failures > 10% in 1 hour
- API cost exceeds budget threshold
- Gemini API errors > 5 in 10 minutes

## Cost Analysis

**Gemini Free Tier:**
- 1500 requests/month free
- ~50 reviews/day sustainable

**Estimated costs beyond free tier:**
- $0.02 per review (approximate)
- ~$30/month for 100 reviews/day

**ROI:**
- Catches bugs earlier in development
- Reduces human review time
- Improves code quality consistency
- Worth the investment for development productivity

## References

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Google Gemini API](https://ai.google.dev/docs)
- [Workload Identity Federation](https://cloud.google.com/iam/docs/workload-identity-federation)
- Related: [ADR-0001: Non-Blocking I/O](./0001-non-blocking-io.md)
- Related: [Lesson 01: Ollama to Gemini Migration](../lessons/01-ollama-to-gemini-migration.md)
- Related: [Lesson 04: GCP WIF Setup](../lessons/04-gcp-workload-identity-federation-setup.md)
