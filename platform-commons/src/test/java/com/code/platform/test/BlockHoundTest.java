package com.code.platform.test;

import org.junit.jupiter.api.Test;
import reactor.blockhound.BlockingOperationError;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

@SuppressWarnings("java:S2925") // Allow Thread.sleep for BlockHound test
class BlockHoundTest {

    @Test
    void detectBlockingCalls() {
        Mono<Object> blocking = Mono.fromRunnable(() -> {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).subscribeOn(Schedulers.parallel());

        StepVerifier.create(blocking)
                .expectError(BlockingOperationError.class)
                .verify();
    }
}
