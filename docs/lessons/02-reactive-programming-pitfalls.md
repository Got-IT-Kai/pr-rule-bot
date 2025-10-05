# Lesson: Reactive Programming Pitfalls & Best Practices

**Date:** October 5, 2025

## Problem Statement

Common mistakes and solutions when using Spring WebFlux and Project Reactor for reactive programming.

## Common Pitfalls

### 1. Explicit `.subscribe()` Usage

#### Bad Practice
```java
public void processReview(String diff) {
    aiPort.evaluateDiff(diff)
        .subscribe(review -> {
            log.info("Review: {}", review);
            gitHubReviewService.postReviewComment(owner, repo, prNumber, review)
                .subscribe();  // Nested subscription
        });
}
```

**Issues:**
- Potential blocking calls
- Difficult error handling
- Risk of memory leaks (unsubscribed)
- No backpressure handling

#### Good Practice
```java
public Mono<Void> processReview(String diff) {
    return aiPort.evaluateDiff(diff)
        .doOnNext(review -> log.info("Review: {}", review))
        .flatMap(review ->
            gitHubReviewService.postReviewComment(owner, repo, prNumber, review)
        );
}
```

**Advantages:**
- Non-blocking
- Declarative error handling
- Composable
- Framework manages subscription

### 2. Using `.block()` with BlockHound

#### When is `.block()` acceptable?

```java
// Never in production reactive code
public String getReview(String diff) {
    return aiPort.evaluateDiff(diff).block();  // Blocking
}

// Only in CLI/Entry points with proper profile
@Profile("ci")
public class ReviewCli implements ApplicationRunner {
    @Override
    public void run(ApplicationArguments args) throws Exception {
        // This is OK - it's an entry point, not in reactive chain
        aiPort.evaluateDiff(diff)
            .flatMap(...)
            .block(Duration.ofMinutes(5));
    }
}
```

**Key Points:**
- `.block()` only at entry points
- Separate with `@Profile` to avoid BlockHound impact in tests
- Never use in the middle of reactive chains

### 3. Error Handling

#### Bad: Try-Catch in Reactive Chain
```java
return gitHubReviewService.hasExistingReview(owner, repo, prNumber)
    .flatMap(hasReview -> {
        try {
            if (hasReview) {
                return Mono.empty();
            }
            return gitHubReviewService.fetchUnifiedDiff(owner, repo, prNumber);
        } catch (Exception e) {  // Won't catch reactive errors
            log.error("Error", e);
            return Mono.empty();
        }
    });
```

#### Good: Use Reactive Error Operators
```java
return gitHubReviewService.hasExistingReview(owner, repo, prNumber)
    .onErrorResume(e -> {
        log.error("Failed to check existing review", e);
        return Mono.just(false);  // Continue with review on error
    })
    .flatMap(hasReview -> {
        if (hasReview) {
            log.info("Review already exists, skipping");
            return Mono.empty();
        }
        return gitHubReviewService.fetchUnifiedDiff(owner, repo, prNumber);
    })
    .onErrorResume(WebClientResponseException.class, e -> {
        log.error("GitHub API error: {}", e.getMessage());
        return Mono.error(new GitHubApiException("Failed to fetch diff", e));
    });
```

**Best Practices:**
- `onErrorResume`: Replace error with another Mono
- `onErrorReturn`: Replace error with a specific value
- `doOnError`: Log errors (stream continues with error)
- Specific exception handling: `WebClientResponseException`, etc.

### 4. SwitchIfEmpty vs DefaultIfEmpty

#### Difference
```java
// switchIfEmpty: Switch to another Publisher when Mono is empty
.switchIfEmpty(Mono.defer(() -> {
    log.warn("No diff found, skipping review");
    return Mono.empty();
}))

// defaultIfEmpty: Provide default value when Mono is empty
.defaultIfEmpty("")  // Return empty string
```

**Use Cases:**
- `switchIfEmpty`: When other logic needs to execute
- `defaultIfEmpty`: When only a default value is needed

### 5. Filter vs FlatMap

#### Inefficient
```java
.flatMap(diff -> {
    if (StringUtils.hasText(diff)) {
        return aiPort.evaluateDiff(diff);
    }
    return Mono.empty();
})
```

