package com.code.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Deprecated
//@Configuration
//@EnableAsync
public class AsyncConfig {

    @Bean(name = ExecutorName.AI_TASK_EXECUTOR)
    public Executor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ai-task-");
        executor.initialize();
        return executor;
    }

    @Bean(name = ExecutorName.REVIEW_TASK_EXECUTOR)
    public Executor reviewTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("review-task-");
        executor.initialize();
        return executor;
    }
}
