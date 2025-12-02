# Event Schemas

**Purpose**: Define contracts for inter-service event communication

**Format**: JSON Schema
**Versioning**: Semantic Versioning
**Scope**: Only schemas used by current services (webhook, context, review, integration)

---

## Structure

```
event-schemas/
├── README.md              # This document
├── POLICY.md              # Schema evolution policy
└── v1/                   # Version 1 schemas
    ├── webhook/          # Webhook service events
    ├── context/          # Context service events
    ├── review/           # Review service events
    └── integration/      # Integration service events
```

---

## Schema Naming Convention

### Event Names
```
{domain}.{entity}.{action}

Examples:
- webhook.pull-request.received
- context.repository.analyzed
- review.code.completed
```

### File Names
```
{event-name}.json

Examples:
- pull-request-received.json
- repository-analyzed.json
- code-review-completed.json
```

---

## Schema Template

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "https://github.com/got-it-kai/pr-rule-bot/event-schemas/v1/{service}/{event-name}.json",
  "title": "{EventName}",
  "description": "Event description",
  "type": "object",
  "required": ["eventId", "timestamp", "eventType"],
  "properties": {
    "eventId": {
      "type": "string",
      "format": "uuid",
      "description": "Unique event identifier"
    },
    "timestamp": {
      "type": "string",
      "format": "date-time",
      "description": "Event occurrence time (ISO 8601)"
    },
    "eventType": {
      "type": "string",
      "const": "{service}.{entity}.{action}",
      "description": "Event type identifier"
    },
    "version": {
      "type": "string",
      "const": "1.0.0",
      "description": "Schema version"
    }
  }
}
```

---

## Schema Evolution Rules

### Backward Compatible (Minor version)
- ✅ Add optional fields
- ✅ Add new enum values
- ✅ Relax validation (e.g., minLength 5 → 3)

### Breaking Changes (Major version)
- ❌ Remove fields
- ❌ Rename fields
- ❌ Change field types
- ❌ Add required fields
- ❌ Tighten validation

### Example
```
v1.0.0 → v1.1.0: Add optional "metadata" field
v1.1.0 → v2.0.0: Change "userId" to "authorId"
```

---

## Validation

### Local/CI Validation
- Automated in tests: shared-events serializes events and validates against these schemas (see `shared-events/src/test/java/com/code/events/schema/EventSchemaContractTest.java`).
- Optional manual check:
```bash
npm install -g ajv-cli
ajv validate -s event-schemas/v1/webhook/pull-request-received.json -d <payload.json>
```

---

## Related Documents

- [Common Module Policy](../../internal/docs/policy/common-module-policy.md)
- [AsyncAPI Specification](https://www.asyncapi.com/)
- [JSON Schema Specification](https://json-schema.org/)

---

## Notes

### Schema Format
Currently using JSON Schema for:
- Documentation and human readability
- IDE support and validation
- Easy local validation with ajv-cli

When Kafka is introduced, schemas will be migrated to Avro format for runtime schema registry integration.

### Repository Separation
This directory can be separated into a separate repository with full git history:
```bash
# Separate into a separate repository including history
git subtree split -P event-schemas -b event-schemas-branch
git push <new-repo-url> event-schemas-branch:main
```
