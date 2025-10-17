# Current Architecture (Monolith)

**Status**: Implemented
**Architecture**: Monolith
**Last Updated**: 2025-10-18

---

## Overview

The current system is implemented as a **monolith application** using Spring Boot with WebFlux for reactive, non-blocking I/O. The application follows hexagonal architecture principles with clear separation between layers.

**Design principles:**
- Reactive programming throughout the stack
- Clean separation of concerns (hexagonal architecture)
- Strategy pattern for pluggable AI providers
- Non-blocking I/O for optimal resource utilization

---

## System Context

```
┌──────────────────────────────────────────────┐
│              GitHub Platform                  │
│  Webhooks, REST API, GraphQL, Comments       │
└───────────────────┬──────────────────────────┘
                    │ HTTPS
                    ▼
         ┌─────────────────────┐
         │  PR Rule Bot        │
         │  (Monolith)         │
         │                     │
         │  ┌───────────────┐  │
         │  │   Webhook     │  │
         │  │  Controller   │  │
         │  └───────┬───────┘  │
         │          │          │
         │          ▼          │
         │  ┌───────────────┐  │
         │  │ GitHub Client │  │
         │  │  (WebFlux)    │  │
         │  └───────┬───────┘  │
         │          │          │
         │          ▼          │
         │  ┌───────────────┐  │
         │  │  AI Review    │  │
         │  │   Service     │  │
         │  └───────┬───────┘  │
         │          │          │
         │          ▼          │
         │  ┌───────────────┐  │
         │  │ AI Providers  │  │
         │  │ (Gemini/      │  │
         │  │  Ollama)      │  │
         │  └───────────────┘  │
         └─────────────────────┘
                    │
                    ▼
         ┌─────────────────────┐
         │   Google Gemini     │
         │   or Ollama         │
         └─────────────────────┘
```

---

## Application Structure

### Package Layout

```
com.code.agent/
├── presentation/           # REST API & Webhook endpoints
│   └── web/
│       └── GitHubWebhookController.java
├── application/           # Business logic orchestration
│   ├── service/
│   │   └── CodeReviewOrchestrationService.java
│   └── listener/
│       └── PullRequestEventListener.java
├── domain/                # Domain models and logic
│   └── model/
│       ├── PullRequest.java
│       ├── ReviewComment.java
│       └── CodeChange.java
├── infra/                 # Infrastructure adapters
│   ├── ai/
│   │   ├── config/       # AI provider configuration
│   │   ├── router/       # Strategy pattern for provider routing
│   │   └── service/      # Gemini and Ollama implementations
│   ├── github/
│   │   ├── adapter/      # GitHub API client (WebFlux)
│   │   ├── service/      # Review posting service
│   │   └── webhook/      # Signature verification
│   └── eventbus/
│       └── adapter/      # Internal event bus (Spring Events)
├── config/                # Application-wide configuration
│   ├── SecurityConfig.java
│   └── WebFluxConfig.java
└── cli/                   # CLI mode for CI/CD
    └── ReviewCli.java
```

### Architecture Pattern

**Hexagonal Architecture (Ports and Adapters):**

```
┌─────────────────────────────────────────────────────┐
│                    Presentation                      │
│          (Controllers, REST Endpoints)               │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│                   Application                        │
│           (Use Cases, Orchestration)                 │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│                     Domain                           │
│            (Business Logic, Entities)                │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│                 Infrastructure                       │
│              (External Integrations)                 │
│                                                      │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐         │
│  │ GitHub   │  │ Gemini   │  │ Ollama   │         │
│  │ Adapter  │  │ Adapter  │  │ Adapter  │         │
│  └──────────┘  └──────────┘  └──────────┘         │
└─────────────────────────────────────────────────────┘
```

---

## Core Components

### 1. Webhook Controller

**Responsibility**: Receive and validate GitHub webhooks

**Operations:**
- Verify HMAC-SHA256 signature
- Parse webhook payload
- Trigger code review workflow
- Return 200 OK response

**Technology**: Spring WebFlux (non-blocking)

**Endpoint**: `/api/v1/webhooks/github/pull_request`

**Implementation**: `GitHubWebhookController.java`

---

### 2. GitHub Client

**Responsibility**: Interact with GitHub REST API

**Operations:**
- Fetch PR diff and metadata
- Post review comments
- Manage PR review status
- Handle rate limiting and retries

**Technology**: Spring WebClient (reactive HTTP client)

