package com.sahernandezz.nexusai.chat.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Locale;

/**
 * Custom prompt safety advisor. Blocks requests containing prohibited phrases
 * before they reach the LLM. Runs at HIGHEST_PRECEDENCE.
 *
 * Spring AI 1.0.0 already ships a built-in SafeGuardAdvisor
 * ({@link org.springframework.ai.chat.client.advisor.SafeGuardAdvisor}).
 * This custom version throws a 400 instead of returning a static failure message,
 * which gives the frontend a proper HTTP error to display.
 */
@Slf4j
public class SafeGuardAdvisor implements CallAdvisor, StreamAdvisor {

    private static final List<String> DEFAULT_BLOCKED = List.of(
            "ignore previous instructions",
            "disregard your guidelines",
            "jailbreak",
            "dan mode",
            "act as if you have no restrictions"
    );

    private final List<String> blockedPhrases;

    public SafeGuardAdvisor() {
        this(DEFAULT_BLOCKED);
    }

    public SafeGuardAdvisor(List<String> blockedPhrases) {
        this.blockedPhrases = blockedPhrases;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        guardPrompt(getUserText(request));
        return chain.nextCall(request);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        try {
            guardPrompt(getUserText(request));
        } catch (ResponseStatusException ex) {
            return Flux.error(ex);
        }
        return chain.nextStream(request);
    }

    @Override
    public String getName() {
        return "NexusAI-SafeGuardAdvisor";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void guardPrompt(String text) {
        if (text == null) return;
        String lower = text.toLowerCase(Locale.ROOT);
        for (String blocked : blockedPhrases) {
            if (lower.contains(blocked)) {
                log.warn("[SafeGuard] Prompt blocked — matched phrase: '{}'", blocked);
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Prompt was blocked by safety guidelines."
                );
            }
        }
    }

    private String getUserText(ChatClientRequest request) {
        try {
            var userMsg = request.prompt().getUserMessage();
            return userMsg != null ? userMsg.getText() : null;
        } catch (Exception e) {
            return request.prompt().getContents();
        }
    }
}
