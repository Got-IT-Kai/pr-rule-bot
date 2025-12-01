package com.code.platform.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

@RequiredArgsConstructor
public class MetricsHelper {

    private final MeterRegistry meterRegistry;
    private final String metricPrefix;

    public void incrementCounter(String name, String... tags) {
        validateName(name);
        meterRegistry.counter(metricPrefix + "." + name, tags).increment();
    }

    public void incrementCounter(String name, double amount, String... tags) {
        validateName(name);
        meterRegistry.counter(metricPrefix + "." + name, tags).increment(amount);
    }

    public <T> T recordTime(String name, Callable<T> operation, String... tags) throws Exception {
        validateName(name);
        return meterRegistry.timer(metricPrefix + "." + name, tags).recordCallable(operation);
    }

    public <T> Mono<T> recordTime(String name, Mono<T> mono, String... tags) {
        validateName(name);
        return Mono.defer(() -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            AtomicBoolean recorded = new AtomicBoolean(false);
            return mono.doFinally(signal -> {
                if (recorded.compareAndSet(false, true)) {
                    sample.stop(meterRegistry.timer(metricPrefix + "." + name, tags));
                }
            });
        });
    }

    public <T> Mono<T> recordTimeWithStatus(String name, Mono<T> mono, String... additionalTags) {
        validateName(name);
        return Mono.defer(() -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            AtomicReference<String> status = new AtomicReference<>("success");
            AtomicReference<String> errorType = new AtomicReference<>(null);
            AtomicBoolean recorded = new AtomicBoolean(false);

            return mono
                    .doOnError(error -> {
                        status.set("error");
                        errorType.set(error.getClass().getSimpleName());
                    })
                    .doOnCancel(() -> status.set("cancelled"))
                    .doFinally(signal -> {
                        if (recorded.compareAndSet(false, true)) {
                            String[] tags = errorType.get() != null
                                    ? appendTags(additionalTags, "status", status.get(), "error_type", errorType.get())
                                    : appendTags(additionalTags, "status", status.get());
                            sample.stop(meterRegistry.timer(metricPrefix + "." + name, tags));
                        }
                    });
        });
    }

    public void recordDuration(String name, Duration duration, String... tags) {
        validateName(name);
        meterRegistry.timer(metricPrefix + "." + name, tags).record(duration);
    }

    // Note: Creates new array each time. If GC becomes an issue at high load, consider caching.
    private String[] appendTags(String[] existingTags, String... newTags) {
        String[] result = new String[existingTags.length + newTags.length];
        System.arraycopy(existingTags, 0, result, 0, existingTags.length);
        System.arraycopy(newTags, 0, result, existingTags.length, newTags.length);
        return result;
    }

    public void recordValue(String name, double value, String... tags) {
        validateName(name);
        meterRegistry.summary(metricPrefix + "." + name, tags).record(value);
    }

    public <T> void gauge(String name, T stateObject, Function<T, Number> valueSupplier, String... tags) {
        validateName(name);
        meterRegistry.gauge(metricPrefix + "." + name, io.micrometer.core.instrument.Tags.of(tags),
                stateObject, obj -> valueSupplier.apply(obj).doubleValue());
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Metric name cannot be null or blank");
        }
    }
}
