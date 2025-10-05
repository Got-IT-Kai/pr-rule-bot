# Release Management

This directory contains release planning and documentation for pr-rule-bot.

## Release Process

### 1. Planning Phase

Create release plan document:
- Define release goals and scope
- List features and fixes
- Document architecture decisions (ADRs)
- Set success criteria

See: [v1.0-plan.md](./v1.0-plan.md)

### 2. Development Phase

#### Create Milestones

Group related work into milestones:
- Security fixes
- Bug fixes
- New features
- Infrastructure improvements
- Test coverage
- Code quality

Milestones are managed in GitHub (not documented separately).

#### Create Issues

For each task:
1. Use appropriate issue template (.github/ISSUE_TEMPLATE/)
2. Assign to relevant milestone
3. Add priority labels
4. Reference related ADRs

#### Track Progress

Monitor milestone completion:
```bash
gh api repos/Got-IT-Kai/pr-rule-bot/milestones
```

### 3. Testing Phase

Before release:
- [ ] All milestone issues closed
- [ ] Test coverage meets targets (80%+)
- [ ] Security audit passes
- [ ] Integration tests pass
- [ ] Documentation updated

### 4. Release Phase

#### Create GitHub Release

```bash
# Tag the release
git tag -a v1.0.0 -m "Release v1.0.0"
git push origin v1.0.0

# Create GitHub release
gh release create v1.0.0 \
  --title "v1.0.0 - First Production Release" \
  --notes-file docs/release/v1.0-notes.md \
  --target master
```

#### Update CHANGELOG

Move unreleased changes to versioned section in CHANGELOG.md:

```markdown
## [1.0.0] - 2025-XX-XX

### Added
- Webhook signature verification
- Smart token chunking
...
```

#### Build and Publish

```bash
# Build the application
./gradlew clean build

# Attach jar to release
gh release upload v1.0.0 build/libs/pr-rule-bot-1.0.0.jar
```

### 5. Post-Release

- [ ] Update README with new version
- [ ] Announce release (if public)
- [ ] Monitor for issues
- [ ] Plan next release (v2.0)

## Release Naming

Following [Semantic Versioning](https://semver.org/):

- **Major (X.0.0)**: Breaking changes
- **Minor (0.X.0)**: New features, backward compatible
- **Patch (0.0.X)**: Bug fixes, backward compatible

## Milestone Management

### Creating Milestones

```bash
gh api repos/Got-IT-Kai/pr-rule-bot/milestones -X POST \
  -f title="Milestone Name" \
  -f description="Description with issue list" \
  -f state="open"
```

### Assigning Issues

```bash
gh issue edit <issue-number> --milestone "Milestone Name"
```

### Closing Milestones

When all issues are complete:
```bash
gh api repos/Got-IT-Kai/pr-rule-bot/milestones/<number> -X PATCH \
  -f state="closed"
```

## Documents in this Directory

- **README.md** (this file): Release process guide
- **v1.0-plan.md**: Detailed v1.0 release plan
- **v1.0-notes.md**: Release notes for v1.0 (created when ready)
- **CHANGELOG.md** (root): Complete project changelog

## Related Documentation

- [Architecture Decision Records](../adr/README.md)
- [Issue Templates](../../.github/ISSUE_TEMPLATE/)
- [Code Review Reports](../code-review/README.md)

## Release Checklist Template

```markdown
## Pre-Release Checklist

- [ ] All milestone issues completed
- [ ] Test coverage >= 80%
- [ ] Security audit passed
- [ ] All tests passing
- [ ] Documentation updated
- [ ] CHANGELOG updated
- [ ] Release notes drafted
- [ ] Version bumped in build.gradle

## Release Checklist

- [ ] Git tag created
- [ ] GitHub release created
- [ ] Artifacts uploaded
- [ ] Release notes published

## Post-Release Checklist

- [ ] README updated
- [ ] Monitor for issues
- [ ] Plan next release
```
