# Issue and PR Labeling Strategy

## Label Categories

### Type (required)
Primary classification of the issue.

- `type/bug` - Bug fix or defect
- `type/feature` - New feature or enhancement
- `type/task` - Technical task or improvement
- `type/security` - Security vulnerability or fix
- `type/test` - Test-related changes
- `type/docs` - Documentation updates
- `type/chore` - Maintenance, dependency updates

### Priority (optional)
Urgency and importance.

- `priority/critical` - Must be fixed immediately, blocks release
- `priority/high` - Should be fixed soon, important for release
- `priority/medium` - Normal priority
- `priority/low` - Nice to have, future consideration

### Area (required)
Component or subsystem affected.

- `area/webhook` - GitHub webhook handling
- `area/ai` - AI integration and prompt engineering
- `area/persistence` - Database and storage
- `area/api` - API endpoints and controllers
- `area/security` - Authentication, authorization, encryption
- `area/observability` - Logging, metrics, tracing
- `area/build` - Build system, dependencies
- `area/testing` - Test infrastructure
- `area/docs` - Documentation

### Status (lifecycle)
Current state of the issue.

- `status/triage` - Needs initial review
- `status/blocked` - Blocked by external dependency
- `status/in-progress` - Currently being worked on
- `status/review` - In code review
- `status/done` - Completed and merged

### Special Labels

- `good-first-issue` - Good for new contributors
- `help-wanted` - Extra attention needed
- `breaking-change` - Introduces breaking API change
- `needs-adr` - Requires architecture decision record

## Labeling Examples

### Security Issue
```
type/security
priority/critical
area/webhook
```

### Feature Request
```
type/feature
priority/high
area/ai
```

### Bug Report
```
type/bug
priority/medium
area/persistence
```

### Technical Task
```
type/task
priority/low
area/build
```

## Label Management

### Creating Labels via CLI

```bash
gh label create "type/bug" --color d73a4a --description "Bug fix or defect"
gh label create "type/feature" --color 0e8a16 --description "New feature or enhancement"
gh label create "type/security" --color d93f0b --description "Security vulnerability"
gh label create "priority/critical" --color b60205 --description "Critical priority"
gh label create "area/webhook" --color 5319e7 --description "GitHub webhook handling"
```

### Required Labels

Every issue must have:
1. One `type/*` label
2. One `area/*` label
3. One `priority/*` label (for type/bug and type/security)

### Label Automation

Use GitHub Actions to:
- Auto-label based on title prefix (e.g., "[Bug]" → type/bug)
- Enforce required labels before close
- Update status labels based on PR state
