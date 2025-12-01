package com.code.review.infrastructure.adapter.outbound.ai;

import com.code.review.domain.model.PrContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
final class OllamaAiClient implements AiClient {

    private final AiClientHelper helper;
    private final int maxTokens;
    private final String modelName;

    OllamaAiClient(
            @Autowired(required = false) OllamaChatModel ollamaChatModel,
            PromptProperties promptProperties,
            OllamaAiProperties ollamaProperties) {

        ChatClient chatClient = ollamaChatModel != null
                ? ChatClient.create(ollamaChatModel)
                : null;

        if (chatClient == null) {
            log.warn("OllamaChatModel bean is not configured. Ollama AI client will not be operational.");
        }

        PromptProperties.Prompts prompts = promptProperties.ollama();
        this.helper = new AiClientHelper(
                chatClient,
                new PromptTemplate(AiClientHelper.loadResource(prompts.codeReviewPrompt())),
                new PromptTemplate(AiClientHelper.loadResource(prompts.reviewMergePrompt())),
                "Ollama"
        );
        this.maxTokens = ollamaProperties.maxTokens();
        this.modelName = "ollama";
    }

    @Override
    public Mono<String> reviewCode(String diff, PrContext prContext) {
        return helper.reviewCode(diff, prContext);
    }

    @Override
    public Mono<String> mergeReviews(String combinedReviews, PrContext prContext) {
        return helper.mergeReviews(combinedReviews, prContext);
    }

    @Override
    public int maxTokens() {
        return maxTokens;
    }

    @Override
    public String providerName() {
        return AiProvider.OLLAMA.name().toLowerCase();
    }

    @Override
    public String modelName() {
        return modelName;
    }

    @Override
    public boolean isReady() {
        return helper.isReady();
    }

    @Override
    public AiProvider provider() {
        return AiProvider.OLLAMA;
    }
}
