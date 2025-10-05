# Lesson: GCP Workload Identity Federation Setup for GitHub Actions

**Date:** October 5, 2025

## Problem Statement

Need to authenticate GitHub Actions to Google Cloud Platform (GCP) services (Gemini API) without storing long-lived JSON credentials, which pose security risks.

## Why Workload Identity Federation?

### Traditional Approach (Bad)
```yaml
# Store JSON key as GitHub secret
- uses: google-github-actions/auth@v2
  with:
    credentials_json: ${{ secrets.GCP_SA_KEY }}
```

**Problems:**
- Long-lived credentials can be compromised
- Manual rotation required
- Difficult to audit access
- If leaked, unlimited access until rotated

### Workload Identity Federation (Good)
```yaml
# No credentials stored, OIDC token-based
- uses: google-github-actions/auth@v2
  with:
    workload_identity_provider: ${{ secrets.GCP_WIF_PROVIDER }}
    service_account: ${{ secrets.GCP_SERVICE_ACCOUNT }}
```

**Benefits:**
- No long-lived credentials
- Automatic token rotation
- GitHub OIDC provides identity proof
- Granular permission control
- Better audit trail

## Step-by-Step Setup Guide

### 1. Enable Required GCP APIs

```bash
# Enable IAM Service Account Credentials API
gcloud services enable iamcredentials.googleapis.com

# Enable other required APIs
gcloud services enable aiplatform.googleapis.com
```

**Why:** WIF requires the IAM Service Account Credentials API to generate short-lived tokens.

### 2. Create Service Account

```bash
# Create service account
gcloud iam service-accounts create github-actions-ai-review \
    --display-name="GitHub Actions AI Review" \
    --description="Service account for AI code review from GitHub Actions"

# Get the email
export SA_EMAIL="github-actions-ai-review@YOUR_PROJECT_ID.iam.gserviceaccount.com"
```

### 3. Grant Service Account Permissions

```bash
# Grant Vertex AI User role (for Gemini API)
gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
    --member="serviceAccount:${SA_EMAIL}" \
    --role="roles/aiplatform.user"

# Optional: Add other roles as needed
gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
    --member="serviceAccount:${SA_EMAIL}" \
    --role="roles/logging.logWriter"
```

**Common Roles:**
- `roles/aiplatform.user` - Access Vertex AI (Gemini)
- `roles/logging.logWriter` - Write logs (optional)
- `roles/monitoring.metricWriter` - Write metrics (optional)

### 4. Create Workload Identity Pool

```bash
# Create the pool
gcloud iam workload-identity-pools create "github-actions-pool" \
    --location="global" \
    --display-name="GitHub Actions Pool" \
    --description="Identity pool for GitHub Actions"

# Verify creation
gcloud iam workload-identity-pools describe github-actions-pool \
    --location="global"
```

### 5. Create Workload Identity Provider

```bash
# Create OIDC provider for GitHub
gcloud iam workload-identity-pools providers create-oidc "github-provider" \
    --location="global" \
    --workload-identity-pool="github-actions-pool" \
    --issuer-uri="https://token.actions.githubusercontent.com" \
    --attribute-mapping="google.subject=assertion.sub,attribute.actor=assertion.actor,attribute.repository=assertion.repository" \
    --attribute-condition="assertion.repository_owner=='YOUR_GITHUB_USERNAME'"
```

**Important Parameters:**
- `--issuer-uri`: GitHub's OIDC token issuer
- `--attribute-mapping`: Maps GitHub token claims to GCP attributes
  - `google.subject`: Unique identifier
  - `attribute.actor`: GitHub user who triggered workflow
  - `attribute.repository`: Repository name
- `--attribute-condition`: Security restriction (only your repos)

### 6. Grant Service Account Access to Workload Identity

```bash
# Allow the identity pool to impersonate the service account
gcloud iam service-accounts add-iam-policy-binding "${SA_EMAIL}" \
    --role="roles/iam.workloadIdentityUser" \
    --member="principalSet://iam.googleapis.com/projects/PROJECT_NUMBER/locations/global/workloadIdentityPools/github-actions-pool/attribute.repository/YOUR_GITHUB_USERNAME/YOUR_REPO_NAME"
```

**Get Project Number:**
```bash
gcloud projects describe YOUR_PROJECT_ID --format="value(projectNumber)"
```

### 7. Get Workload Identity Provider Name

```bash
# Get the full provider resource name
gcloud iam workload-identity-pools providers describe github-provider \
    --location="global" \
    --workload-identity-pool="github-actions-pool" \
    --format="value(name)"
```

**Output Example:**
```
projects/123456789/locations/global/workloadIdentityPools/github-actions-pool/providers/github-provider
```

### 8. Configure GitHub Secrets

Add these secrets to your GitHub repository:

1. **GCP_WIF_PROVIDER**
   ```
   projects/123456789/locations/global/workloadIdentityPools/github-actions-pool/providers/github-provider
   ```

2. **GCP_SERVICE_ACCOUNT**
   ```
   github-actions-ai-review@YOUR_PROJECT_ID.iam.gserviceaccount.com
   ```

3. **GCP_PROJECT_ID**
   ```
   YOUR_PROJECT_ID
   ```

**How to add:**
- Go to repository → Settings → Secrets and variables → Actions
- Click "New repository secret"
- Add each secret

