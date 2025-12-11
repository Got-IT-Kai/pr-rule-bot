# PR Rule Bot

[![CI & PR Analysis](https://github.com/Got-IT-Kai/pr-rule-bot/actions/workflows/ci.yml/badge.svg)](https://github.com/Got-IT-Kai/pr-rule-bot/actions/workflows/ci.yml)
[![Sonar Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=Got-IT-Kai_pr-rule-bot&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=Got-IT-Kai_pr-rule-bot)
[![Sonar Coverage](https://sonarcloud.io/api/project_badges/measure?project=Got-IT-Kai_pr-rule-bot&metric=coverage)](https://sonarcloud.io/component_measures?id=Got-IT-Kai_pr-rule-bot&metric=coverage&view=list)
![Java](https://img.shields.io/badge/Java-21-orange)
![Gradle](https://img.shields.io/badge/Gradle-8.5-blue)
[![License](https://img.shields.io/badge/license-Apache%202.0-green)](LICENSE)

Asynchronous GitHub PR reviewer that survives webhook timeouts and keeps the first review loop fast.

A lightweight webhook service accepts PR events, hands them to Kafka, and downstream services fetch diffs, run AI review, and post comments back to GitHub.

---

## Why it’s structured this way

### **Webhook timeout**
- GitHub webhooks must respond in ~10s. `webhook-service` validates the request, enqueues an event, and returns immediately so it never blocks on AI or GitHub API latency.

### **Heavy review pipeline**
- Fetching diffs, calling an LLM, and formatting comments is CPU/IO-heavy.
- `context-service` focuses on GitHub API + diff validation,
- `review-service` focuses on AI orchestration,
- `integration-service` focuses on writing results back to GitHub.

### **Stateless by design**
- GitHub is the source of truth. The system doesn’t keep its own database or history; it just reacts to events and posts reviews.

### **Policy as an extension point**
- `policy-service` is a stub for future rule engines (org-wide policies, style rules, security checks) without mixing that logic into the AI path.

---

## Services

### **`webhook-service`**
- Validate HMAC-SHA256 webhook signature
- Parse PR events
- Emit `pull-request.received`
- Return 202 quickly

### **`context-service`**
- Fetch PR diff and file metadata
- Apply basic diff validation (skip binary/permission-only/no-hunk)
- Emit `context.collected` with status (COMPLETED/SKIPPED/FAILED)

### **`policy-service`**
- Placeholder for future policy checks (no active producers/consumers yet)

### **`review-service`**
- Split diff by file and respect token limits
- Call Gemini or Ollama through Spring AI
- Emit `review.completed` or `review.failed`

### **`integration-service`**
- Post PR comments for:
  - Completed reviews
  - Failed / skipped contexts (with reason)
- Uses compensation pattern with DLT for failed events

---

## Self-review on GKE

Simple self-review setup on GKE Autopilot.

The MSA architecture requires always-on infrastructure, not ephemeral CI runners.

---

## Run locally

### **Prerequisites**
- Java 21
- Docker & Docker Compose
- GitHub Personal Access Token (PAT)
- Optional: Ollama or Gemini credentials for the AI provider

### **Start infra and build**

```bash
docker-compose up -d              # start Kafka + observability stack
./gradlew build                   # build all modules
./gradlew :webhook-service:bootRun   # run a single service
```
