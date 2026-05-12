package com.sahernandezz.nexusai.chat.advisor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.core.Ordered;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Records Prometheus metrics for every LLM call.
 * Metrics:
 *   nexusai.llm.requests  {provider, outcome=success|error}
 *   nexusai.llm.tokens    {provider, type=prompt|completion|stream_chunks}
 *   nexusai.llm.latency   {provider}                                  (seconds)
 */
@Slf4j
@RequiredArgsConstructor
public class MetricsAdvisor implements CallAdvisor, StreamAdvisor {

    private final MeterRegistry meterRegistry;
    private final String provider;

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            ChatClientResponse response = chain.nextCall(request);
            recordTokens(response);
            incrementRequests("success");
            return response;
        } catch (Exception e) {
            incrementRequests("error");
            throw e;
        } finally {
            sample.stop(Timer.builder("nexusai.llm.latency")
                    .tag("provider", provider)
                    .register(meterRegistry));
        }
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        Timer.Sample sample = Timer.start(meterRegistry);
        AtomicLong chunkCount = new AtomicLong(0);

        return chain.nextStream(request)
                .doOnNext(r -> chunkCount.incrementAndGet())
                .doOnComplete(() -> {
                    sample.stop(Timer.builder("nexusai.llm.latency")
                            .tag("provider", provider)
                            .register(meterRegistry));
                    incrementRequests("success");
                    Counter.builder("nexusai.llm.tokens")
                            .tag("provider", provider)
                            .tag("type", "stream_chunks")
                            .register(meterRegistry)
                            .increment(chunkCount.get());
                })
                .doOnError(e -> incrementRequests("error"));
    }

    @Override
    public String getName() {
        return "MetricsAdvisor";
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 10;
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void incrementRequests(String outcome) {
        Counter.builder("nexusai.llm.requests")
                .tag("provider", provider)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    private void recordTokens(ChatClientResponse response) {
        if (response.chatResponse() == null
                || response.chatResponse().getMetadata() == null
                || response.chatResponse().getMetadata().getUsage() == null) {
            return;
        }
        var usage = response.chatResponse().getMetadata().getUsage();

        Long promptTokens = usage.getPromptTokens() != null
                ? usage.getPromptTokens().longValue() : null;
        Long completionTokens = usage.getCompletionTokens() != null
                ? usage.getCompletionTokens().longValue() : null;

        if (promptTokens != null) {
            Counter.builder("nexusai.llm.tokens")
                    .tag("provider", provider)
                    .tag("type", "prompt")
                    .register(meterRegistry)
                    .increment(promptTokens);
        }
        if (completionTokens != null) {
            Counter.builder("nexusai.llm.tokens")
                    .tag("provider", provider)
                    .tag("type", "completion")
                    .register(meterRegistry)
                    .increment(completionTokens);
        }
    }
}

