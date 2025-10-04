package com.code.agent.cli;

import com.code.agent.application.port.out.AiPort;
import com.code.agent.config.CliProperties;
import com.code.agent.domain.model.Repository;
import com.code.agent.infra.github.service.GitHubReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewCliTest {

    @Mock
    AiPort aiPort;

    @Mock
    GitHubReviewService gitHubReviewService;

    @Mock
    CliProperties cliProperties = new CliProperties(new Repository("o", "r"), 1, 1);

    @InjectMocks
    ReviewCli reviewCli;

    @BeforeEach
    void setUp() {
        Repository repository = new Repository("o", "r");
        when(cliProperties.repository()).thenReturn(repository);
        when(cliProperties.prNumber()).thenReturn(1);
        when(cliProperties.timeOutMinutes()).thenReturn(1);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void runSuccess() throws Exception {
        when(gitHubReviewService.hasExistingReview("o", "r", 1))
                .thenReturn(Mono.just(false));
        when(gitHubReviewService.fetchUnifiedDiff("o", "r", 1))
                .thenReturn(Mono.just("diff --git a/file.txt b/file.txt"));
        when(aiPort.evaluateDiff("diff --git a/file.txt b/file.txt"))
                .thenReturn(Mono.just("AI Review"));
        when(gitHubReviewService.postReviewComment("o", "r", 1, "AI Review"))
                .thenReturn(Mono.empty());

        CountDownLatch latch = new CountDownLatch(1);
        Schedulers.boundedElastic().schedule(() -> {
            try {
                reviewCli.run(new DefaultApplicationArguments(new String[0]));
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        verify(gitHubReviewService).fetchUnifiedDiff("o", "r", 1);
        verify(aiPort).evaluateDiff("diff --git a/file.txt b/file.txt");
        verify(gitHubReviewService).postReviewComment("o", "r", 1, "AI Review");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void runNoDiff() throws Exception {
        when(gitHubReviewService.hasExistingReview("o", "r", 1))
                .thenReturn(Mono.just(false));
        when(gitHubReviewService.fetchUnifiedDiff("o", "r", 1))
                .thenReturn(Mono.just(""));

        CountDownLatch latch = new CountDownLatch(1);
        Schedulers.boundedElastic().schedule(() -> {
            try {
                reviewCli.run(new DefaultApplicationArguments(new String[0]));
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(3, TimeUnit.SECONDS));

        verify(aiPort, never()).evaluateDiff(anyString());
        verify(gitHubReviewService, never()).postReviewComment(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void runAiReturnsEmpty() throws Exception {
        when(gitHubReviewService.hasExistingReview("o", "r", 1))
                .thenReturn(Mono.just(false));
        when(gitHubReviewService.fetchUnifiedDiff("o", "r", 1))
                .thenReturn(Mono.just("diff --git a/file.txt b/file.txt"));
        when(aiPort.evaluateDiff("diff --git a/file.txt b/file.txt"))
                .thenReturn(Mono.just(""));

        CountDownLatch latch = new CountDownLatch(1);
        Schedulers.boundedElastic().schedule(() -> {
            try {
                reviewCli.run(new DefaultApplicationArguments(new String[0]));
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(3, TimeUnit.SECONDS));

        verify(gitHubReviewService, never()).postReviewComment(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void runPostReviewFails() throws Exception {
        when(gitHubReviewService.hasExistingReview("o", "r", 1))
                .thenReturn(Mono.just(false));
        when(gitHubReviewService.fetchUnifiedDiff("o", "r", 1))
                .thenReturn(Mono.just("diff --git a/file.txt b/file.txt"));
        when(aiPort.evaluateDiff("diff --git a/file.txt b/file.txt"))
                .thenReturn(Mono.just("AI Review"));
        when(gitHubReviewService.postReviewComment("o", "r", 1, "AI Review"))
                .thenReturn(Mono.error(new RuntimeException("GitHub API error")));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> exception = new AtomicReference<>();

        Schedulers.boundedElastic().schedule(() -> {
            try {
                reviewCli.run(new DefaultApplicationArguments(new String[0]));
            } catch (Exception e) {
                exception.set(e);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertNotNull(exception.get());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void runSkipsWhenReviewAlreadyExists() throws Exception {
        // Given: Review already exists
        when(gitHubReviewService.hasExistingReview("o", "r", 1))
                .thenReturn(Mono.just(true));

        CountDownLatch latch = new CountDownLatch(1);
        Schedulers.boundedElastic().schedule(() -> {
            try {
                reviewCli.run(new DefaultApplicationArguments(new String[0]));
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(3, TimeUnit.SECONDS));

        // Then: Should not fetch diff or call AI
        verify(gitHubReviewService).hasExistingReview("o", "r", 1);
        verify(gitHubReviewService, never()).fetchUnifiedDiff(anyString(), anyString(), anyInt());
        verify(aiPort, never()).evaluateDiff(anyString());
        verify(gitHubReviewService, never()).postReviewComment(anyString(), anyString(), anyInt(), anyString());
    }
}