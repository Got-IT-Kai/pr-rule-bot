# ADR-0010: Multi-Provider Abstraction

**Date**: 2025-10-15
**Status**: Proposed

---

## Context

Currently, pr-rule-bot is tightly coupled to GitHub:
- Hardcoded GitHub webhook payloads and API calls
- GitHub-specific authentication mechanisms
- Direct dependency on GitHub REST API client

**Vision V1.0 Goal**: Support any Git-based VCS (GitHub, GitLab, Bitbucket, self-hosted instances)

**Problem:**
- Users locked into GitHub
- Cannot use with GitLab, Bitbucket, or self-hosted Git servers
- Difficult to add new VCS providers
- Provider-specific features (GitHub draft PRs, GitLab MR approvals) would be lost with simple abstraction

**Impact if not addressed:**
- Limited market to GitHub-only users
- Cannot compete with multi-platform tools
- Users cannot migrate between VCS platforms

## Decision

**Use a Hybrid Abstraction approach** combining:
1. Core interface for common VCS operations (type-safe)
2. Grouped operations (PRs, comments, statuses, repositories)
3. Extension mechanism for provider-specific features (opt-in)

### Architecture

```java
// Main abstraction
interface VcsProvider {
    String getProviderId();               // "github", "gitlab", etc.
    PullRequestOperations pullRequests(); // Common PR operations
    CommentOperations comments();
    StatusOperations statuses();
    <T extends Extension> Optional<T> extension(Class<T> type);
}

// Common operations (works across all providers)
interface PullRequestOperations {
    Mono<PullRequest> get(String repoId, String prNumber);
    Flux<FileChange> getChanges(String repoId, String prNumber);
    Mono<DiffStats> getStats(String repoId, String prNumber);
}

// Provider-agnostic domain model
record PullRequest(
    String id, String title, String description,
    PullRequestState state, Author author,
    String sourceBranch, String targetBranch
) {}

// Provider-specific extensions (optional)
interface GitHubExtension extends Extension {
    Mono<Void> convertToDraft(String prId);
    Mono<Void> enableAutoMerge(String prId);
}
```

### Usage in Application Code

**Before** (GitHub-specific):
```java
@Service
public class ReviewCoordinator {
    private final GitHubClient githubClient;

    public void reviewPR(String prNumber) {
        var pr = githubClient.getPullRequest(prNumber);
        var files = githubClient.getChangedFiles(prNumber);
        // Review logic
    }
}
```

**After** (Provider-agnostic):
```java
@Service
public class ReviewCoordinator {
    private final VcsProvider vcsProvider;

    public Mono<Void> reviewPR(String repoId, String prNumber) {
        return vcsProvider.pullRequests().get(repoId, prNumber)
            .flatMap(pr -> vcsProvider.pullRequests().getChanges(repoId, prNumber)
                .collectList()
                .flatMap(files -> performReview(pr, files)));
    }

    // Use provider-specific features when available
    private Mono<Void> convertToDraftIfNeeded(String prId) {
        return Mono.justOrEmpty(vcsProvider.extension(GitHubExtension.class))
            .flatMap(ext -> ext.convertToDraft(prId))
            .switchIfEmpty(Mono.empty());  // Graceful fallback
    }
}
```

### Configuration

```yaml
vcs:
  provider: github  # or gitlab, bitbucket

  github:
    token: ${GITHUB_TOKEN}
    api-url: https://api.github.com

  gitlab:
    token: ${GITLAB_TOKEN}
    api-url: https://gitlab.com/api/v4
```

## Consequences

### Positive

**Multi-VCS Support**: Works with any Git-based platform
**Feature Preservation**: Provider-specific features accessible via extensions
**Type Safety**: Core operations compile-time checked
**Testability**: Easy to mock VcsProvider for unit tests
**No Vendor Lock-in**: Users can migrate between VCS platforms
**Extensibility**: Easy to add new providers by implementing interface
**Self-Hosted Support**: Works with private GitLab/Bitbucket instances

### Negative

