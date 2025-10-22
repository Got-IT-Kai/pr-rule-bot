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
- [License](#license)

---

## Why This Project?

Traditional code reviews are time-consuming and often miss subtle issues like security vulnerabilities, performance bottlenecks, or architectural inconsistencies. This bot addresses these challenges by:

- **Automated Analysis**: Instantly reviews PRs for code quality, security, performance, and best practices
- **Consistent Standards**: Applies uniform review criteria across all pull requests
- **Developer Productivity**: Frees developers to focus on high-value architectural discussions
- **Continuous Learning**: Improves code quality feedback based on evolving patterns

The project demonstrates production-grade software engineering practices including reactive programming, comprehensive testing, and systematic decision-making through Architecture Decision Records.

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

**Current Implementation (Monolith):**
```
                          GitHub Platform
                                │
                                v
                         ┌─────────────┐
                         │   Webhook   │
                         │  Controller │
                         └──────┬──────┘
                                │
                                v
                         ┌─────────────┐
                         │    Agent    │
                         │ Application │
                         │             │
                         │ • GitHub    │
                         │ • AI Review │
                         │ • Webhook   │
                         └─────────────┘
```

**Planned Architecture (Microservices - See ADR-0015):**
```
                          GitHub Platform
                                │
                                v
                         ┌─────────────┐
                         │ API Gateway │
                         └──────┬──────┘
                                │
                    ┌───────────┴───────────┐
                    v                       v
            ┌──────────────┐        ┌──────────────┐
            │   Webhook    │        │ Integration  │
            │   Service    │        │   Service    │
            └──────┬───────┘        └──────▲───────┘
                   │                       │
                   v                       │
              ┌─────────┐                  │
              │  Kafka  │──────────────────┘
              └────┬────┘
                   │
        ┌──────────┼──────────┐
        v          v          v
   ┌────────┐ ┌────────┐ ┌─────────┐
   │Context │ │ Policy │ │ Review  │
   │Service │ │Service │ │ Service │
   └────────┘ └────────┘ └─────────┘
```

### Design Patterns & Principles

**Current Implementation:**
- **Reactive Streams**: Non-blocking I/O throughout the stack with Spring WebFlux
- **Hexagonal Architecture**: Ports and adapters pattern for clean separation
- **Strategy Pattern**: Pluggable AI providers (Gemini, Ollama)

**Planned Features (ADR-0015, ADR-0016):**
- **Microservices Architecture**: 5 independent services with clear boundaries
- **Event-Driven Architecture**: Asynchronous communication via Kafka
- **Independent Deployment**: Zero-downtime rolling updates per service
- **Auto-Scaling**: Kubernetes HPA based on metrics

### Architectural Decisions

All major technical decisions are documented as [Architecture Decision Records](docs/adr/README.md), including:

**Foundation:**
- [ADR-0001: Non-Blocking I/O](docs/adr/0001-non-blocking-io.md) - Spring WebFlux reactive architecture
- [ADR-0015: Microservices Architecture](docs/adr/0015-microservices-architecture.md) - MSA with Kafka and Kubernetes
- [ADR-0016: Kubernetes Deployment](docs/adr/0016-kubernetes-deployment-strategy.md) - Orchestration and scaling

**Safety & Security:**
- [ADR-0003: Webhook Security](docs/adr/0003-webhook-security.md) - HMAC signature verification

**Operations:**
- [ADR-0014: OpenTelemetry from Day 1](docs/adr/0014-opentelemetry-from-day-1.md) - Observability and metrics

---

## Technical Stack

**Current Implementation:**

| Technology | Version | Purpose |
|------------|---------|---------|
| **Java** | 21 | Modern language features (records, pattern matching, virtual threads) |
| **Spring Boot** | 3.5.3 | Application framework with reactive web support |
| **Spring WebFlux** | 6.2.x | Reactive web framework for non-blocking HTTP |
| **Project Reactor** | 3.7.x | Reactive streams implementation (Mono/Flux) |
| **Google Gemini** | 2.0 Flash | Cloud-based LLM for code analysis |
| **Ollama** | Latest | Local LLM runtime for privacy-focused development |
| **Spring AI** | Latest | AI provider abstraction framework |
| **Gradle** | 8.5+ | Build automation with Kotlin DSL |
| **JUnit 5** | 5.11.x | Unit and integration testing framework |
| **Mockito** | 5.x | Mocking framework for unit tests |
| **JaCoCo** | 0.8.x | Code coverage analysis |
| **SonarCloud** | - | Continuous code quality monitoring |
| **BlockHound** | Latest | Reactive blocking call detection |
| **GitHub Actions** | - | CI/CD automation |

**Planned Technologies (ADR-0015, ADR-0016):**

| Technology | Version | Purpose |
|------------|---------|---------|
| **Apache Kafka** | 3.7.0 | Event streaming platform (KRaft mode) |
| **Kubernetes** | 1.28+ | Container orchestration and scaling |
| **PostgreSQL** | 16+ | Per-service relational database |
| **Redis** | 7+ | Shared caching layer |
| **OpenTelemetry** | Latest | Observability (metrics, traces, logs) |

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
curl -X POST http://localhost:8080/api/v1/webhooks/github/pull_request \
  -H "Content-Type: application/json" \
  -H "X-GitHub-Event: pull_request" \
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
   - **Payload URL**: `https://your-server.com/api/v1/webhooks/github/pull_request`
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
Bot receives event at /api/v1/webhooks/github/pull_request
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

**Multi-Module Microservices Architecture:**

```
pr-rule-bot/
├── common/                    # Shared module
│   ├── src/main/java/com/code/common/
│   │   ├── dto/              # Data Transfer Objects
│   │   ├── event/            # Domain events for Kafka
│   │   ├── constant/         # Shared constants
│   │   └── exception/        # Common exceptions
│   └── build.gradle.kts
│
├── webhook-service/           # Port 8080 - GitHub webhook receiver
│   ├── src/main/java/com/code/webhook/
│   │   ├── controller/       # Webhook endpoints
│   │   ├── security/         # Signature verification
│   │   └── publisher/        # Kafka event publishing
│   ├── src/main/resources/
│   │   └── application.yml
│   └── build.gradle.kts
│
├── context-service/           # Port 8081 - Context collection
│   ├── src/main/java/com/code/context/
│   │   ├── service/          # Context analysis logic
│   │   ├── github/           # GitHub API client
│   │   └── repository/       # Database access
│   ├── src/main/resources/
│   │   └── application.yml
│   └── build.gradle.kts
│
├── policy-service/            # Port 8082 - Policy evaluation
│   ├── src/main/java/com/code/policy/
│   │   ├── service/          # Policy engine
│   │   ├── model/            # Policy definitions
│   │   └── repository/       # Policy storage
│   ├── src/main/resources/
│   │   └── application.yml
│   └── build.gradle.kts
│
├── review-service/            # Port 8083 - AI review orchestration
│   ├── src/main/java/com/code/review/
│   │   ├── service/          # Review orchestration
│   │   ├── ai/               # AI provider integration
│   │   └── aggregator/       # Result aggregation
│   ├── src/main/resources/
│   │   └── application.yml
│   └── build.gradle.kts
│
├── integration-service/       # Port 8084 - GitHub API integration
│   ├── src/main/java/com/code/integration/
│   │   ├── service/          # GitHub operations
│   │   ├── checkrun/         # Check Run API
│   │   ├── sarif/            # SARIF upload
│   │   └── comment/          # PR comment posting
│   ├── src/main/resources/
│   │   └── application.yml
│   └── build.gradle.kts
│
├── docs/
│   ├── adr/                   # Architecture Decision Records
│   ├── code-review/           # Historical review reports
│   ├── issue/                 # Issue management guides
│   ├── lessons/               # Lessons learned
│   └── release/               # Release planning
├── .github/
│   ├── workflows/             # GitHub Actions CI/CD
│   └── ISSUE_TEMPLATE/        # Issue templates
├── settings.gradle.kts        # Multi-module configuration
├── build.gradle.kts           # Root build configuration
└── README.md                  # This file
```

**Note**: The microservices architecture (ADR-0015) is currently under implementation. Services are scaffolded with basic Spring Boot applications. Full functionality including Kafka integration, database access, and business logic will be added in subsequent issues.

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

### Additional Resources

- **[Release Management](docs/release/README.md)** - Release planning and process
- **[Code Review Reports](docs/code-review/README.md)** - Historical code review findings
- **[Lessons Learned](docs/lessons/README.md)** - Technical insights and solutions
  - [Ollama to Gemini Migration](docs/lessons/01-ollama-to-gemini-migration.md)
  - [Reactive Programming Pitfalls](docs/lessons/02-reactive-programming-pitfalls.md)
  - [GCP Workload Identity Federation](docs/lessons/04-gcp-workload-identity-federation-setup.md)
- **[Issue Templates](.github/ISSUE_TEMPLATE/)** - Bug reports, features, security

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
