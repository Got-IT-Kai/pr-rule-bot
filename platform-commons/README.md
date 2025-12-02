# Platform Commons

**Version**: 0.1.0
**Purpose**: Shared platform infrastructure for microservices

---

## Purpose

This module contains strictly limited shared code that meets the criteria defined in the [Common Module Policy](../../internal/docs/policy/common-module-policy.md).

**Important**: This module has zero business logic and extremely low change frequency.

---

## What's Included

### 1. Correlation (Request Tracing)
- `CorrelationId`: Unique identifier for tracking requests across services

### 2. Shared Configuration (application-platform.yml)
Common configuration imported by all microservices via `spring.config.import`:

#### GitHub API Configuration
- Shared GitHub token configuration with empty default (`${GITHUB_TOKEN:}`)
- Common base URL, timeout, and retry settings
- Used by: context-service, integration-service

#### Configuration Management Pattern
All services import platform configuration:
```yaml
spring:
  config:
    import: classpath:application-platform.yml
```

This provides:
- Consistent security patterns (empty defaults for sensitive values)
- Centralized configuration management
- Single source of truth for shared properties
- See: internal/docs/architecture/ADR-0017-configuration-management-patterns.md

---

## What's NOT Included

Following the Common Module Policy, this module **does not** contain:

- ❌ Domain models (User, PullRequest, etc.)
- ❌ Business logic (ValidationService, CalculationService, etc.)
- ❌ Application configuration (application.yml, etc.)
- ❌ Domain events (see event-schemas instead)

---

## Usage

### Adding Dependency

```kotlin
// In service build.gradle.kts
dependencies {
    implementation(project(":platform-commons"))
}
```

### Using CorrelationId

```java
import com.code.platform.correlation.CorrelationId;

public class MyService {
    public void processRequest() {
        String correlationId = CorrelationId.generate();
        // Use correlationId for distributed tracing
    }
}
```

---

## Before Adding Anything

Ask these questions:

1. Contains business logic? → **Reject**
2. Used by fewer than 3 services? → **Reject**
3. High change frequency? → **Reject**
4. Not technical infrastructure? → **Reject**

Only proceed if all answers are satisfactory.

---

## Related Documents

- [Common Module Policy](../../internal/docs/policy/common-module-policy.md)
- [Event Schemas](../event-schemas/README.md)

---

**Last Updated**: 2025-11-20
