# Lesson: Spring Boot CLI Graceful Shutdown

**Date:** October 5, 2025

## Problem Statement

Spring Boot CLI application in GitHub Actions doesn't terminate after task completion, causing the workflow to wait indefinitely.

## Root Cause Analysis

### Initial Implementation
```java
@Component
@Profile("ci")
public class ReviewCli implements ApplicationRunner {
    @Override
    public void run(ApplicationArguments args) throws Exception {
        gitHubReviewService.fetchUnifiedDiff(owner, repo, prNumber)
            .flatMap(aiPort::evaluateDiff)
            .flatMap(review -> gitHubReviewService.postReviewComment(...))
            .doOnSuccess(v -> log.info("Review completed"))
            .doOnError(e -> log.error("Review failed", e))
            .block(Duration.ofMinutes(5));

        // Problem: Spring Boot context keeps running
    }
}
```

**Issues:**
1. `ApplicationRunner` completes execution, but Spring Boot context remains active
2. Embedded Netty server (WebFlux) and other non-daemon threads stay alive
3. GitHub Actions waits indefinitely for process termination

### Why This Happens
```
Spring Boot Lifecycle:
1. Context initialization
2. ApplicationRunner execution ← Our logic runs here
3. Context continues running ← Problem
4. (Never shuts down automatically)
```

## Solutions Explored

### Bad: System.exit()
```java
@Override
public void run(ApplicationArguments args) throws Exception {
    try {
        // ... review logic
        log.info("Review completed");
        System.exit(0);  // Too abrupt
    } catch (Exception e) {
        log.error("Review failed", e);
        System.exit(1);
    }
}
```

**Issues:**
- Forces JVM termination (no graceful shutdown)
- Ignores Spring's shutdown hooks
- Resources not cleaned up (DB connections, etc.)
- Difficult to test

### Bad: Thread Sleep then Exit
```java
@Override
public void run(ApplicationArguments args) throws Exception {
    // ... review logic

    new Thread(() -> {
        try {
            Thread.sleep(1000);
            System.exit(0);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }).start();
}
```

**Issues:**
- Complex thread management
- Potential race conditions
- Still not graceful

### Good: SpringApplication.exit()
```java
@Component
@Profile("ci")
@RequiredArgsConstructor
public class ReviewCli implements ApplicationRunner {

    private final ConfigurableApplicationContext applicationContext;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        gitHubReviewService.fetchUnifiedDiff(owner, repo, prNumber)
            .flatMap(aiPort::evaluateDiff)
            .flatMap(review -> gitHubReviewService.postReviewComment(...))
            .doOnSuccess(v -> {
                log.info("Review completed");
                shutdownApplication(0);  // Graceful
            })
            .doOnError(e -> {
                log.error("Review failed", e);
                shutdownApplication(1);
            })
            .block(Duration.ofMinutes(5));
    }

    private void shutdownApplication(int exitCode) {
        SpringApplication.exit(applicationContext, () -> exitCode);
    }
}
```

**Advantages:**
- Graceful shutdown (follows Spring lifecycle)
- Executes shutdown hooks
- Cleans up resources
- Configurable exit code
- Testable

## Key Lessons Learned

### 1. ApplicationRunner ≠ Application Shutdown
`ApplicationRunner` is just an execution hook at startup, not a lifecycle controller.

### 2. CLI vs Server Application
```java
// Server Application (default)
@SpringBootApplication
public class ServerApp {
    public static void main(String[] args) {
        SpringApplication.run(ServerApp.class, args);
        // Context keeps running for requests
    }
}

// CLI Application (separated by profile)
@Profile("ci")
@Component
public class CliApp implements ApplicationRunner {
    @Override
    public void run(ApplicationArguments args) {
        // Execute task
        // Shutdown when done
        SpringApplication.exit(context);
    }
}
```

### 3. Exit Codes Matter
```java
private void shutdownApplication(int exitCode) {
    SpringApplication.exit(applicationContext, () -> exitCode);
}

// Used in GitHub Actions
// exit code 0 = success
// exit code 1 = failure
```

**GitHub Actions:**
```yaml
- name: Run AI Code Review
  run: ./gradlew bootRun --args="cli"
  # Exit code 0: Step succeeds
  # Exit code 1: Step fails, workflow stops
```

### 4. Profile-based Architecture
```yaml
# application.yml
spring:
  profiles:
    active: default  # Server mode

---
spring:
  config:
    activate:
      on-profile: ci
# CLI-specific config
```

**Benefits:**
- Same codebase, different behaviors
- Easy testing (profile switching)
- No code duplication

## Testing CLI Shutdown

### Unit Test
```java
@ExtendWith(MockitoExtension.class)
class ReviewCliTest {
    @Mock
    private ConfigurableApplicationContext applicationContext;

    @Test
    void shouldShutdownWithExitCode0OnSuccess() throws Exception {
        // given
        given(gitHubService.fetchDiff()).willReturn(Mono.just(DIFF));
        given(aiPort.evaluateDiff(DIFF)).willReturn(Mono.just(REVIEW));
        given(gitHubService.postReview()).willReturn(Mono.empty());

        // when
        reviewCli.run(args);

        // then
        // Note: Can't easily test SpringApplication.exit()
        // But we can verify the flow completes
        then(gitHubService).should(times(1)).postReview(...);
    }
}
```

### Integration Test
```java
@SpringBootTest
@ActiveProfiles({"test", "ci"})
class ReviewCliIntegrationTest {
    @Test
    void shouldCompleteReviewFlow() {
        // Test with mocked external services
        // Verify application context behavior
    }
}
```

## Best Practices

### For CLI Applications in Spring Boot:

1. **Use SpringApplication.exit()**
   ```java
   SpringApplication.exit(context, () -> exitCode);
   ```

2. **Separate Profiles**
   ```java
   @Profile("ci")  // CLI mode
   @Profile("!ci")  // Server mode
   ```

3. **Proper Error Handling**
   ```java
   .doOnSuccess(v -> shutdown(0))
   .doOnError(e -> {
       log.error("Failed", e);
       shutdown(1);
   })
   ```

4. **Set Timeout**
   ```java
   .block(Duration.ofMinutes(timeout));
   ```

5. **Document Exit Codes**
   ```java
   // 0 = Success
   // 1 = General error
   // 2 = Configuration error
   // etc.
   ```

### For GitHub Actions:

```yaml
- name: Run CLI Task
  run: ./gradlew bootRun --args="cli"
  timeout-minutes: 10
  env:
    SPRING_PROFILES_ACTIVE: ci
```

## Comparison

| Approach | Graceful | Exit Code | Testing | Resources Cleanup |
|----------|----------|-----------|---------|------------------|
| `System.exit()` | No | Yes | No | No |
| Thread + exit | No | Yes | No | No |
| `SpringApplication.exit()` | Yes | Yes | Yes | Yes |

## Related Files

- `ReviewCli.java:72-74` - Shutdown implementation
- `.github/workflows/ai-code-review.yml` - CI usage

## References

- [Spring Boot: Application Exit](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.spring-application.application-exit)
- [ApplicationRunner](https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/ApplicationRunner.html)
