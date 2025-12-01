package com.code.review.infrastructure.adapter.outbound.ai;

import com.code.review.domain.model.PrContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
final class GeminiAiClient implements AiClient {

    private final AiClientHelper helper;
    private final int maxTokens;
    private final String modelName;

    GeminiAiClient(
            @Autowired(required = false) VertexAiGeminiChatModel geminiChatModel,
            PromptProperties promptProperties,
            GeminiAiProperties geminiProperties) {

        ChatClient chatClient = geminiChatModel != null
                ? ChatClient.create(geminiChatModel)
                : null;

        if (chatClient == null) {
            log.warn("VertexAiGeminiChatModel bean is not configured. Gemini AI client will not be operational.");
        }

        PromptProperties.Prompts prompts = promptProperties.gemini();
        this.helper = new AiClientHelper(
                chatClient,
                new PromptTemplate(AiClientHelper.loadResource(prompts.codeReviewPrompt())),
                new PromptTemplate(AiClientHelper.loadResource(prompts.reviewMergePrompt())),
                "Gemini"
        );
        this.maxTokens = geminiProperties.maxTokens();
        this.modelName = "gemini";
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
        return AiProvider.GEMINI.name().toLowerCase();
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
        return AiProvider.GEMINI;
    }
}
