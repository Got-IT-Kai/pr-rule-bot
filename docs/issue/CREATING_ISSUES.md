# Creating Issues Guide

## Quick Start

1. Choose appropriate template from `.github/ISSUE_TEMPLATE/`
2. Fill in all required sections
3. Apply proper labels (see LABELING_STRATEGY.md)
4. Link to related issues/PRs
5. Reference ADRs if architectural change

## Issue Templates

### Bug Report
Use when: Reporting defects or unexpected behavior

**Required sections:**
- Description
- Steps to Reproduce
- Expected vs Actual Behavior
- Environment

**Labels:** `type/bug`, `area/*`, `priority/*`

### Feature Request
Use when: Proposing new functionality

**Required sections:**
- Problem Statement
- Proposed Solution
- Acceptance Criteria

**Labels:** `type/feature`, `area/*`

### Security Issue
Use when: Reporting vulnerabilities

**Required sections:**
- Vulnerability Description
- Impact
- Proposed Fix

**Labels:** `type/security`, `priority/critical`, `area/*`

### Task
Use when: Technical work items

**Required sections:**
- Description
- Task checklist
- Acceptance Criteria

**Labels:** `type/task`, `area/*`

## Best Practices

### Title Format

```
[Type] Brief description (max 72 chars)

Examples:
[Bug] Webhook signature validation fails with special characters
[Feature] Add token chunking for large diffs
[Security] Rate limiting bypass vulnerability
```

### Writing Descriptions

- Be specific and concise
- Include context (why this matters)
- Link to related issues (#123) and ADRs
- Add code snippets or logs if relevant

### Task Breakdown

Use GitHub task lists:
```markdown
- [ ] Create WebhookSecurityService
- [ ] Implement signature validation
- [ ] Add integration tests
- [ ] Update documentation
```

### Dependencies

Always specify:
```markdown
Blocked by: #42
Depends on: #38
Related to: #15
```

## Issue Creation Checklist

Before submitting:
- [ ] Used correct template
- [ ] All required sections filled
- [ ] Proper labels applied
- [ ] Title follows format
- [ ] Dependencies specified
- [ ] ADR referenced (if applicable)
- [ ] Acceptance criteria defined
- [ ] Assigned to milestone (if v1.0)

## Creating Issues via CLI

```bash
# Bug report
gh issue create \
  --template bug_report.md \
  --title "[Bug] Brief description" \
  --label "type/bug,area/webhook,priority/high"

# Feature request
gh issue create \
  --template feature_request.md \
  --title "[Feature] Brief description" \
  --label "type/feature,area/ai"

# Security issue
gh issue create \
  --template security.md \
  --title "[Security] Brief description" \
  --label "type/security,priority/critical,area/security"
```

## Milestone Assignment

For v1.0.0 release issues:
```bash
gh issue edit <number> --milestone "v1.0.0"
```
