package com.sahernandezz.nexusai.chat;

import com.sahernandezz.nexusai.chat.advisor.MetricsAdvisor;
import com.sahernandezz.nexusai.chat.advisor.SafeGuardAdvisor;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Registry of available {@link ChatClient} instances.
 * <p>
 * Only OpenAI is supported. The {@code openai} provider maps to GPT-4o.
 * </p>
 */
@Slf4j
@Component
public class ChatClientRegistry {

    private static final String PROVIDER = "openai";

    private final ChatClient openAiClient;

    public ChatClientRegistry(OpenAiChatModel openAiChatModel, MeterRegistry meterRegistry) {
        SafeGuardAdvisor safeGuard = new SafeGuardAdvisor();
        this.openAiClient = ChatClient.builder(openAiChatModel)
                .defaultAdvisors(
                        safeGuard,
                        new SimpleLoggerAdvisor(),
                        new MetricsAdvisor(meterRegistry, PROVIDER)
                )
                .build();
        log.info("[ChatClientRegistry] Registered provider: {}", PROVIDER);
    }

    /** Returns the OpenAI ChatClient regardless of the requested provider name. */
    public ChatClient get(String provider) {
        return openAiClient;
    }

    public List<String> availableProviders() {
        return List.of(PROVIDER);
    }

    public boolean hasProvider(String provider) {
        return PROVIDER.equalsIgnoreCase(provider);
    }
}
