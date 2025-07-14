package com.code.agent.infra.ai.adapter;

import com.code.agent.application.port.out.AiPort;
import com.code.agent.domain.model.ReviewResult;
import org.springframework.stereotype.Component;

@Component
public class OllamaAiAdapter implements AiPort {
    @Override
    public ReviewResult evaluateDiff(String diff) {
        return new ReviewResult(true, "Mock review comment");
    }
}
