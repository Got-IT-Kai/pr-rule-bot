package com.code.review.evaluation;

import com.code.review.application.port.inbound.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;

@SpringBootTest(
        properties = {
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
                "ai.provider=gemini"
        }
)
@EnableConfigurationProperties(EvaluationProperties.class)
class CommittedChangesEvaluationTest {

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private EvaluationProperties evalProperties;

    private EvaluationSupport support;

    @BeforeEach
    void setUp() {
        support = new EvaluationSupport(evalProperties.baseBranch());
    }

    @Test
    void evaluateCommittedChanges() throws IOException, InterruptedException {
        support.runEvaluation(reviewService, "committed", support::getCommittedDiff);
    }
}
