package com.code.review.evaluation;

import com.code.review.application.port.inbound.ReviewService;
import com.code.review.domain.model.ReviewResult;
import lombok.RequiredArgsConstructor;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
class EvaluationSupport {

    private static final int GIT_TIMEOUT_SECONDS = 30;
    private final String baseBranchOverride;

    @FunctionalInterface
    interface DiffSupplier {
        String getDiff(String baseBranch) throws IOException, InterruptedException;
    }

    void runEvaluation(
            ReviewService reviewService,
            String prefix,
            DiffSupplier diffSupplier
    ) throws IOException, InterruptedException {
        String baseBranch = detectBaseBranch();
        String branchName = getCurrentBranchName();

        if (branchName.equals(baseBranch)) {
            throw new IllegalStateException(
                    "Cannot evaluate from base branch '" + baseBranch + "'. " +
                            "Run this test from a feature branch with changes."
            );
        }

        String diff = diffSupplier.getDiff(baseBranch);

        if (diff.isBlank()) {
            saveSkippedResult(prefix, branchName);
            return;
        }

        String correlationId = UUID.randomUUID().toString();

        StepVerifier.create(
                        reviewService.perform(
                                "eval-" + correlationId,
                                "local",
                                "evaluation",
                                1,
                                branchName,
                                diff,
                                correlationId
                        )
                )
                .assertNext(result -> saveResult(prefix, branchName, diff, result))
                .expectComplete()
                .verify(Duration.ofMinutes(3));
    }

    String detectBaseBranch() throws IOException, InterruptedException {
        if (baseBranchOverride != null && !baseBranchOverride.isBlank()) {
            return baseBranchOverride;
        }

        for (String branch : new String[]{"master", "main"}) {
            GitResult result = runGit("rev-parse", "--verify", branch);
            if (result.exitCode == 0) {
                return branch;
            }
        }

        GitResult result = runGit("symbolic-ref", "refs/remotes/origin/HEAD");
        if (result.exitCode == 0 && !result.output.isBlank()) {
            return result.output.replace("refs/remotes/origin/", "");
        }

        throw new IllegalStateException("Cannot detect base branch (tried eval.base-branch property, master, main, origin/HEAD)");
    }

    String getCurrentBranchName() throws IOException, InterruptedException {
        GitResult result = runGit("rev-parse", "--abbrev-ref", "HEAD");
        if (result.exitCode != 0) {
            throw new RuntimeException("git rev-parse failed: " + result.output);
        }
        return result.output;
    }

    String getCommittedDiff(String baseBranch) throws IOException, InterruptedException {
        GitResult result = runGit("diff", baseBranch + "...HEAD");
        if (result.exitCode != 0) {
            throw new RuntimeException("git diff " + baseBranch + "...HEAD failed: " + result.output);
        }
        return result.output;
    }

    String getWorkInProgressDiff(String baseBranch) throws IOException, InterruptedException {
        GitResult result = runGit("diff", baseBranch);
        if (result.exitCode != 0) {
            throw new RuntimeException("git diff " + baseBranch + " failed: " + result.output);
        }
        return result.output;
    }

    void saveResult(String prefix, String branchName, String diff, ReviewResult result) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path outputPath = Path.of("build/evaluation/" + prefix + "-" + timestamp + ".txt");

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
            Files.createDirectories(outputPath.getParent());
            Files.writeString(outputPath, output, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save evaluation result to " + outputPath.toAbsolutePath(), e);
        }
    }

    void saveSkippedResult(String prefix, String branchName) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path outputPath = Path.of("build/evaluation/" + prefix + "-" + timestamp + ".txt");

        String output = String.format("""
                        === Metadata ===
                        Timestamp: %s
                        Branch: %s
                        Diff lines: 0
                        Success: N/A
                        AI Provider: N/A
                        AI Model: N/A

                        === AI Review ===
                        Skipped: No changes detected.
                        """,
                timestamp,
                branchName
        );

        try {
            Files.createDirectories(outputPath.getParent());
            Files.writeString(outputPath, output, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save evaluation result to " + outputPath.toAbsolutePath(), e);
        }
    }

    private GitResult runGit(String... args) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("git");
        for (String arg : args) {
            pb.command().add(arg);
        }
        pb.redirectErrorStream(true);

        Process process = pb.start();
        boolean completed = process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (!completed) {
            process.destroyForcibly();
            throw new RuntimeException("git command timed out after " + GIT_TIMEOUT_SECONDS + "s: git " + String.join(" ", args));
        }

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        int exitCode = process.exitValue();

        return new GitResult(exitCode, output);
    }

    private record GitResult(int exitCode, String output) {
    }
}
