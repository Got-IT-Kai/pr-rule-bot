# AI PR Review Bot

[![CI & PR Analysis](https://github.com/Got-IT-Kai/pr-rule-bot/actions/workflows/ci.yml/badge.svg)](https://github.com/Got-IT-Kai/pr-rule-bot/actions/workflows/ci.yml)
[![Sonar Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=Got-IT-Kai_pr-rule-bot&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=Got-IT-Kai_pr-rule-bot)
[![Sonar Coverage](https://sonarcloud.io/api/project_badges/measure?project=Got-IT-Kai_pr-rule-bot&metric=coverage)](https://sonarcloud.io/component_measures?id=Got-IT-Kai_pr-rule-bot&metric=coverage&view=list)
![Java](https://img.shields.io/badge/Java-21-orange)
![Gradle](https://img.shields.io/badge/Gradle-8.5-blue)
[![License](https://img.shields.io/badge/license-Apache%202.0-green)](LICENSE)

Asynchronous GitHub PR reviewer that acknowledges webhook deliveries quickly and runs diff collection, AI review, and comment posting out-of-band via Kafka.

---

## Why it’s structured this way

### **Webhook response window**
- GitHub webhook receivers should return a 2xx response within 10 seconds, so the webhook service validates the request, publishes an event, and returns immediately.

### **Heavy review pipeline**
- Fetching diffs, calling an LLM, and formatting comments are CPU/IO-heavy.
- `context-service` focuses on GitHub API + diff validation,
- `review-service` focuses on AI orchestration,
- `integration-service` focuses on writing results back to GitHub.

### **No application database**
- GitHub remains the source of truth, and the system avoids keeping its own long-lived review history.

### **Failure isolation**
- GitHub does not automatically redeliver failed webhook deliveries, so failed downstream work is routed to DLT and can be replayed manually.

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

---

## Planned extensions
- **policy-service** — org-wide policies, style rules, and security checks
