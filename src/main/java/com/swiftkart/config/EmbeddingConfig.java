package com.swiftkart.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Embedding Model Configuration for Profile-Aware Resolution.
 * 
 * This configuration resolves the ambiguity when both Ollama and OpenAI
 * starters are present in the classpath, creating two competing EmbeddingModel beans.
 * 
 * By defining profile-specific @Primary beans, we ensure that the correct
 * embedding model is injected based on the active Spring profile:
 * - @Profile("ollama") for Ollama deployments
 * - @Profile("openai") for OpenAI deployments
 * 
 * This allows clean dependency injection without explicit @Qualifier annotations
 * in the application code (e.g., InMemoryRagStore, RagIngestionService).
 */
@Configuration
public class EmbeddingConfig {

    /**
     * Provide the Ollama EmbeddingModel as @Primary when the "ollama" profile is active.
     * 
     * Spring auto-configuration registers the Ollama embedding model with bean name
     * "ollamaEmbeddingModel" when the Ollama starter is present. This method simply
     * qualifies and marks it as primary for injection.
     * 
     * @param ollamaEmbeddingModel The auto-configured Ollama embedding model
     * @return The Ollama EmbeddingModel as the primary choice
     */
    @Bean
    @Primary
    @Profile("ollama")
    public EmbeddingModel ollamaEmbedding(
            @Qualifier("ollamaEmbeddingModel") EmbeddingModel ollamaEmbeddingModel) {
        return ollamaEmbeddingModel;
    }

    /**
     * Provide the OpenAI EmbeddingModel as @Primary when the "openai" profile is active.
     * 
     * Spring auto-configuration registers the OpenAI embedding model with bean name
     * "openAiEmbeddingModel" when the OpenAI starter is present. This method simply
     * qualifies and marks it as primary for injection.
     * 
     * @param openAiEmbeddingModel The auto-configured OpenAI embedding model
     * @return The OpenAI EmbeddingModel as the primary choice
     */
    @Bean
    @Primary
    @Profile("openai")
    public EmbeddingModel openAiEmbedding(
            @Qualifier("openAiEmbeddingModel") EmbeddingModel openAiEmbeddingModel) {
        return openAiEmbeddingModel;
    }
}
