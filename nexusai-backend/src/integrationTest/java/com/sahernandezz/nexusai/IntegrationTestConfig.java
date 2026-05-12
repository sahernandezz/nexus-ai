package com.sahernandezz.nexusai;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration-test wide stubs for the Spring AI beans.
 *
 * The IT suite focuses on auth / health / persistence flows — it does not
 * exercise the LLM. Without these stubs, Spring AI's auto-configured
 * {@code OpenAiEmbeddingModel} reaches out to {@code api.openai.com} during
 * context initialization (to query the embedding dimensions for the pgvector
 * store) and fails with HTTP 401 because the API key is fake.
 *
 * Wired automatically by every {@link AbstractIntegrationTest} subclass via
 * {@code @Import(IntegrationTestConfig.class)} on that base class.
 */
@TestConfiguration
public class IntegrationTestConfig {

    @Bean
    @Primary
    public EmbeddingModel embeddingModel() {
        EmbeddingModel m = mock(EmbeddingModel.class);
        when(m.dimensions()).thenReturn(1536);
        when(m.embed(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new float[1536]);
        when(m.call(org.mockito.ArgumentMatchers.any()))
                .thenReturn(mock(EmbeddingResponse.class));
        return m;
    }

    @Bean
    @Primary
    public OpenAiChatModel openAiChatModel() {
        return mock(OpenAiChatModel.class);
    }
}
