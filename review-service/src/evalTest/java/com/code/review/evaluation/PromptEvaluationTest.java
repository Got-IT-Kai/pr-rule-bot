package com.code.review.evaluation;

import com.code.review.application.port.inbound.ReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@SpringBootTest(
        properties = {
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
                "ai.provider=gemini"
        }
)
class PromptEvaluationTest {

    @Autowired
    private ReviewService reviewService;

    @Test
    void evaluateCurrentBranchChanges() throws IOException, InterruptedException {
        String diff = getCurrentBranchDiff();
        String branchName = getCurrentBranchName();

        if (diff.isBlank()) {
            return;
        }

        StepVerifier.create(
                        reviewService.perform(
                                "eval-" + System.currentTimeMillis(),
                                "local",
                                "evaluation",
                                999,
                                branchName,
                                diff,
                                "eval-corr-" + System.currentTimeMillis()
                        )
                )
                .assertNext(result -> {
                    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));

                    String output = String.format("""
                                    === Metadata ===
                                    Timestamp: %s
                                    Branch: %s
                                    Diff lines: %d
                                    Success: %s
                                    AI Provider: %s
                                    AI Model: %s

                                    === AI Review ===
                                    %s
                                    """,
                            timestamp,
                            branchName,
                            diff.lines().count(),
                            result.isSuccessful(),
                            result.aiProvider(),
                            result.aiModel(),
                            result.reviewComment()
                    );

                    try {
                        Path outputPath = Path.of("build/evaluation/result-" + timestamp + ".txt");
                        Files.createDirectories(outputPath.getParent());
                        Files.writeString(outputPath, output);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to save evaluation result to build/evaluation/result-" + timestamp + ".txt", e);
                    }
                })
                .expectComplete()
                .verify(Duration.ofMinutes(3));
    }

    private String getCurrentBranchDiff() throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("git", "diff", "master");
        Process process = pb.start();
        String diff = new String(process.getInputStream().readAllBytes());
        process.waitFor();
        return diff;
    }

    private String getCurrentBranchName() throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD");
        Process process = pb.start();
        String branch = new String(process.getInputStream().readAllBytes()).trim();
        process.waitFor();
        return branch;
    }
}
