package com.code.agent.infra.ai.adapter;

import com.code.agent.infra.ai.model.AiProvider;
import com.code.agent.infra.ai.spi.AiModelClient;
import com.code.agent.infra.ai.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component("geminiClient")
public class GeminiAiClient implements AiModelClient {

    private final ChatClient chatClient;
    private final PromptTemplate codeReviewPrompt;
    private final PromptTemplate reviewMergePrompt;

    private static final int MAX_TOKENS = 100_000;
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(10);

    public GeminiAiClient(@Autowired(required = false) VertexAiGeminiChatModel geminiChatModel,
                          AiProperties aiProperties) {
        if (geminiChatModel == null) {
            log.warn("VertexAiGeminiChatModel bean is not configured. Gemini AI client will not be operational.");
            chatClient = null;
        } else {
            chatClient = ChatClient.create(geminiChatModel);
        }

        AiProperties.Prompt prompt = aiProperties.prompts().get(AiProvider.GEMINI);
        codeReviewPrompt = new PromptTemplate(prompt.codeReviewPrompt());
        reviewMergePrompt = new PromptTemplate(prompt.reviewMergePrompt());
    }

    @Override
    public Mono<String> reviewCode(String diff) {
        Map<String, Object> model = Map.of("diff", diff);

        return chatClient.prompt(codeReviewPrompt.create(model))
                .stream()
                .content()
                .timeout(REQUEST_TIMEOUT)
                .collect(Collectors.joining());
    }

    @Override
    public Mono<String> reviewMerge(String combinedReviews) {
        Map<String, Object> model = Map.of("merge", combinedReviews);

        return chatClient.prompt(reviewMergePrompt.create(model))
                .stream()
                .content()
                .timeout(REQUEST_TIMEOUT)
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
        return AiProvider.GEMINI.name().toLowerCase();
    }

    @Override
    public AiProvider provider() {
        return AiProvider.GEMINI;
    }
}