**Implementation**:
- `GitHubAdapter.java`
- `GitHubReviewService.java`

**Retry Strategy**: Exponential backoff with jitter

---

### 3. AI Review Service

**Responsibility**: Analyze code changes using AI

**Operations:**
- Build review prompts
- Call AI provider (Gemini or Ollama)
- Parse AI responses
- Generate structured review comments
- Handle token limits with chunking strategy

**Technology**: Spring AI abstraction

**Implementation**: `CodeReviewService.java`

**Token Management**: Context-aware chunking (ADR-0008)

---

### 4. AI Provider Router

**Responsibility**: Route requests to appropriate AI provider

**Operations:**
- Provider selection based on configuration
- Abstract provider-specific details
- Handle provider failures gracefully

**Technology**: Strategy pattern

**Implementation**:
- `AiModelClient.java` (interface)
- `GeminiModelClient.java` (Gemini implementation)
- `OllamaModelClient.java` (Ollama implementation)

**Configuration**: `AI_PROVIDER` environment variable

---

## Request Flow

### Pull Request Review Workflow

```
1. GitHub → Webhook Controller
   POST /api/v1/webhooks/github/pull_request
   Headers: X-Hub-Signature-256, X-GitHub-Event

2. Webhook Controller
   - Verify HMAC signature
   - Parse event payload
   - Emit internal event

3. Event Listener
   - Handle PullRequestEvent
   - Trigger orchestration service

4. Orchestration Service
   - Fetch PR diff (GitHub API)
   - Check if bot should review
   - Build review context

5. AI Review Service
   - Build prompt with diff
   - Call AI provider
   - Parse response

6. GitHub Client
   - Post review comments
   - Update PR status

Total latency: ~2-5 seconds (p95)
```

---

## Data Flow

### Review Comment Generation

```
GitHub Webhook Event
        ↓
Signature Verification (HMAC-SHA256)
        ↓
PR Metadata Fetch (GitHub REST API)
        ↓
PR Diff Fetch (GitHub REST API)
        ↓
Token Count Check
        ↓
[If oversized] → Chunk Strategy (ADR-0008)
        ↓
AI Prompt Building
        ↓
AI Provider Call (Gemini/Ollama)
        ↓
Response Parsing
        ↓
Comment Formatting
        ↓
GitHub Comment Post (GitHub REST API)
        ↓
200 OK Response
```

---

## Technology Stack

### Core Framework
- **Language**: Java 21
- **Framework**: Spring Boot 3.5.3
- **Reactive**: Spring WebFlux + Project Reactor

### External Integrations
- **AI Provider**: Google Gemini 2.0 Flash, Ollama
- **VCS Platform**: GitHub (REST API, Webhooks)
- **AI Abstraction**: Spring AI

### Development Tools
- **Build**: Gradle 8.5+ (Kotlin DSL)
- **Testing**: JUnit 5, Mockito, BlockHound
- **Quality**: SonarCloud, JaCoCo
- **CI/CD**: GitHub Actions

---

## Security

### Authentication
- **GitHub Webhook**: HMAC-SHA256 signature verification (ADR-0003)
- **GitHub API**: Personal Access Token (PAT)
- **Gemini API**: API Key or Workload Identity Federation

### Secret Management
- All credentials via environment variables
- No hardcoded secrets in code
- Token sanitization in logs

### Input Validation
- Spring Validation on all endpoints
- Webhook signature verification before processing
- AI prompt sanitization for injection defense

---

## Observability

### Logging
- **Format**: Structured JSON
- **Level**: DEBUG for application, INFO for libraries
- **Context**: Request ID, user ID, operation name

### Metrics (Planned)
- Request rate, latency, error rate
- AI API token usage
- GitHub API rate limit consumption

### Tracing (Planned)
- OpenTelemetry integration (ADR-0014)
- Distributed tracing with Jaeger

---

## Deployment Model

### Server Mode (Production)

```bash
java -jar pr-rule-bot-0.0.1-SNAPSHOT.jar \
  -DGITHUB_TOKEN=$TOKEN \
  -DGITHUB_WEBHOOK_SECRET=$SECRET \
  -DGOOGLE_GEMINI_API_KEY=$KEY
```

**Platform Options:**
- Google Cloud Run (containerized)
- AWS EC2 (JAR)
- Kubernetes (future migration path)

### CLI Mode (CI/CD)

```bash
java -jar pr-rule-bot-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=ci \
  --cli.pr-number=123 \
  --cli.repository.owner=owner \
  --cli.repository.name=repo
```

