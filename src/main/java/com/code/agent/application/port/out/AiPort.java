package com.code.agent.application.port.out;

import reactor.core.publisher.Mono;

public interface AiPort {
    Mono<String> evaluateDiff(String diff);
}
