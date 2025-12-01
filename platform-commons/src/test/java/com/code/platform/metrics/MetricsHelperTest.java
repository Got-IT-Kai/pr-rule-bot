package com.code.platform.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MetricsHelper")
class MetricsHelperTest {

    private MeterRegistry meterRegistry;
    private MetricsHelper metricsHelper;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsHelper = new MetricsHelper(meterRegistry, "test");
    }

    @Nested
    @DisplayName("incrementCounter")
    class IncrementCounter {

        @Test
        @DisplayName("should increment counter successfully")
        void shouldIncrementCounter() {
            metricsHelper.incrementCounter("requests");

            assertThat(meterRegistry.counter("test.requests").count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("should increment counter with tags")
        void shouldIncrementCounterWithTags() {
            metricsHelper.incrementCounter("requests", "status", "success");

            assertThat(meterRegistry.counter("test.requests", "status", "success").count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("should increment counter by specific amount")
        void shouldIncrementCounterByAmount() {
            metricsHelper.incrementCounter("bytes", 150.5, "direction", "inbound");

            assertThat(meterRegistry.counter("test.bytes", "direction", "inbound").count()).isEqualTo(150.5);
        }

        @Test
        @DisplayName("should throw exception for null name")
        void shouldThrowExceptionForNullName() {
            assertThatThrownBy(() -> metricsHelper.incrementCounter(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Metric name cannot be null or blank");
        }

        @Test
        @DisplayName("should throw exception for blank name")
        void shouldThrowExceptionForBlankName() {
            assertThatThrownBy(() -> metricsHelper.incrementCounter("  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Metric name cannot be null or blank");
        }
    }

    @Nested
    @DisplayName("recordTime (Callable)")
    class RecordTimeCallable {

        @Test
        @DisplayName("should record time for successful operation")
        void shouldRecordTimeForSuccessfulOperation() throws Exception {
            String result = metricsHelper.recordTime("operation", () -> "success");

            assertThat(result).isEqualTo("success");
            assertThat(meterRegistry.timer("test.operation").count()).isEqualTo(1);
        }

        @Test
        @DisplayName("should record time for operation that throws exception")
        void shouldRecordTimeForOperationThatThrows() {
            assertThatThrownBy(() -> metricsHelper.recordTime("operation", () -> {
                throw new RuntimeException("Test error");
            })).isInstanceOf(RuntimeException.class)
                    .hasMessage("Test error");

            assertThat(meterRegistry.timer("test.operation").count()).isEqualTo(1);
        }

        @Test
        @DisplayName("should throw exception for null name")
        void shouldThrowExceptionForNullName() {
            assertThatThrownBy(() -> metricsHelper.recordTime(null, () -> "test"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Metric name cannot be null or blank");
        }
    }

    @Nested
    @DisplayName("recordTime (Mono)")
    class RecordTimeMono {

        @Test
        @DisplayName("should record time for successful Mono")
        void shouldRecordTimeForSuccessfulMono() {
            Mono<String> mono = Mono.just("success");

            StepVerifier.create(metricsHelper.recordTime("operation", mono))
                    .expectNext("success")
                    .verifyComplete();

            assertThat(meterRegistry.timer("test.operation").count()).isEqualTo(1);
        }

        @Test
        @DisplayName("should record time for Mono that errors")
        void shouldRecordTimeForMonoThatErrors() {
            Mono<String> mono = Mono.error(new RuntimeException("Test error"));

            StepVerifier.create(metricsHelper.recordTime("operation", mono))
                    .expectError(RuntimeException.class)
                    .verify();

            assertThat(meterRegistry.timer("test.operation").count()).isEqualTo(1);
        }

        @Test
        @DisplayName("should record time for cancelled Mono")
        void shouldRecordTimeForCancelledMono() {
            Mono<String> mono = Mono.never();

            StepVerifier.create(metricsHelper.recordTime("operation", mono))
                    .thenCancel()
                    .verify();

            assertThat(meterRegistry.timer("test.operation").count()).isEqualTo(1);
        }

        @Test
        @DisplayName("should record only once even on multiple signals")
        void shouldRecordOnlyOnce() {
            // This test ensures doFinally with AtomicBoolean prevents double recording
            Mono<String> mono = Mono.just("success");

            StepVerifier.create(metricsHelper.recordTime("operation", mono))
                    .expectNext("success")
                    .verifyComplete();

            assertThat(meterRegistry.timer("test.operation").count()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("recordTimeWithStatus")
    class RecordTimeWithStatus {

        @Test
        @DisplayName("should record time with success status")
        void shouldRecordTimeWithSuccessStatus() {
            Mono<String> mono = Mono.just("success");

            StepVerifier.create(metricsHelper.recordTimeWithStatus("operation", mono))
                    .expectNext("success")
                    .verifyComplete();

            Timer timer = meterRegistry.timer("test.operation", "status", "success");
            assertThat(timer.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("should record time with error status and error type")
        void shouldRecordTimeWithErrorStatus() {
            Mono<String> mono = Mono.error(new IllegalArgumentException("Test error"));

            StepVerifier.create(metricsHelper.recordTimeWithStatus("operation", mono))
                    .expectError(IllegalArgumentException.class)
                    .verify();

            Timer timer = meterRegistry.timer("test.operation", "status", "error", "error_type", "IllegalArgumentException");
            assertThat(timer.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("should record time with cancelled status")
        void shouldRecordTimeWithCancelledStatus() {
            Mono<String> mono = Mono.never();

            StepVerifier.create(metricsHelper.recordTimeWithStatus("operation", mono))
                    .thenCancel()
                    .verify();

            Timer timer = meterRegistry.timer("test.operation", "status", "cancelled");
            assertThat(timer.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("should record time with additional tags")
        void shouldRecordTimeWithAdditionalTags() {
            Mono<String> mono = Mono.just("success");

            StepVerifier.create(metricsHelper.recordTimeWithStatus("operation", mono, "env", "test"))
                    .expectNext("success")
                    .verifyComplete();

            Timer timer = meterRegistry.timer("test.operation", "env", "test", "status", "success");
            assertThat(timer.count()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("recordDuration")
    class RecordDurationTest {

        @Test
        @DisplayName("should record duration successfully")
        void shouldRecordDuration() {
            metricsHelper.recordDuration("latency", Duration.ofMillis(150));

            Timer timer = meterRegistry.timer("test.latency");
            assertThat(timer.count()).isEqualTo(1);
            assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isGreaterThan(0);
        }

        @Test
        @DisplayName("should throw exception for null name")
        void shouldThrowExceptionForNullName() {
            assertThatThrownBy(() -> metricsHelper.recordDuration(null, Duration.ofMillis(100)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Metric name cannot be null or blank");
        }
    }

    @Nested
    @DisplayName("recordValue")
    class RecordValueTest {

        @Test
        @DisplayName("should record value successfully")
        void shouldRecordValue() {
            metricsHelper.recordValue("payload.size", 1024.0);

            assertThat(meterRegistry.summary("test.payload.size").count()).isEqualTo(1);
            assertThat(meterRegistry.summary("test.payload.size").totalAmount()).isEqualTo(1024.0);
        }

        @Test
        @DisplayName("should throw exception for null name")
        void shouldThrowExceptionForNullName() {
            assertThatThrownBy(() -> metricsHelper.recordValue(null, 100.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Metric name cannot be null or blank");
        }
    }

    @Nested
    @DisplayName("gauge")
    class GaugeTest {

        @Test
        @DisplayName("should register gauge successfully")
        void shouldRegisterGauge() {
            metricsHelper.gauge("queue.size", 42, value -> value);

            io.micrometer.core.instrument.Gauge gauge = meterRegistry.find("test.queue.size").gauge();
            assertThat(gauge).isNotNull();
            assertThat(gauge.value()).isEqualTo(42.0);
        }

        @Test
        @DisplayName("should throw exception for null name")
        void shouldThrowExceptionForNullName() {
            assertThatThrownBy(() -> metricsHelper.gauge(null, 10, value -> value))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Metric name cannot be null or blank");
        }
    }
}
