# ADR-0009: Gradle Build Configuration Language

**Date:** 2025-10-06
**Status:** Accepted

## Context

The project currently uses Gradle Groovy DSL for build configuration (`build.gradle`). Several issues have emerged:

### Build Script Maintainability
- No type safety in build scripts - errors only discovered at runtime
- Limited IDE support - poor autocomplete and navigation in IntelliJ IDEA
- Difficult to refactor - no compile-time guarantees when changing APIs
- String-based configuration prone to typos

### Test Configuration Issues
- JVM Test Suite tasks don't properly chain - `check` task skips already-executed tests due to UP-TO-DATE optimization
- Tests must be run individually for reliable execution
- Task dependency graph is implicit and fragile

### Industry Trends
- Gradle Kotlin DSL is becoming the de facto standard for new projects
- Better tooling support in modern IDEs (especially IntelliJ)
- Type-safe build scripts enable better refactoring and maintenance

## Decision

Migrate from Gradle Groovy DSL to Gradle Kotlin DSL:

1. **Convert `build.gradle` to `build.gradle.kts`**
2. **Use Kotlin DSL syntax** for all build configuration
3. **Improve test task dependencies** to ensure reliable execution:
   - Maintain source set separation (unit tests in `src/test`, integration tests in `src/integrationTest`)
   - Fix task dependency chain for reliable `check` execution
4. **Centralize dependency versions** using type-safe property accessors

## Consequences

### Positive

- **Type Safety**: Compile-time error detection in build scripts
- **IDE Support**: Full autocomplete, navigation, and refactoring in IntelliJ IDEA
- **Discoverability**: Better API discovery through IDE suggestions
- **Maintainability**: Easier refactoring with type-safe APIs
- **Industry Standard**: Aligns with modern Gradle best practices
- **Reliable Testing**: Improved test task dependencies ensure consistent execution

### Negative

- **Build Performance**: Slightly slower initial build due to Kotlin compilation
- **Learning Curve**: Must learn Kotlin DSL syntax (minor - very similar to Groovy)
- **Migration Effort**: One-time cost to convert existing build script

## Alternatives Considered

### Alternative 1: Keep Groovy DSL with Improvements

**Description:** Fix test configuration issues while staying with Groovy DSL

**Pros:**
- No migration effort
- Familiar syntax
- Faster build times

**Cons:**
- No type safety
- Poor IDE support continues
- Misses opportunity to modernize
- Test reliability issues persist due to dynamic nature

**Why rejected:** Doesn't address fundamental maintainability and IDE support issues.

### Alternative 2: Gradle Version Catalogs Only

**Description:** Use Gradle Version Catalogs for dependency management but keep Groovy DSL

**Pros:**
- Centralized dependency management
- Works with Groovy DSL
- Type-safe dependency declarations

**Cons:**
- Doesn't solve IDE support issues
- No type safety for build logic
- Half-measure that doesn't fully solve the problem

**Why rejected:** Only addresses dependency management, not the broader build script maintainability issues.

### Alternative 3: Complete Migration to Kotlin + Kotlin DSL

**Description:** Convert entire project to Kotlin, not just build scripts

**Pros:**
- Unified language for code and build
- Modern language features
- Better null safety

**Cons:**
- Massive migration effort
- Team must learn Kotlin
- Out of scope for current issue

**Why rejected:** Too large of a scope change; build script migration provides most benefits with minimal effort.

## Implementation

### Migration Steps

1. **Rename build file**: `build.gradle` → `build.gradle.kts`
2. **Convert Groovy syntax to Kotlin**:
   - String quotes: `'dependency'` → `"dependency"`
   - Map syntax: `key: value` → `"key" to value`
   - Method calls: parentheses and explicit parameters
   - Lambdas: `{}` with `it` or named parameters
3. **Fix test configuration**:
   - Explicit task dependencies
   - Ensure `test` and `integrationTest` always run before `check`
   - Remove UP-TO-DATE issues with proper task ordering
4. **Centralize versions**: Use `val` properties in `ext` block
5. **Test thoroughly**: Verify all tasks work correctly

### Test Configuration Pattern

Following [Gradle official documentation](https://docs.gradle.org/current/userguide/jvm_test_suite_plugin.html):

```kotlin
testing {
    suites {
        // Common configuration for all test suites
        withType(JvmTestSuite::class).configureEach {
            useJUnitJupiter()
            dependencies {
                implementation("org.springframework.boot:spring-boot-starter-test")
                implementation("io.projectreactor:reactor-test")
            }
        }

        // Unit tests (BlockHound enabled)
        val test by getting(JvmTestSuite::class) {
            dependencies {
                implementation("org.mockito:mockito-inline:...")
                implementation("io.projectreactor.tools:blockhound:...")
            }
        }

        // Integration tests (BlockHound disabled)
        val integrationTest by registering(JvmTestSuite::class) {
            dependencies {
                implementation(project())
                implementation("com.squareup.okhttp3:mockwebserver:...")
            }
            targets {
                all {
                    testTask.configure {
                        shouldRunAfter(test)
                    }
                }
            }
        }
    }
}

// Ensure check runs both test suites
tasks.named("check") {
    dependsOn(tasks.named("test"))
    dependsOn(tasks.named("integrationTest"))
}
```

**Note:** JVM Test Suite Plugin is marked `@Incubating` but is the [recommended approach](https://blog.gradle.org/introducing-test-suites) by Gradle team for modern projects.

## Validation

**Success Criteria:**
- Build succeeds with `build.gradle.kts`
- All tests pass: `./gradlew test integrationTest`
- `./gradlew check` reliably runs all tests
- IDE autocomplete works in build script
- No blocking calls detected in unit tests (BlockHound)

**Performance Benchmark:**
- Measure clean build time before/after migration
- Accept up to 10% build time increase for type safety benefits

**Note:** Actual validation results should be documented in the PR description when implementing this ADR. See PR template "ADR Validation Results" section for format.

## References

- [Gradle Kotlin DSL Primer](https://docs.gradle.org/current/userguide/kotlin_dsl.html)
- [Migrating build logic from Groovy to Kotlin](https://docs.gradle.org/current/userguide/migrating_from_groovy_to_kotlin_dsl.html)
- [JVM Test Suite Plugin](https://docs.gradle.org/current/userguide/jvm_test_suite_plugin.html)
- Related: Issue #55
