# 0001 - Adopt fully Non-Blocking I/O

"Date": 2025-07-21

## Context
Blocking I/O model can become a bottleneck in high-throughput applications, especially when dealing with multiple concurrent requests.
On‑premise LLM responses are often slow and unpredictable.
The service is intended for team/enterprise‑level use, not just individual workloads.
Git‑repository size and activity can further delay responses as I/O pressure grows.

## Decision
* Use Spring WebFlux and Reactor for fully non-blocking I/O
* Replace `ChatClient.call()` with `ChatClient.stream()`
* Use `Flux` and `Mono` for reactive programming
* Avoid blocking calls in the event loop
* Remove @Async annotations and replace with reactive operators

## Consequences
* Makes event-loop never blocks
* Handling time-consuming tasks with "publishOn" and "subscribeOn" operators
* Enhance Observability with Reactor's `Context` for tracing and logging

