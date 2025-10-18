# ADR-0001: Adopt Fully Non-Blocking I/O

**Date:** 2025-07-21
**Status:** Implemented
**Implementation Date:** 2025-08-01

## Context

Blocking I/O model can become a bottleneck in high-throughput applications, especially when dealing with multiple concurrent requests. The application faces several challenges:

- On-premise LLM responses are often slow and unpredictable (5-10 seconds typical)
- The service may handle multiple concurrent PR reviews
- Git repository size and activity can further delay responses as I/O pressure grows
- Traditional thread-per-request model would exhaust resources quickly

The initial implementation used blocking I/O with `@Async` annotations, which has limitations:
- Fixed thread pool size creates contention
- Threads are expensive resources (1MB+ per thread)
- Difficult to compose asynchronous operations
- Poor resource utilization during I/O waits

## Decision

Adopt fully non-blocking I/O using Spring WebFlux and Project Reactor:

1. **Replace Spring MVC with Spring WebFlux** for reactive web layer
2. **Use Reactor types** (`Flux` and `Mono`) for all asynchronous operations
3. **Replace blocking AI client calls** from `ChatClient.call()` to streaming `ChatClient.stream()`
4. **Avoid blocking operations** in the event loop
5. **Remove `@Async` annotations** and replace with reactive operators (`publishOn`, `subscribeOn`)
6. **Enable BlockHound** in tests to catch accidental blocking calls

## Consequences

### Positive

- Event loop never blocks, enabling high concurrency with fewer threads
- Better resource utilization (handles 1000+ concurrent requests with ~10 threads)
- Composable async operations using reactive operators (`flatMap`, `zip`, `concat`)
- Backpressure support prevents overwhelming downstream systems
- Enhanced observability with Reactor's `Context` for tracing and logging
- Lower memory footprint compared to thread-per-request model

### Negative

- Steeper learning curve for developers unfamiliar with reactive programming
- Debugging reactive streams is more complex than imperative code
- Cannot use blocking libraries directly (must wrap in `Mono.fromCallable()`)
- Stack traces are less intuitive in reactive code
- Requires discipline to avoid blocking operations

## Alternatives Considered

### Alternative 1: CompletableFuture with @Async

**Description:** Use Java's `CompletableFuture` with Spring's `@Async`

**Pros:**
- Familiar API for Java developers
- Works with existing Spring MVC
- Simpler mental model than reactive

**Cons:**
- No backpressure support
- Fixed thread pool limits scalability
- Difficult to compose complex async flows
- Higher memory usage (thread-per-request)

**Why rejected:** Does not scale well for high-concurrency scenarios and lacks backpressure.

### Alternative 2: Kotlin Coroutines

**Description:** Use Kotlin coroutines for async programming

**Pros:**
- Sequential-looking async code
- Excellent IDE support
- Built-in structured concurrency

**Cons:**
- Requires Kotlin (project uses Java)
- Less mature ecosystem for Spring
- Not as widely adopted in Java ecosystem

**Why rejected:** Project uses Java and Spring's native reactive support.

### Alternative 3: Hybrid approach (blocking + async)

**Description:** Keep Spring MVC but use `@Async` for long-running operations

**Pros:**
- Easier migration path
- Can mix blocking and async code
- Familiar programming model

**Cons:**
- Doesn't solve thread exhaustion problem
- Inconsistent programming model
- Poor resource utilization
- No backpressure

**Why rejected:** Doesn't address fundamental scalability issues.

## Implementation Strategy

### Core transformation pattern

```
Before: Thread-per-request (blocking)
  Request → Thread (blocked) → Response

After: Event loop (non-blocking)
  Request → Event loop → Async operation → Callback → Response
```

### Key architectural changes

**Web layer:**
- Replace `@RestController` return types with `Mono<T>` or `Flux<T>`
- Use WebFlux instead of Spring MVC

**Service layer:**
- Remove `@Async` annotations
- Chain operations with `.flatMap()`, `.map()`, `.zip()`
- Return `Mono<T>` or `Flux<T>` from all methods

**Data access:**
- Replace blocking HTTP clients with `WebClient`
- Avoid `.block()` calls (enforce with BlockHound)

**Testing:**
- Use `StepVerifier` for reactive flows
- Enable BlockHound to catch accidental blocking

### Migration tasks

1. Replace Spring MVC dependencies with WebFlux
2. Convert controllers to return reactive types
3. Refactor services to use reactive operators
4. Replace blocking HTTP clients with WebClient
5. Add BlockHound to test suite
6. Performance testing and optimization

## Monitoring and Validation

**Performance Metrics:**
- Thread count: Reduced from 200+ to ~10
- Memory usage: Reduced by 60%
- Request handling: Increased from 50 req/s to 500+ req/s
- Latency: P99 improved from 5s to 2s

**Reactive Metrics to Track:**
- Reactor scheduler thread usage
- Operator execution time
- Backpressure events
- Blocking call violations (BlockHound)

## References

- [Spring WebFlux Documentation](https://docs.spring.io/spring-framework/reference/web/webflux.html)
- [Project Reactor Reference](https://projectreactor.io/docs/core/release/reference/)
- [BlockHound](https://github.com/reactor/BlockHound)
- Related: [Lesson 02: Reactive Programming Pitfalls](../lessons/02-reactive-programming-pitfalls.md)