#### Better
```java
.filter(StringUtils::hasText)
.flatMap(aiPort::evaluateDiff)
```

**Why?**
- More declarative
- Cleaner code
- Better performance (less overhead)

## Key Lessons Learned

### 1. Think in Streams, Not Steps
Reactive programming defines the flow of data:
```
Data → Transform → Filter → Combine with other streams → Final processing
```

### 2. Composition is Key
```java
// Each step returns Mono/Flux - composable
return checkExisting()
    .filter(not(exists))
    .flatMap(this::fetchDiff)
    .filter(StringUtils::hasText)
    .flatMap(aiPort::evaluate)
    .flatMap(this::postReview);
```

### 3. Error Handling at the Right Level
```java
// Specific errors at operation level
.flatMap(this::callExternalApi)
    .onErrorResume(TimeoutException.class, e -> retry())
    .onErrorResume(WebClientResponseException.class, e -> fallback())

// Generic errors at top level
.doOnError(e -> log.error("Failed", e))
.doOnSuccess(v -> log.info("Success"))
```

## Testing Reactive Code

### Use StepVerifier
```java
@Test
void shouldHandleEmptyDiff() {
    // given
    given(gitHubService.fetchDiff()).willReturn(Mono.just(""));

    // when & then
    StepVerifier.create(reviewCli.process())
        .expectComplete()
        .verify();

    then(aiPort).should(never()).evaluateDiff(anyString());
}

@Test
void shouldHandleError() {
    // given
    RuntimeException error = new RuntimeException("API error");
    given(gitHubService.fetchDiff()).willReturn(Mono.error(error));

    // when & then
    StepVerifier.create(reviewCli.process())
        .expectErrorMatches(e ->
            e instanceof RuntimeException &&
            e.getMessage().equals("API error")
        )
        .verify();
}
```

### BlockHound in Tests
```java
// build.gradle
test {
    systemProperty 'blockhound.enabled', 'true'
    jvmArgs += ['-XX:+EnableDynamicAgentLoading']
}

// BlockHound will detect blocking calls
@Test
void shouldNotBlock() {
    StepVerifier.create(
        Mono.delay(Duration.ofMillis(1))
            .doOnNext(it -> {
                Thread.sleep(10);  // BlockHound will catch this
            })
    )
    .expectError(BlockingOperationError.class)
    .verify();
}
```

## Best Practices Summary

### Do's
1. Return `Mono`/`Flux` from all methods
2. Use operators: `filter`, `map`, `flatMap`, `switchIfEmpty`
3. Handle errors with `onErrorResume`, `onErrorReturn`
4. Test with `StepVerifier`
5. Use `doOn*` for side effects (logging)
6. `.block()` only at entry points

### Don'ts
1. Never use `.subscribe()` in business logic
2. No `try-catch` for reactive errors
3. Avoid nested subscriptions
4. Don't block in reactive chains
5. Don't ignore backpressure

## Operator Cheat Sheet

| Operator | Use Case | Example |
|----------|----------|---------|
| `map` | Transform value | `.map(String::toUpperCase)` |
| `flatMap` | Transform to Mono/Flux | `.flatMap(this::callApi)` |
| `filter` | Conditional flow | `.filter(Objects::nonNull)` |
| `switchIfEmpty` | Fallback on empty | `.switchIfEmpty(Mono.just("default"))` |
| `defaultIfEmpty` | Default value | `.defaultIfEmpty("")` |
| `onErrorResume` | Error recovery | `.onErrorResume(e -> Mono.empty())` |
| `onErrorReturn` | Error default | `.onErrorReturn("error occurred")` |
| `doOnNext` | Side effect | `.doOnNext(log::info)` |
| `doOnError` | Error logging | `.doOnError(log::error)` |

## Related Files

- `ReviewCli.java` - Entry point with `.block()`
- `GitHubReviewService.java` - Proper reactive implementation
- `BlockHoundExtension.java` - Test configuration

## References

- [Project Reactor Reference](https://projectreactor.io/docs/core/release/reference/)
- [BlockHound](https://github.com/reactor/BlockHound)
- [Spring WebFlux](https://docs.spring.io/spring-framework/reference/web/webflux.html)
