package com.sahernandezz.nexusai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.api.OpenAiImageApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * OpenAI configuration.
 *
 * Chat model      : GPT-4.1-mini (spring.ai.openai.chat.options.model)
 * Embedding model : text-embedding-3-small — 1536 dims
 * Image model     : DALL-E 3 — overridden here so the underlying RestClient has
 *                   long timeouts (DALL-E 3 calls take 15-60s and the default
 *                   reactor-netty read timeout aborts them with a
 *                   ReadTimeoutException).
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class OpenAiConfig {

    @Value("${spring.ai.openai.chat.options.model:gpt-4.1-mini}")
    private String chatModel;

    @Value("${spring.ai.openai.embedding.options.model:text-embedding-3-small}")
    private String embedModel;

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url:https://api.openai.com}")
    private String baseUrl;

    private final OpenAiEmbeddingModel embeddingModel;

    public OpenAiConfig(OpenAiEmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Value("${spring.ai.openai.image.options.model:gpt-image-1}")
    private String imageModelName;

    @EventListener(ApplicationReadyEvent.class)
    public void logStartup() {
        log.info("[OpenAI] Chat model     : {}", chatModel);
        // dimensions() makes a live HTTP call against OpenAI to discover the
        // embedding size — wrap so a missing/invalid API key (e.g. in
        // integration tests with stub keys) doesn't take down the whole
        // ApplicationReadyEvent chain.
        String dims;
        try {
            dims = String.valueOf(embeddingModel.dimensions());
        } catch (Exception e) {
            dims = "unknown (" + e.getClass().getSimpleName() + ")";
        }
        log.info("[OpenAI] Embedding model: {} | dims={}", embedModel, dims);
        log.info("[OpenAI] Image model    : {} (read-timeout=240s)", imageModelName);
    }

    /**
     * Custom RestClient.Builder dedicated to DALL-E 3 calls. Uses the JDK HTTP
     * client (instead of reactor-netty) so we can give it a generous read
     * timeout — image generation routinely takes 15-60s.
     */
    private RestClient.Builder longTimeoutRestClientBuilder() {
        HttpClient jdkHttpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdkHttpClient);
        factory.setReadTimeout(Duration.ofSeconds(240));
        return RestClient.builder().requestFactory(factory);
    }

    /**
     * Override the auto-configured OpenAiImageApi with one whose underlying
     * RestClient has a 180s read timeout. The Spring AI auto-config skips
     * creating its own bean when one is present (matched by type).
     */
    @Bean
    @Primary
    public OpenAiImageApi openAiImageApi() {
        return OpenAiImageApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .restClientBuilder(longTimeoutRestClientBuilder())
                .build();
    }

    /**
     * Build the OpenAiImageModel from the long-timeout API. Required because
     * once we provide our own OpenAiImageApi bean, the auto-config will still
     * try to wire OpenAiImageModel against it but we want to be explicit so the
     * dependency on our bean is unambiguous.
     */
    @Bean
    @Primary
    public OpenAiImageModel openAiImageModel(OpenAiImageApi openAiImageApi) {
        return new OpenAiImageModel(openAiImageApi);
    }
}
