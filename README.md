# PR Rule Bot

[![Build Status](https://img.shields.io/github/actions/workflow/status/Got-IT-Kai/pr-rule-bot/ci.yml?branch=master)](https://github.com/Got-IT-Kai/pr-rule-bot/actions)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=Got-IT-Kai_pr-rule-bot&metric=alert_status)](https://sonarcloud.io/dashboard?id=Got-IT-Kai_pr-rule-bot)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=Got-IT-Kai_pr-rule-bot&metric=coverage)](https://sonarcloud.io/summary/new_code?id=Got-IT-Kai_pr-rule-bot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.3-brightgreen.svg)](https://spring.io/projects/spring-boot)

**AI-powered code review automation for GitHub pull requests**

An intelligent code review bot leveraging Large Language Models to provide comprehensive, context-aware feedback on pull requests. Built on a fully reactive architecture using Spring WebFlux and Project Reactor for high-performance, non-blocking I/O operations.

---

## Table of Contents

- [Why This Project?](#why-this-project)
- [Key Features](#key-features)
- [Architecture Highlights](#architecture-highlights)
- [Technical Stack](#technical-stack)
- [Getting Started](#getting-started)
- [Usage](#usage)
- [Configuration](#configuration)
- [Development](#development)
- [Documentation](#documentation)
- [Roadmap](#roadmap)
- [License](#license)

---

## Why This Project?

Traditional code reviews are time-consuming and often miss subtle issues like security vulnerabilities, performance bottlenecks, or architectural inconsistencies. This bot addresses these challenges by:

- **Automated Analysis**: Instantly reviews PRs for code quality, security, performance, and best practices
- **Consistent Standards**: Applies uniform review criteria across all pull requests
- **Developer Productivity**: Frees developers to focus on high-value architectural discussions
- **Continuous Learning**: Improves code quality feedback based on evolving patterns

The project demonstrates production-grade software engineering practices including reactive programming, microservices architecture, comprehensive testing, and systematic decision-making through Architecture Decision Records.

---

## Key Features

### 🤖 AI-Powered Code Review
- **Multi-Model Support**: Google Gemini (cloud) and Ollama (local) with abstracted AI provider interface
- **Intelligent Analysis**: Detects security vulnerabilities, code smells, performance issues, and architectural problems
- **Context-Aware Feedback**: Understands code context and provides actionable, specific suggestions
- **Smart Token Management**: Implements context-aware chunking strategy for files exceeding LLM token limits ([ADR-0008](docs/adr/0008-token-chunking-strategy.md))

### ⚡ Reactive Architecture
- **Non-Blocking I/O**: Built on Spring WebFlux and Project Reactor for optimal resource utilization ([ADR-0001](docs/adr/0001-non-blocking-io.md))
- **Backpressure Handling**: Reactive streams manage load gracefully under high request volumes
- **Event-Driven Design**: Asynchronous event processing with proper error handling and retry logic
- **Performance**: Significantly reduced latency and improved throughput compared to blocking alternatives

### 🔐 Enterprise-Grade Security
- **Webhook Verification**: HMAC-SHA256 signature validation for GitHub webhooks ([ADR-0003](docs/adr/0003-webhook-security.md))
- **Secret Management**: Environment-based configuration with no hardcoded credentials
- **Token Sanitization**: GitHub tokens automatically scrubbed from logs
- **Prompt Injection Defense**: AI prompt validation to prevent malicious input exploitation

### 📊 Quality & Observability
- **82%+ Test Coverage**: Comprehensive unit and integration test suites with JaCoCo reporting
- **Continuous Quality Monitoring**: SonarCloud integration for static analysis and quality gates
- **Structured Logging**: Contextual logging for debugging and audit trails
- **GitHub Actions CI/CD**: Automated testing, building, and quality checks

### 🔄 GitHub Integration
- **Webhook Events**: Real-time PR event processing (opened, synchronized, reopened)
- **GitHub Actions**: Automated review workflow triggered on PR events ([ADR-0002](docs/adr/0002-automate-pr-reviews-via-github-actions.md))
- **Bot Identity Management**: Prevents duplicate reviews and proper bot identification ([ADR-0004](docs/adr/0004-bot-identity-management.md))
- **Comment Synthesis**: Aggregates review findings into structured, actionable PR comments

---

## Architecture Highlights

### System Architecture

```
┌──────────────────┐
│  GitHub Events   │
│  (Webhooks/API)  │
└────────┬─────────┘
         │
         v
┌──────────────────┐
│ Security Filter  │  HMAC-SHA256 Signature Verification
│ (Webhook Auth)   │
└────────┬─────────┘
         │
         v
┌──────────────────┐
│ REST Controller  │  WebFlux Non-Blocking Controllers
│ (Reactive Web)   │
└────────┬─────────┘
         │
         v
┌──────────────────┐
│ Review Service   │  Core Business Logic
│ (Orchestration)  │  • Event Processing
└────────┬─────────┘  • Review Synthesis
         │           • Error Handling
         v
    ┌────┴────┐
    │         │
    v         v
┌────────┐ ┌──────────────┐
│GitHub  │ │ AI Adapter   │  Strategy Pattern
│Adapter │ │ (Abstraction)│  • Provider Selection
└────────┘ └──────┬───────┘  • Token Management
              │         │      • Retry Logic
              v         v
         ┌────────┐ ┌────────┐
         │Gemini  │ │Ollama  │
         │Provider│ │Provider│
         └────────┘ └────────┘
```

### Design Patterns & Principles

- **Reactive Streams**: Full reactive pipeline from HTTP layer to external service calls
- **Strategy Pattern**: Pluggable AI providers (Gemini, Ollama, extensible to OpenAI/Azure)
- **Adapter Pattern**: Abstracted GitHub and AI service integrations
- **Dependency Injection**: Spring Boot IoC for loose coupling and testability
- **Fail-Fast**: Input validation with Spring Validation and custom constraints
- **Error Handling**: Comprehensive exception handling with reactive error operators

### Architectural Decisions

All major technical decisions are documented as [Architecture Decision Records](docs/adr/README.md), including:

- [ADR-0001: Non-Blocking I/O](docs/adr/0001-non-blocking-io.md) - Why Spring WebFlux over traditional blocking I/O
- [ADR-0002: GitHub Actions Integration](docs/adr/0002-automate-pr-reviews-via-github-actions.md) - CI/CD automation strategy
- [ADR-0003: Webhook Security](docs/adr/0003-webhook-security.md) - HMAC signature verification implementation
- [ADR-0004: Bot Identity Management](docs/adr/0004-bot-identity-management.md) - Preventing duplicate reviews
- [ADR-0008: Token Chunking Strategy](docs/adr/0008-token-chunking-strategy.md) - Handling large files with LLM token limits

---

## Technical Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| **Java** | 21 | Modern language features (records, pattern matching, virtual threads) |
| **Spring Boot** | 3.5.3 | Application framework with reactive web support |
| **Spring WebFlux** | 6.2.x | Reactive web framework for non-blocking HTTP |
| **Project Reactor** | 3.7.x | Reactive streams implementation (Mono/Flux) |
| **Spring AI** | 1.0.0 | AI integration abstraction layer |
| **Google Gemini** | 2.0 Flash | Cloud-based LLM for code analysis |
| **Ollama** | Latest | Local LLM runtime (privacy-focused option) |
| **Gradle** | 8.5+ | Build automation with version catalogs |
| **JUnit 5** | 5.11.x | Unit and integration testing framework |
| **Mockito** | 5.2.0 | Mocking framework for unit tests |
| **JaCoCo** | Latest | Code coverage analysis |
| **SonarCloud** | - | Continuous code quality and security scanning |
| **GitHub Actions** | - | CI/CD automation |
| **BlockHound** | 1.0.8 | Reactive blocking call detection (dev/test) |

---

## Getting Started

### Prerequisites

- **Java 21+** ([Download](https://openjdk.org/projects/jdk/21/))
- **Gradle 8.5+** (included via wrapper)
- **Docker Desktop** (optional, for local Ollama)
- **GitHub Account** (for webhook/API integration)

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/Got-IT-Kai/pr-rule-bot.git
   cd pr-rule-bot
   ```

2. **Configure environment variables**

   Create a `.env` file in the project root:
   ```bash
   # GitHub Configuration
   GITHUB_TOKEN=ghp_your_personal_access_token_here
   GITHUB_WEBHOOK_SECRET=your_secure_webhook_secret

   # AI Provider - Choose one of the following options:

   # Option 1: Google AI Studio API Key (Quick Start - Development)
   # Get your API key from: https://aistudio.google.com/app/apikey
   # Pros: Simple setup, free tier available
   # Cons: Lower rate limits (15 req/min), API key in environment
   # WARNING: For production, use secrets manager (e.g., GCP Secret Manager)
   GOOGLE_GEMINI_API_KEY=YOUR_API_KEY_HERE
   AI_PROVIDER=gemini

   # Option 2: GCP Vertex AI with Workload Identity (Production - Recommended)
   # Requires GCP project and Workload Identity Federation setup
   # See: docs/lessons/04-gcp-workload-identity-federation-setup.md
   # Pricing: https://cloud.google.com/vertex-ai/pricing
   # Pros: Secure OIDC authentication, higher rate limits, no API key exposure
   # Cons: More complex setup
   GCP_PROJECT_ID=your-gcp-project-id
   GCP_LOCATION=us-central1
   AI_PROVIDER=gemini
   # Note: No API key needed - uses WIF authentication in CI/CD

   # Option 3: Local Ollama (Privacy-focused - No API key needed)
   # Run LLM locally without cloud dependencies
   OLLAMA_BASE_URL=http://localhost:11434
   OLLAMA_MODEL=llama3  # or llama2, qwen2.5-coder:3b, etc.
   AI_PROVIDER=ollama
   ```

3. **Build the application**
   ```bash
   ./gradlew clean build
   ```

4. **Run the application**
   ```bash
   ./gradlew bootRun
   ```

   The service will start on `http://localhost:8080`

### Quick Test

Test the webhook endpoint:
```bash
curl -X POST http://localhost:8080/api/webhook \
  -H "Content-Type: application/json" \
  -H "X-Hub-Signature-256: sha256=test" \
  -d '{"action": "opened", "pull_request": {...}}'
```

---

## Usage

### Server Mode (Production)

The bot runs as a persistent web service that listens for GitHub webhook events.

#### 1. Deploy the Application

Deploy to your preferred platform (e.g., Cloud Run, EC2, Kubernetes):

```bash
# Build JAR
./gradlew clean build

# Run server
java -jar build/libs/pr-rule-bot-0.0.1-SNAPSHOT.jar
```

Or use Docker:

```bash
# Build image
docker build -t pr-rule-bot:latest .

# Run container
docker run -p 8080:8080 \
  -e GITHUB_TOKEN=$GITHUB_TOKEN \
  -e GITHUB_WEBHOOK_SECRET=$WEBHOOK_SECRET \
  -e GOOGLE_GEMINI_API_KEY=$GEMINI_KEY \
  pr-rule-bot:latest
```

#### 2. Configure GitHub Webhook

Set up webhook in your target repository:

1. Navigate to **Settings → Webhooks → Add webhook**
2. Configure:
   - **Payload URL**: `https://your-server.com/api/webhook`
   - **Content type**: `application/json`
   - **Secret**: Your webhook secret (matches `GITHUB_WEBHOOK_SECRET`)
   - **Events**: Select "Pull requests"
   - **Active**: ✓ Enabled
3. Save webhook

#### 3. Automatic Reviews

Once configured, the bot automatically reviews PRs when:
- New PR is opened
- PR is updated with new commits
- PR is synchronized with base branch

Example workflow:
```
Developer opens PR
    ↓
GitHub sends webhook event
    ↓
Bot receives event at /api/webhook
    ↓
Signature verification (HMAC-SHA256)
    ↓
Fetch PR diff and metadata
    ↓
AI analyzes code changes
    ↓
Post review comments on PR
```

### CLI Mode (Development/Testing)

For testing or CI/CD integration, the bot supports CLI mode that runs once and exits.

This mode requires `ci` profile and reads PR information from environment variables or `application.yml`:

```yaml
# Configuration (application.yml)
cli:
  pr-number: ${PR_NUMBER:0}
  repository:
    owner: ${REPO_OWNER:kai}
    name: ${REPO_NAME:pr-rule-bot}
  time-out-minutes: 20
```

**Run CLI mode:**

```bash
# Using Gradle
SPRING_PROFILES_ACTIVE=ci \
GITHUB_TOKEN=ghp_xxx \
PR_NUMBER=123 \
REPO_OWNER=your-org \
REPO_NAME=your-repo \
AI_PROVIDER=gemini \
./gradlew bootRun

# Or using JAR directly
java -jar build/libs/pr-rule-bot-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=ci \
  --cli.pr-number=123 \
  --cli.repository.owner=your-org \
  --cli.repository.name=your-repo
```

**GitHub Actions Integration:**

See `.github/workflows/ai-code-review.yml` for a complete example of using CLI mode in CI/CD:

```yaml
- name: Run AI Code Review with Gemini
  env:
    GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
    PR_NUMBER: ${{ github.event.pull_request.number }}
    REPO_OWNER: ${{ github.repository_owner }}
    REPO_NAME: ${{ github.event.repository.name }}
    AI_PROVIDER: gemini
    SPRING_PROFILES_ACTIVE: ci
  run: |
    ./gradlew bootRun --no-daemon
```

This mode is used to test the bot on its own PRs without running a persistent server.

### Local Development with Ollama

For privacy-sensitive or offline development:

```bash
# Start Ollama
docker pull ollama/ollama
docker run -d -p 11434:11434 --name ollama ollama/ollama

# Pull a code review model
docker exec ollama ollama pull llama3.1:8b

# Configure application to use Ollama
export OLLAMA_BASE_URL=http://localhost:11434
export OLLAMA_MODEL=llama3.1:8b
export AI_PROVIDER=ollama

# Run bot
./gradlew bootRun
```

---

## Configuration

### Application Properties

Full configuration example (`src/main/resources/application.yml`):

```yaml
spring:
  ai:
    # Google Gemini Configuration
    vertex:
      ai:
        gemini:
          project-id: ${GCP_PROJECT_ID}
          location: us-central1
          model: gemini-2.0-flash-exp
          temperature: 0.3
          max-tokens: 8192

    # Ollama Configuration (alternative)
    ollama:
      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
      model: ${OLLAMA_MODEL:llama3.1:8b}

github:
  api:
    base-url: https://api.github.com
    timeout: 30s
  webhook:
    secret: ${GITHUB_WEBHOOK_SECRET}
  token: ${GITHUB_TOKEN}

logging:
  level:
    com.code: DEBUG
    reactor.netty: INFO
```

### AI Provider Selection

The bot supports multiple AI providers through Spring AI abstractions:

| Provider | Setup Complexity | Rate Limits | Pros | Cons | Use Case |
|----------|------------------|-------------|------|------|----------|
| **Google AI Studio API Key** | ⭐ Easy | 15 req/min (free tier) | Free tier, simple setup, fast | API key exposure risk, lower rate limits | Quick testing, development |
| **GCP Vertex AI (WIF)** | ⭐⭐⭐ Complex | [Quota limits](https://cloud.google.com/vertex-ai/docs/quotas) | Secure OIDC auth, high rate limits, no API key exposure, production-ready | Complex setup, requires GCP project | Production deployments, CI/CD |
| **Ollama (Local)** | ⭐⭐ Medium | None (local) | Privacy, no API costs, offline, no rate limits | Slower, requires GPU/resources, local setup | Development, sensitive code, air-gapped environments |
| **OpenAI** *(extensible)* | ⭐ Easy | [Tier-based](https://platform.openai.com/docs/guides/rate-limits) | Industry standard, GPT-4 quality | Higher cost, requires API key | Premium quality needs |

**Setup Guides:**
- **Google AI Studio**: Get API key from [Google AI Studio](https://aistudio.google.com/app/apikey)
- **GCP Vertex AI**: See [Workload Identity Federation Setup](docs/lessons/04-gcp-workload-identity-federation-setup.md) and [Pricing](https://cloud.google.com/vertex-ai/pricing)
- **Ollama**: See "Local Development with Ollama" section above

**Configuration:**
- Set provider in `application.yml` using `ai.provider` property
- Or use environment variable: `AI_PROVIDER=gemini` or `AI_PROVIDER=ollama`

---

## Development

### Project Structure

```
pr-rule-bot/
├── src/
│   ├── main/java/com/code/
│   │   ├── adapter/           # External service integrations
│   │   │   ├── ai/            # AI provider adapters (Gemini, Ollama)
│   │   │   └── github/        # GitHub API client
│   │   ├── config/            # Spring configuration
│   │   ├── controller/        # REST/Webhook controllers
│   │   ├── model/             # Domain models & DTOs
│   │   │   ├── event/         # GitHub event models
│   │   │   └── review/        # Review domain models
│   │   ├── service/           # Business logic
│   │   └── util/              # Utilities & helpers
│   ├── main/resources/
│   │   ├── application.yml    # Configuration
│   │   └── logback-spring.xml # Logging configuration
│   ├── test/                  # Unit tests
│   └── integrationTest/       # Integration tests
├── docs/
│   ├── adr/                   # Architecture Decision Records
│   ├── code-review/           # Historical review reports
│   ├── issue/                 # Issue management guides
│   ├── lessons/               # Lessons learned
│   └── release/               # Release planning
├── .github/
│   ├── workflows/             # GitHub Actions CI/CD
│   └── ISSUE_TEMPLATE/        # Issue templates
├── build.gradle               # Gradle build configuration
└── README.md                  # This file
```

### Running Tests

```bash
# Unit tests only
./gradlew test

# Integration tests only
./gradlew integrationTest

# All tests with coverage report
./gradlew clean build jacocoMergeReport
```

View coverage report: `build/reports/jacoco/jacocoMergeReport/html/index.html`

### Code Quality Checks

```bash
# Run SonarCloud analysis
./gradlew sonar -Dsonar.login=$SONAR_TOKEN

# Local quality checks (without SonarCloud)
./gradlew check
```

### Testing Reactive Code

The project uses **BlockHound** to detect blocking calls in reactive pipelines during testing:

```java
@BeforeAll
static void setupBlockHound() {
    BlockHound.install(builder -> builder
        .allowBlockingCallsInside("java.io.FileInputStream", "read")
    );
}

@Test
void shouldNotBlockInReactivePipeline() {
    // Test will fail if blocking call detected
    StepVerifier.create(reactiveService.process())
        .expectNext(expectedValue)
        .verifyComplete();
}
```

### Development Best Practices

- **Reactive Programming**: All I/O operations use `Mono`/`Flux` - no blocking calls
- **Test Coverage**: Maintain 80%+ coverage (current: 82%)
- **ADR Documentation**: Document architectural decisions in `docs/adr/`
- **Code Reviews**: Self-review using the bot on development branches
- **Error Handling**: Use reactive error operators (`onErrorResume`, `retry`)

---

## Documentation

### Architecture Decision Records (ADRs)

All significant architectural decisions are documented with context, alternatives, and consequences:

- **[ADR Index](docs/adr/README.md)** - Complete list of ADRs
- **Infrastructure & Security**
  - [ADR-0003: Webhook Security](docs/adr/0003-webhook-security.md)
  - [ADR-0004: Bot Identity Management](docs/adr/0004-bot-identity-management.md)
- **Architecture & Performance**
  - [ADR-0001: Non-Blocking I/O](docs/adr/0001-non-blocking-io.md)
  - [ADR-0008: Token Chunking Strategy](docs/adr/0008-token-chunking-strategy.md)
- **Deferred to v2.0**
  - [ADR-0005: Rate Limiting Strategy](docs/adr/0005-rate-limiting.md)
  - [ADR-0006: Observability Strategy](docs/adr/0006-observability-strategy.md)
  - [ADR-0007: Circuit Breaker Pattern](docs/adr/0007-circuit-breaker-pattern.md)

### Additional Resources

- **[Release Management](docs/release/README.md)** - Release planning and process
- **[Code Review Reports](docs/code-review/README.md)** - Historical code review findings
- **[Lessons Learned](docs/lessons/README.md)** - Technical insights and solutions
  - [Ollama to Gemini Migration](docs/lessons/01-ollama-to-gemini-migration.md)
  - [Reactive Programming Pitfalls](docs/lessons/02-reactive-programming-pitfalls.md)
  - [GCP Workload Identity Federation](docs/lessons/04-gcp-workload-identity-federation-setup.md)
- **[Issue Templates](.github/ISSUE_TEMPLATE/)** - Bug reports, features, security

---

## Roadmap

### v1.0 - Production Ready (In Progress)

**Focus**: Core functionality with security and stability

- **Security Hardening**
  - [x] HMAC webhook signature verification
  - [ ] AI prompt injection protection
  - [ ] GitHub token log sanitization

- **Core Features**
  - [ ] Smart token chunking for large files
  - [ ] Bot identity management
  - [ ] Memory leak fixes
  - [ ] Event publisher optimization

- **Quality & Testing**
  - [x] 82% test coverage (unit + integration)
  - [ ] Improve AI adapter coverage (43% → 80%)
  - [ ] Webhook controller tests (10% → 80%)

- **Infrastructure**
  - [ ] Remove unused OTEL dependencies
  - [ ] Clean up vector database remnants
  - [ ] JavaDoc documentation

See [v1.0 Release Plan](docs/release/v1.0-plan.md) for detailed breakdown and [milestones](https://github.com/Got-IT-Kai/pr-rule-bot/milestones).

### v2.0 - Scale & Resilience (Planned)

**Focus**: Multi-repository support and operational excellence

- **Scalability**
  - Rate limiting with Redis/Bucket4j
  - Circuit breaker pattern (Resilience4j)
  - Distributed tracing (OpenTelemetry)

- **Advanced Features**
  - Multi-repository support
  - Custom review rule engine
  - Historical code quality trends
  - RAG-based context augmentation

- **Observability**
  - Prometheus metrics
  - Grafana dashboards
  - Distributed tracing (Tempo/Jaeger)
  - Alert management

---

## Contributing

This is a personal project, but contributions, suggestions, and feedback are welcome!

### How to Contribute

1. **Check existing issues**: [GitHub Issues](https://github.com/Got-IT-Kai/pr-rule-bot/issues)
2. **Review ADRs**: Understand architectural decisions in [docs/adr/](docs/adr/)
3. **Use issue templates**: Select appropriate template from [.github/ISSUE_TEMPLATE/](.github/ISSUE_TEMPLATE/)
4. **Follow conventions**: Match existing code style and reactive patterns
5. **Write tests**: Maintain 80%+ coverage for new code
6. **Update docs**: Add ADRs for architectural changes

### Issue Types

- **Bug Report**: [Template](.github/ISSUE_TEMPLATE/bug_report.md)
- **Feature Request**: [Template](.github/ISSUE_TEMPLATE/feature_request.md)
- **Security Issue**: [Template](.github/ISSUE_TEMPLATE/security.md)
- **Technical Debt**: [Template](.github/ISSUE_TEMPLATE/technical_debt.md)
- **Task/Enhancement**: [Template](.github/ISSUE_TEMPLATE/task.md)

---

## Security

### Security Measures

- **Webhook Signature Verification**: HMAC-SHA256 validation for all GitHub webhooks
- **Secret Management**: All credentials via environment variables, never committed
- **Token Sanitization**: GitHub tokens automatically redacted from logs
- **Input Validation**: Spring Validation for all incoming requests
- **AI Prompt Sanitization**: Defense against prompt injection attacks

### Reporting Vulnerabilities

Please use the [security issue template](.github/ISSUE_TEMPLATE/security.md) to report security vulnerabilities. Do not disclose security issues publicly until they are addressed.

---

## License

This project is licensed under the **Apache License 2.0** - see the [LICENSE](LICENSE) file for details.

```
Copyright 2025 Kai L.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

---

## Acknowledgments

- **[Spring AI](https://spring.io/projects/spring-ai)** - Unified AI integration abstraction
- **[Google Gemini](https://ai.google.dev/)** - Powerful and cost-effective LLM API
- **[Ollama](https://ollama.ai/)** - Privacy-focused local LLM runtime
- **[Project Reactor](https://projectreactor.io/)** - Reactive programming foundation
- **[Spring Framework](https://spring.io/)** - Enterprise Java application framework
- **[SonarCloud](https://sonarcloud.io/)** - Continuous code quality platform

---

**Maintained by**: [Got-IT-Kai](https://github.com/Got-IT-Kai)
**Repository**: [github.com/Got-IT-Kai/pr-rule-bot](https://github.com/Got-IT-Kai/pr-rule-bot)
**Documentation**: [docs/](docs/)
**Issues**: [GitHub Issues](https://github.com/Got-IT-Kai/pr-rule-bot/issues)
