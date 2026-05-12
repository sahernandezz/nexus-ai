package com.sahernandezz.nexusai.memory;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;

/**
 * Factory that builds a per-request {@link MessageChatMemoryAdvisor}
 * bound to a specific conversation/session ID.
 */
@RequiredArgsConstructor
public class ChatMemoryAdvisorFactory {

    private final ChatMemory chatMemory;
    private static final int DEFAULT_WINDOW = 20;  // last 20 messages

    public MessageChatMemoryAdvisor forSession(String sessionId) {
        return MessageChatMemoryAdvisor.builder(chatMemory)
                .conversationId(sessionId)
                .build();
    }

    public MessageChatMemoryAdvisor forSession(String sessionId, int windowSize) {
        return MessageChatMemoryAdvisor.builder(chatMemory)
                .conversationId(sessionId)
                .build();
    }
}

