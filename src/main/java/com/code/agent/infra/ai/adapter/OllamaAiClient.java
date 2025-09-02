package com.code.agent.infra.ai.adapter;

import com.code.agent.infra.ai.model.AiProvider;
import com.code.agent.infra.ai.config.AiProperties;
import com.code.agent.infra.ai.spi.AiModelClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class OllamaAiClient implements AiModelClient {

    private final ChatClient chatClient;

    private final PromptTemplate codeReviewPrompt;
    private final PromptTemplate reviewMergePrompt;

    private static final int MAX_TOKENS = 7680;
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(10);

    public OllamaAiClient(OllamaChatModel ollamaChatModel,
                          AiProperties aiProperties) {
        this.chatClient = ChatClient.create(ollamaChatModel);
        AiProperties.Prompt prompt = aiProperties.prompts().get(AiProvider.OLLAMA);
        this.codeReviewPrompt = new PromptTemplate(prompt.codeReviewPrompt());
        this.reviewMergePrompt = new PromptTemplate(prompt.reviewMergePrompt());
    }

    @Override
    public Mono<String> reviewCode(String diff) {
        log.debug("Processing diff chunk: {}", diff);
        Map<String, Object> model = Map.of("diff", diff);

        return chatClient.prompt(codeReviewPrompt.create(model))
                .stream()
                .content()
                .timeout(Duration.ofMinutes(5))
                .collect(Collectors.joining());
    }

    @Override
    public Mono<String> reviewMerge(String combinedReviews) {
        Map<String, Object> model = Map.of("merge", combinedReviews);
        return chatClient.prompt(reviewMergePrompt.create(model))
                .stream()
                .content()
                .timeout(Duration.ofMinutes(5))
                .collect(Collectors.joining());
    }

    @Override
    public int maxTokens() {
        return MAX_TOKENS;
    }

    @Override
    public Duration requestTimeout() {
        return REQUEST_TIMEOUT;
    }

    @Override
    public boolean isReady() {
        return chatClient != null;
    }

    @Override
    public String modelName() {
        return AiProvider.OLLAMA.name().toLowerCase();
    }

    @Override
    public AiProvider provider() {
        return AiProvider.OLLAMA;
    }
}