**Use Case**: GitHub Actions workflow for self-review

---

## Configuration

### Environment Variables

**Required:**
- `GITHUB_TOKEN`: GitHub Personal Access Token
- `GITHUB_WEBHOOK_SECRET`: Webhook signature secret
- `AI_PROVIDER`: `gemini` or `ollama`

**Gemini Configuration:**
- `GOOGLE_GEMINI_API_KEY`: API key (development)
- `GCP_PROJECT_ID`: Project ID (production with WIF)
- `GCP_LOCATION`: Region (default: us-central1)

**Ollama Configuration:**
- `OLLAMA_BASE_URL`: Base URL (default: http://localhost:11434)
- `OLLAMA_MODEL`: Model name (default: qwen2.5-coder:3b)

### Application Configuration

**application.yml:**
```yaml
spring:
  profiles:
    active: ${APP_MODE:web}
  ai:
    vertex:
      ai:
        gemini:
          project-id: ${GCP_PROJECT_ID}
          location: ${GCP_LOCATION:us-central1}
          chat:
            options:
              model: gemini-2.0-flash-001
              temperature: 0.2
    ollama:
      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
      chat:
        model: ${OLLAMA_MODEL:qwen2.5-coder:3b}
        options:
          temperature: 0.2

github:
  base-url: "https://api.github.com"
  token: ${GITHUB_TOKEN}
  webhook-secret: ${GITHUB_WEBHOOK_SECRET}
  client:
    response-timeout: 300s
    connect-timeout: 5s
```

---

## Performance Characteristics

### Latency
- **Webhook response**: < 100ms (signature verification only)
- **GitHub API calls**: ~500ms - 1s per request
- **AI review (Gemini)**: ~2-5s per PR
- **AI review (Ollama local)**: ~10-30s per PR
- **Total end-to-end**: ~3-6s (Gemini), ~12-35s (Ollama)

### Throughput
- **Max concurrent reviews**: Limited by AI provider
- **Gemini rate limit**: 15 req/min (free tier), higher (paid)
- **Ollama**: No rate limit (local), constrained by hardware

### Resource Usage
- **Memory**: ~512MB heap (typical), ~1GB heap (large PRs)
- **CPU**: Low utilization except during AI calls
- **Network**: Dependent on PR size and AI provider

---

## Scaling Considerations

### Current Limitations
- **Single instance**: No load balancing
- **Synchronous processing**: One PR at a time
- **In-memory state**: No distributed session management
- **No persistence**: No database for review history

### Horizontal Scaling (Future)
To support multiple instances:
1. Add load balancer in front of application
2. Implement distributed session store (Redis)
3. Add message queue for async processing (Kafka)
4. Store review results in database (PostgreSQL)

**See**: ADR-0015 (Microservices Architecture) for planned scaling approach

---

## Failure Handling

### GitHub API Failures
- **Retry**: Exponential backoff (max 3 retries)
- **Circuit breaker**: Not implemented (monolith)
- **Fallback**: Log error, skip review

### AI Provider Failures
- **Retry**: Single retry on transient errors
- **Timeout**: 10 minutes (configurable)
- **Fallback**: Post generic error comment

### Webhook Signature Mismatch
- **Behavior**: Return 401 Unauthorized
- **Logging**: Log failed verification attempts
- **No retry**: Client (GitHub) must resend

---

## Migration Path to Microservices

When ready to migrate to microservices (ADR-0015):

1. **Phase 1**: Extract webhook service
2. **Phase 2**: Add Kafka for async communication
3. **Phase 3**: Extract context and policy services
4. **Phase 4**: Extract review and integration services
5. **Phase 5**: Deploy to Kubernetes

**See**: [implementation-guide.md](../implementation/implementation-guide.md) for detailed migration plan

---

## Related Documentation

- [ADR-0001: Non-Blocking I/O](../adr/0001-non-blocking-io.md) - Reactive architecture
- [ADR-0003: Webhook Security](../adr/0003-webhook-security.md) - Signature verification
- [ADR-0008: Token Chunking Strategy](../adr/0008-token-chunking-strategy.md) - Handle large PRs
- [ADR-0015: Microservices Architecture](../adr/0015-microservices-architecture.md) - Future architecture (Proposed)
- [system-architecture.md](./system-architecture.md) - Planned microservices architecture (Proposed)
- [security-architecture.md](./security-architecture.md) - Security design

---

**Last Updated**: 2025-10-18
