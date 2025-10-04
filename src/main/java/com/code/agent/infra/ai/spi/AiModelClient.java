package com.code.agent.infra.ai.spi;

import com.code.agent.infra.ai.model.AiProvider;
import reactor.core.publisher.Mono;

/**
 * Service provider interface for AI model clients.
 * Implementations provide code review capabilities using different AI providers.
 */
public interface AiModelClient {
    /**
     * Reviews the provided code diff and generates review comments.
     *
     * @param diff the git diff content to review
     * @return Mono containing the AI-generated review comments
     */
    Mono<String> reviewCode(String diff);

    /**
     * Merges multiple review results into a consolidated review summary.
     *
     * @param combinedReviews multiple review results to be merged
     * @return Mono containing the consolidated review summary
     */
    Mono<String> reviewMerge(String combinedReviews);

    /**
     * Returns the maximum number of tokens this client can process.
     *
     * @return maximum token limit for this AI provider
     */
    int maxTokens();

    /**
     * Checks if the AI client is properly configured and ready to use.
     *
     * @return true if the client is ready, false otherwise
     */
    boolean isReady();

    /**
     * Returns the name of the AI model used by this client.
     *
     * @return model name as a string
     */
    String modelName();

    /**
     * Returns the AI provider type for this client.
     *
     * @return the AiProvider enum value
     */
    AiProvider provider();
}
