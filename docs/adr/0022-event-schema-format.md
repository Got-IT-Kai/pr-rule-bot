# ADR-0022: Event Schema Format (JSON Schema for Now, Avro Later)

## Context
Services exchange events over Kafka. Today we serialize POJOs with Jackson and document the shape using JSON Schema. Avro is common for Kafka, but typically paired with a schema registry for compatibility checks.

## Decision
Stay with JSON payloads + JSON Schema documentation for the current local/single-producer flow. Defer Avro + schema registry until we need stronger compatibility guarantees or multiple producers/consumers across teams.

## Consequences
- ✅ No extra infra; easy local development; simple to inspect messages.
- ❌ No automated compatibility enforcement; schema evolution relies on discipline; payloads are larger than Avro.

## When to revisit
- Add a schema registry (Confluent/Karapace/Apicurio) and Avro codegen when we need cross-team compatibility checks, multiple producers/consumers, or backward/forward evolution enforcement.