### 9. Update GitHub Actions Workflow

```yaml
name: AI Code Review

on:
  pull_request:
    types: [opened, synchronize]

permissions:
  contents: read
  pull-requests: write
  id-token: write  # Required for OIDC token

jobs:
  ai-review:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Authenticate to Google Cloud
        uses: google-github-actions/auth@v2
        with:
          workload_identity_provider: ${{ secrets.GCP_WIF_PROVIDER }}
          service_account: ${{ secrets.GCP_SERVICE_ACCOUNT }}

      - name: Run AI Review
        env:
          GCP_PROJECT_ID: ${{ secrets.GCP_PROJECT_ID }}
        run: ./gradlew bootRun --args="cli"
```

**Critical:** `id-token: write` permission is required for GitHub to issue OIDC tokens.

## Verification

### Test Authentication

```bash
# In GitHub Actions, verify authentication
gcloud auth list

# Check if service account is active
gcloud config get-value account
```

### Test API Access

```bash
# Test Gemini API access
curl -X POST \
  -H "Authorization: Bearer $(gcloud auth print-access-token)" \
  -H "Content-Type: application/json" \
  "https://us-central1-aiplatform.googleapis.com/v1/projects/YOUR_PROJECT_ID/locations/us-central1/publishers/google/models/gemini-2.0-flash-001:generateContent" \
  -d '{"contents":[{"role":"user","parts":[{"text":"Hello"}]}]}'
```

## Troubleshooting

### Error: "IAM Service Account Credentials API has not been used"

**Solution:**
```bash
gcloud services enable iamcredentials.googleapis.com
```

### Error: "Permission denied on service account"

**Solution:** Check IAM binding
```bash
# Verify service account has workloadIdentityUser role
gcloud iam service-accounts get-iam-policy ${SA_EMAIL}
```

### Error: "Token request failed"

**Causes:**
1. Missing `id-token: write` permission in workflow
2. Incorrect attribute condition in provider
3. Repository doesn't match the condition

**Debug:**
```bash
# Check workflow permissions
# Verify attribute-condition matches your repo
gcloud iam workload-identity-pools providers describe github-provider \
    --location="global" \
    --workload-identity-pool="github-actions-pool"
```

### Error: "Invalid audience"

**Solution:** Ensure issuer-uri is exactly `https://token.actions.githubusercontent.com`

## Security Best Practices

### 1. Principle of Least Privilege
```bash
# Only grant necessary roles
# Don't use roles/owner or roles/editor
gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
    --member="serviceAccount:${SA_EMAIL}" \
    --role="roles/aiplatform.user"  # Specific role only
```

### 2. Repository Restrictions
```bash
# Restrict to specific repository
--attribute-condition="assertion.repository=='owner/repo'"

# Or restrict to organization
--attribute-condition="assertion.repository_owner=='org-name'"
```

### 3. Environment Restrictions
```bash
# Only allow from specific environment (e.g., production)
--attribute-condition="assertion.repository=='owner/repo' && assertion.environment=='production'"
```

### 4. Audit Logging
```bash
# Enable audit logs for service account
gcloud projects get-iam-policy YOUR_PROJECT_ID \
    --flatten="bindings[].members" \
    --filter="bindings.members:serviceAccount:${SA_EMAIL}"
```

### 5. Regular Review
- Periodically review service account permissions
- Check Cloud Audit Logs for unusual activity
- Rotate any remaining long-lived keys

## Key Concepts

### OIDC (OpenID Connect)
- Standard for federated authentication
- GitHub generates OIDC tokens for workflows
- Tokens contain claims (repository, actor, etc.)
- Short-lived (typically 1 hour)

### JWT Claims in GitHub OIDC Token
```json
{
  "sub": "repo:owner/repo:ref:refs/heads/main",
  "repository": "owner/repo",
  "repository_owner": "owner",
  "actor": "username",
  "workflow": "AI Code Review",
  "environment": "production"
}
```

### How WIF Works
```
1. GitHub Actions requests OIDC token from GitHub
2. GitHub issues token with claims (repo, actor, etc.)
3. Workflow sends token to GCP WIF
4. WIF validates token against provider config
5. WIF checks attribute conditions
6. WIF generates short-lived GCP credentials
7. Workflow uses credentials to access GCP services
```

## Comparison: JSON Key vs WIF

| Aspect | JSON Key | Workload Identity Federation |
|--------|----------|----------------------------|
| Credential Storage | Required | Not required |
| Credential Lifetime | Permanent | Short-lived (1 hour) |
| Rotation | Manual | Automatic |
| Revocation | Manual | Automatic (token expiry) |
| Audit Trail | Limited | Comprehensive |
| Security Risk | High (if leaked) | Low |
| Setup Complexity | Low | Medium |
| Maintenance | Manual | Minimal |

## Related Files

- `.github/workflows/ai-code-review.yml` - Workflow using WIF
- Migration commit: `4b10546`

## References

- [Workload Identity Federation](https://cloud.google.com/iam/docs/workload-identity-federation)
- [GitHub OIDC](https://docs.github.com/en/actions/deployment/security-hardening-your-deployments/about-security-hardening-with-openid-connect)
- [google-github-actions/auth](https://github.com/google-github-actions/auth)
- [GCP IAM Best Practices](https://cloud.google.com/iam/docs/best-practices)
