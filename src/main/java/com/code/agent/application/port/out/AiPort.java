package com.code.agent.application.port.out;

import com.code.agent.domain.model.ReviewResult;

public interface AiPort {
    ReviewResult evaluateDiff(String diff);
}
