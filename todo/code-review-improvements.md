# Code Review Improvements

## 🔥 High Priority (Must Fix)

### 1. CI Test Failures
**Status**: ✅ FIXED
- `AgentApplicationTests > contextLoads()` FIXED
- `ReviewRequestedEventListenerTest > checkEventPublishing()` FIXED
- **Root Cause**: Missing configuration properties in `application.yml` for integration tests
- **Fix Applied**:
  - Added `ai.provider`, `ai.prompts`, `ai.client` configuration
  - Added `github.review-path`, `github.client` configuration
  - Created test prompt files in `src/integrationTest/resources/prompts/`

### 2. Error Handling in `hasExistingReview`
**Status**: ⏳ TODO
- Add `onErrorResume` to handle errors during the `hasExistingReview` call
- Currently, errors will cause the entire review process to fail
```java
.flatMap(hasReview -> {
    if (hasReview) {
        // skip
    }
    return gitHubReviewService.fetchUnifiedDiff(owner, repo, prNumber);
})
.onErrorResume(e -> {
    log.error("Failed to check existing review", e);
    return Mono.just(false); // Continue with review on error
})
```

### 3. Specific Exception Handling
**Status**: ⏳ TODO
- Catch specific exceptions like `WebClientResponseException` and `TimeoutException`
- Create custom exception types (e.g., `GitHubApiException`)
- Improve error messages for better debugging

### 4. Test Exception Assertions
**Status**: ⏳ TODO
- Use `assertThrows` instead of try-catch in tests
- Use `StepVerifier.expectErrorMatches` for reactive exception verification
- Examples in:
  - `ReviewCliTest`: error handling tests
  - `ReviewCoordinatorTest`: timeout scenarios

## 🎯 Medium Priority (Should Do)

### 5. Filter Reviews by AI Agent
**Status**: 🤔 CONSIDER
- Currently checks if ANY review exists
- Should filter to find reviews created by the AI agent
- Prevents skipping when human reviews exist

### 6. Add Comprehensive Logging
**Status**: ⏳ TODO
- Add logging when no diff is found
- Make log messages more specific in `hasExistingReview`
- Add debug logging in `ReviewCoordinator`

### 7. Improve Workflow Error Handling
**Status**: ⏳ TODO
- Add `set -e` to GitHub Actions workflow scripts
- Improve error messages in workflow
- Use GitHub Actions' built-in error reporting

### 8. Test Coverage Improvements
**Status**: ⏳ TODO
- Add timeout scenario test in `ReviewCoordinatorTest`
- Add integration tests for `GeminiAiClient`
- Test prompt templates thoroughly with edge cases

## 📝 Low Priority (Nice to Have)

### 9. Configuration Improvements
- Centralize environment variables in workflow
- Use snake_case for custom properties in `application.yml`
- Add validation for `max-tokens` range
- Document purpose of each configuration property

### 10. Code Quality
- Simplify `AiProperties` setup in `OllamaAiClientTest`
- Use constants for repeated values (e.g., `maxTokens = 7680`)
- Verify prompt content more precisely in tests
- Consider property-based testing for configuration values

### 11. Build Improvements
- Refine SonarCloud exclusion rule (use `*.yml` instead of `**`)
- Consider dedicated Gradle task for code analysis
- Explore `gradle/gradle-build-action` for better caching

## 🚫 Not Needed / Out of Scope

### Won't Do
- **Dedicated GitHub Action**: Current scale doesn't require it
- **Optional<Boolean> return**: `Boolean` is sufficient
- **Remove @SpringBootTest**: Integration tests need full context
- **MockedStatic concerns**: Necessary for Spring AI ChatClient API

## 📊 Priority Order

1. ✅ Fix CI test failures (BLOCKING)
2. ✅ Add error handling to `hasExistingReview`
3. ✅ Improve specific exception handling
4. ✅ Fix test exception assertions
5. 🤔 Consider filtering reviews by AI agent
6. ⏳ Add comprehensive logging
7. ⏳ Improve test coverage

---

## Notes
- Focus on stability and error handling first
- Test improvements should follow functional fixes
- Configuration and code quality are continuous improvements
