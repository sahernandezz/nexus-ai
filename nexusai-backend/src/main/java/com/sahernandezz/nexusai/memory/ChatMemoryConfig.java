package com.sahernandezz.nexusai.memory;

import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * M4 — Chat Memory Configuration.
 *
 * Spring AI 1.0.0 auto-configures a JDBC-backed {@link ChatMemory} bean
 * when {@code spring-ai-starter-model-chat-memory-repository-jdbc} is on
 * the classpath and the datasource is configured.
 *
 * Schema is created by Flyway migration {@code V2__chat_memory.sql}.
 *
 * Usage in chat:
 * <pre>
 *   chatClient.prompt()
 *     .user(message)
 *     .advisors(MessageChatMemoryAdvisor.builder(chatMemory)
 *         .conversationId(sessionId).build())
 *     .stream().content()
 * </pre>
 */
@Configuration(proxyBeanMethods = false)
public class ChatMemoryConfig {

    /**
     * Creates a reusable {@link MessageChatMemoryAdvisor} factory-style helper.
     * The advisor itself is created per-request with the caller's conversationId.
     *
     * Exposed so ChatService and RagService can inject it consistently.
     */
    @Bean
    public ChatMemoryAdvisorFactory chatMemoryAdvisorFactory(ChatMemory chatMemory) {
        return new ChatMemoryAdvisorFactory(chatMemory);
    }
}

