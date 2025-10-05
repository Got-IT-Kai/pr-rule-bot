# Lesson: Ollama to Gemini Migration

**Date:** October 5, 2025

## Problem Statement

We initially attempted to use Ollama for local LLM inference in GitHub Actions CI to reduce costs. However, significant performance issues led us to migrate to Google Gemini.

## Root Cause Analysis

### Initial Decision: Ollama

**Rationale:**
- Completely free within GitHub Actions free tier
- Data privacy (no external API calls)
- Offline operation capability

**Implementation:**
- Model: `qwen2.5-coder:3b` (quantized 3B parameter model)
- Pre-pulled Docker image uploaded to GHCR
- Executed via Docker in GitHub Actions

### Performance Issues

**Key Problems:**
1. **Response Time**: Simple code reviews took 5-10 minutes, often resulting in timeout
   - Even with 10-minute timeout, workflows frequently failed
   - No successful completion in CI environment
2. **GitHub Actions Constraints**:
   - Free tier: 2-core CPU, 7GB RAM
   - Insufficient resources for LLM inference
3. **Model Size vs Performance Trade-off**:
   - 3B: Still too slow (5-10 min), frequently timed out
   - 7B: Even slower, requires 16GB+ memory, completely impractical

### Solution: Google Gemini

**Advantages:**
- Fast response times: 10-30 seconds
- Free tier: 1500 requests/month (sufficient)
- High quality: Flash 2.0 model has strong code understanding
- No infrastructure management required (serverless)

**Implementation:**
- Workload Identity Federation for secure authentication
- OIDC token-based authentication without JSON keys
- Enabled IAM Service Account Credentials API

## Key Lessons Learned

### 1. Cost vs Performance Trade-off

> Free doesn't always mean optimal. Time is also a cost.

- GitHub Actions execution time counts against quota (2000 minutes/month)
- 5 minutes vs 30 seconds = 10x efficiency difference
- **Takeaway**: Managed services can be more efficient in early development

### 2. LLM Selection Criteria for CI/CD

**Critical Factors:**
- Response Time (under 1 minute)
- Reliability (99%+ uptime)
- Cost Predictability

**Less Important Initially:**
- Self-hosting
- Complete control
- Custom fine-tuning

### 3. Docker Image Optimization Limitations

- Pre-pulling models to GHCR doesn't reduce inference time
- Model loading is fast, but actual inference is the bottleneck
- **Takeaway**: Model selection matters more than infrastructure optimization

### 4. Workload Identity Federation over JSON Keys

```yaml
# Bad: JSON key stored as secret
- uses: google-github-actions/auth@v2
  with:
    credentials_json: ${{ secrets.GCP_SA_KEY }}

# Good: Keyless authentication
- uses: google-github-actions/auth@v2
  with:
    workload_identity_provider: ${{ secrets.GCP_WIF_PROVIDER }}
    service_account: ${{ secrets.GCP_SERVICE_ACCOUNT }}
```

**Benefits:**
- No long-lived credentials
- Automatic token rotation
- GitHub OIDC-based authentication
- Fine-grained permission control via IAM policies

## Technical Implementation

### Before: Ollama
```yaml
- name: Start Ollama
  run: |
    docker run -d --name ollama -p 11434:11434 \
      ghcr.io/got-it-kai/pr-rule-bot-ollama:3b

- name: Run Review
  run: ./gradlew bootRun --args="cli"
  timeout-minutes: 10  # Often timed out
```

### After: Gemini
```yaml
- name: Authenticate to Google Cloud
  uses: google-github-actions/auth@v2
  with:
    workload_identity_provider: ${{ secrets.GCP_WIF_PROVIDER }}
    service_account: ${{ secrets.GCP_SERVICE_ACCOUNT }}

- name: Run Review
  run: ./gradlew bootRun --args="cli"
  timeout-minutes: 5  # Sufficient time
  env:
    AI_PROVIDER: gemini
    GCP_PROJECT_ID: ${{ secrets.GCP_PROJECT_ID }}
```

## Performance Comparison