**Learning Curve**: Developers must understand abstraction layer
**Indirection**: Extra layer between application and VCS API
**Testing Burden**: Must test each provider implementation
**Initial Effort**: Significant refactoring of existing GitHub code
**Maintenance**: Must keep all provider implementations in sync
**Minor Overhead**: Extra method calls (estimated 5ms per operation)

### Risks and Mitigation

**Risk**: Provider feature gaps (GitLab does not support feature X)
- Mitigation: Extension mechanism makes this explicit; graceful fallback

**Risk**: Performance regression from abstraction
- Mitigation: Benchmark before/after; optimize hot paths; use reactive types

**Risk**: Breaking changes for existing users
- Mitigation: Incremental rollout; feature flag; maintain backward compatibility

## Alternatives Considered

### Alternative 1: Simple Interface-Based Abstraction

```java
interface VcsProvider {
    PullRequest getPullRequest(String id);
    void postComment(String prId, String comment);
}
```

**Pros:**
- Simple and straightforward
- Standard OOP pattern

**Cons:**
- Lowest Common Denominator: Must support features in ALL providers
- Lost Features: Cannot use GitHub draft PRs, GitLab MR approvals
- Rigid: Hard to evolve without breaking changes

**Verdict**: Rejected - Too limiting

### Alternative 2: Pure Capability-Based

```java
interface VcsProvider {
    <T extends Capability> Optional<T> getCapability(Class<T> type);
}
```

**Pros:**
- Ultimate flexibility
- Discoverable capabilities

**Cons:**
- Complexity: Harder to understand and use
- Runtime Checks: No compile-time safety for common operations
- Learning Curve: Unfamiliar pattern for most developers

**Verdict**: Rejected - Too complex

### Alternative 3: Plugin Architecture

```java
// Plugins discovered via ServiceLoader
interface VcsPlugin {
    void initialize(Config config);
}
```

**Pros:**
- Hot-pluggable providers

**Cons:**
- Over-Engineering: Too complex for current needs
- Runtime Loading: Slower, less type-safe
- Distribution: Managing plugins is complex

**Verdict**: Rejected - Unnecessary complexity

## Implementation

### High-Level Approach

1. **Define Core Abstraction** (Week 1)
   - VcsProvider interface
   - Operation interfaces (PullRequestOperations, etc.)
   - Domain models (PullRequest, FileChange, etc.)

2. **Migrate GitHub** (Week 2-3)
   - Implement GitHubProvider
   - Refactor existing code to use abstraction
   - Ensure feature parity with current implementation

3. **Configuration & Integration** (Week 4)
   - Provider selection via config
   - Spring Boot auto-configuration
   - Health checks

4. **Additional Providers** (Week 5+)
   - Implement GitLabProvider
   - Implement BitbucketProvider
   - Comprehensive integration tests

### Validation

**Success Criteria:**
- All existing GitHub functionality works through abstraction
- No performance regression (< 5ms overhead)
- At least 2 providers implemented (GitHub + GitLab)
- Test coverage >= 85%
- Provider switching via configuration only

**Test Strategy:**
- Unit tests with mocked providers
- Integration tests against real VCS APIs (in CI)
- Contract tests ensuring all providers follow same behavior

## Related Decisions

- [ADR-0002: Automate PR Reviews](0002-automate-pr-reviews-via-github-actions.md) - Original GitHub-only design
- [ADR-0003: Webhook Security](0003-webhook-security.md) - Must work across providers

## References

### Similar Abstractions
- [Apache JClouds](https://jclouds.apache.org/) - Multi-cloud abstraction
- [JDBC](https://docs.oracle.com/javase/tutorial/jdbc/) - Database abstraction

### VCS APIs
- [GitHub REST API](https://docs.github.com/en/rest)
- [GitLab API](https://docs.gitlab.com/ee/api/)
- [Bitbucket API](https://developer.atlassian.com/cloud/bitbucket/rest/)

### Design Patterns
- [Strategy Pattern](https://refactoring.guru/design-patterns/strategy)
- [Adapter Pattern](https://refactoring.guru/design-patterns/adapter)
