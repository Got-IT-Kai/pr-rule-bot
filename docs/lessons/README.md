# Lessons Learned

This directory contains technical lessons and architectural decisions from the project.

## Lessons Index

### 1. [Ollama to Gemini Migration](./01-ollama-to-gemini-migration.md)
**Topic:** AI Provider Selection and Performance Optimization

**Key Takeaways:**
- Cost vs performance trade-offs in CI/CD
- LLM selection criteria for automated code review
- Workload Identity Federation for secure authentication

**Technologies:** Ollama, Google Gemini, Docker, GitHub Actions, GCP

---

### 2. [Reactive Programming Pitfalls](./02-reactive-programming-pitfalls.md)
**Topic:** Common Mistakes with Spring WebFlux and Reactor

**Key Takeaways:**
- Avoid `.subscribe()` in business logic
- Use `.block()` only at entry points
- Proper error handling with reactive operators
- Testing with StepVerifier

**Technologies:** Spring WebFlux, Project Reactor, BlockHound

---

### 3. [Spring Boot CLI Graceful Shutdown](./03-spring-boot-cli-shutdown.md)
**Topic:** Proper Termination for CLI Applications

**Key Takeaways:**
- `ApplicationRunner` vs application lifecycle
- `SpringApplication.exit()` for graceful shutdown
- Profile-based architecture (CLI vs Server)
- Exit codes for CI/CD integration

**Technologies:** Spring Boot, ApplicationRunner, GitHub Actions

---

### 4. [GCP Workload Identity Federation Setup](./04-gcp-workload-identity-federation-setup.md)
**Topic:** Keyless Authentication for GitHub Actions to GCP

**Key Takeaways:**
- OIDC-based authentication (no JSON keys)
- Step-by-step WIF configuration
- Security best practices and troubleshooting
- Comparison: JSON keys vs WIF

**Technologies:** GCP, IAM, Workload Identity Federation, GitHub OIDC

---

## How to Use

### For Learning
- Each lesson can be read independently
- Includes practical code examples
- Clear distinction between good and bad practices

### For Reference
- Consult when facing similar problems
- Use as code review checklist
- Reference material for future development

### For Future Projects
- Reference for architecture decisions
- Technology stack selection criteria
- Known issues and solutions

## Lesson Template

When adding new lessons, follow this structure:

```markdown
# Lesson: [Title]

**Date:** YYYY-MM-DD

## Problem Statement
What problem was encountered?

## Root Cause Analysis
Why did it happen?

## Solutions Explored
What was tried?

## Key Lessons Learned
What was learned?

## Best Practices
What should be done?

## Related Files
Links to relevant code

## References
External resources
```

## Categories

- **Architecture:** 01, 03
- **Performance:** 01
- **Security:** 01, 04
- **Reactive Programming:** 02
- **Testing:** 02, 03
- **CI/CD:** 01, 03, 04
- **Infrastructure:** 04

## Continuous Learning

Lessons are continuously added as the project evolves:

- Completed lessons: 4
- Upcoming topics:
  - Error handling strategies
  - Configuration management best practices
  - Test architecture improvements
  - Monitoring and observability

## Contributing

To add a new lesson:

1. Follow the template to create `XX-topic-name.md`
2. Include practical code examples
3. Clearly distinguish Do's and Don'ts
4. Add link to this README

---

> "The only real mistake is the one from which we learn nothing." - Henry Ford