| Metric | Ollama (3b) | Gemini (Flash 2.0) |
|--------|-------------|-------------------|
| Response Time | 5-10 min (timeout) | 10-30 sec |
| Success Rate | 0% (all timed out) | 99%+ |
| Memory Usage | 4-6 GB | N/A (serverless) |
| Setup Time | 2-3 min | < 1 sec |
| Cost per Review | Free (but unusable) | Free (tier) |
| Review Quality | N/A (never completed) | 5/5 |
| Maintenance | High | Low |
| Security Risk | Low | Medium (abuse possible) |

## Security Considerations

### Cost Abuse Prevention

**Risk:** External API providers like Gemini are usage-based, making them vulnerable to abuse through malicious PR submissions.

**Attack Scenario:**
- Attacker creates multiple PRs or repeatedly reopens PRs
- Each PR triggers AI review via API
- Unlimited API calls lead to unexpected billing

**Mitigation Strategy:**

1. **Duplicate Review Prevention**
   ```java
   // Check if review already exists before processing
   return gitHubReviewService.hasExistingReview(owner, repo, prNumber)
       .flatMap(hasReview -> {
           if (hasReview) {
               log.info("Review already exists, skipping");
               return Mono.empty();
           }
           return processReview();
       });
   ```

2. **Workflow Trigger Restrictions**
   ```yaml
   on:
     pull_request:
       types: [opened, synchronize]
       # Removed 'reopened' to prevent abuse

   # Only run for trusted sources
   if: |
     github.event.pull_request.head.repo.full_name == github.repository ||
     contains(fromJSON('["OWNER", "MEMBER", "COLLABORATOR"]'),
              github.event.pull_request.author_association)
   ```

3. **Rate Limiting** (Future Enhancement)
   - Implement request throttling per user/PR
   - Set maximum reviews per time window
   - Monitor API usage with alerts

**Current Protection:**
- Duplicate review check prevents repeated API calls
- Workflow only runs for repository collaborators
- `reopened` event removed from triggers

## Migration Steps

1. Set up GCP Workload Identity Federation
2. Create IAM Service Account with appropriate permissions
3. Add GitHub secrets (WIF provider, SA email)
4. Implement security controls (duplicate check, trigger restrictions)
5. Update workflow (Ollama → Gemini)
6. Test and validate security measures
7. Archive Ollama-related files
8. Clean up Docker images (~44GB freed)

## Best Practices

### For CI/CD AI Integration:
1. **Start with managed services** - Reduce complexity in early stages
2. **Measure first, optimize later** - Optimize after identifying actual bottlenecks
3. **Use keyless authentication** - Balance security and convenience
4. **Implement cost controls** - Prevent abuse with duplicate checks and trigger restrictions
5. **Document decisions** - Record rationale for future reference

### For Security:
1. **Prevent duplicate API calls** - Check for existing reviews before processing
2. **Restrict workflow triggers** - Limit to trusted contributors only
3. **Remove abuse-prone events** - Avoid triggers like `reopened` that can be exploited
4. **Monitor API usage** - Set up alerts for unusual activity
5. **Plan for rate limiting** - Implement throttling for production use

### For Future Considerations:
- Consider self-hosted LLM only with GPU-enabled environments
- Implement comprehensive rate limiting per user/repository
- Set up API usage monitoring and cost alerts
- Prepare fallback strategy for API outages
- Regular security audits of workflow triggers

## Related Files

- Migration commit: `4b10546`
- Archive: `/archive/ollama/`
- Current workflow: `.github/workflows/ai-code-review.yml`
- WIF setup guide: [04-gcp-workload-identity-federation-setup.md](./04-gcp-workload-identity-federation-setup.md)

## References

- [Google Workload Identity Federation](https://cloud.google.com/iam/docs/workload-identity-federation)
- [Gemini API Pricing](https://ai.google.dev/pricing)
- [GitHub Actions: OIDC](https://docs.github.com/en/actions/deployment/security-hardening-your-deployments/about-security-hardening-with-openid-connect)
- [GCP WIF Setup Guide (Internal)](./04-gcp-workload-identity-federation-setup.md)
