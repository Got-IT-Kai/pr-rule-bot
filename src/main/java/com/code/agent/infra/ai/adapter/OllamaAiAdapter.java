package com.code.agent.infra.ai.adapter;

import com.code.agent.application.port.out.AiPort;
import org.springframework.stereotype.Component;

@Component
public class OllamaAiAdapter implements AiPort {
    @Override
    public String evaluateDiff(String diff) {
        return "Mock review comment";
    }
}
