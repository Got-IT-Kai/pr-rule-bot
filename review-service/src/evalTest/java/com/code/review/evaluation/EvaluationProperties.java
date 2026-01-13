package com.code.review.evaluation;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "eval")
record EvaluationProperties(
        String baseBranch
) {
}
