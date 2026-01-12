# ADR-0024: Conditional Kafka Components

## Context
Some scenarios (evalTest, future single-service dev) need to run without Kafka. But @KafkaListener classes and DltPublisher were unconditional beans, breaking startup when KafkaTemplate is absent.

## Decision
Introduce `@ConditionalOnKafkaEnabled` meta-annotation wrapping `@ConditionalOnBean(KafkaTemplate.class)`. Apply to all listeners and DltPublisher.

## Consequences
- ✅ App starts without Kafka via `spring.autoconfigure.exclude`; consistent policy across 6 components.
- ✅ Self-documenting vs raw @ConditionalOnBean.
- ❌ One more abstraction layer.
