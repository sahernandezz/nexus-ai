package com.sahernandezz.nexusai.rag.retrieval;

import com.sahernandezz.nexusai.cache.SemanticCacheService;
import com.sahernandezz.nexusai.chat.ChatClientRegistry;
import com.sahernandezz.nexusai.chat.dto.ChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * RAG-aware chat service.
 * Wraps ChatService with a RetrievalAugmentationAdvisor that injects
 * relevant document chunks as context before calling the LLM.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final ChatClientRegistry registry;
    private final VectorStore vectorStore;
    private final SemanticCacheService cacheService;

    private static final int TOP_K = 8;
    // Permissive threshold so paraphrased questions still pull relevant chunks.
    // The LLM filters irrelevant context downstream; under-retrieval is worse
    // than over-retrieval at this stage.
    private static final double SIMILARITY_THRESHOLD = 0.2;

    // System instruction prepended on every RAG turn. Keeps the model anchored
    // to the retrieved chunks and answers in Spanish/English depending on the
    // user message. The retrieved context itself is injected by Spring AI's
    // RetrievalAugmentationAdvisor afterwards.
    private static final String RAG_SYSTEM_PROMPT = """
            Eres un asistente que responde preguntas usando EXCLUSIVAMENTE
            la informacion contenida en los fragmentos del documento que
            se te proporcionan como contexto.

            Reglas:
            1. Si la respuesta esta en el contexto, responde de forma directa
               y concreta, citando datos exactos (numeros, fechas, nombres).
            2. Si la pregunta es ambigua, asume la interpretacion mas
               probable segun el documento y responde a esa.
            3. Si la informacion NO esta en el contexto, di explicitamente
               'No encuentro esa informacion en el documento' en lugar de
               inventar.
            4. Responde en el mismo idioma de la pregunta del usuario.
            5. Usa formato Markdown cuando ayude (listas, tablas, negritas).
            """;

    // ── Streaming RAG (cache-aware) ──────────────────────────────────────────

    public Flux<ServerSentEvent<String>> streamWithRag(ChatRequest request, String documentId) {
        log.info("[RAG] streamWithRag | provider={} docId={} question={}",
                request.resolvedProvider(), documentId,
                request.message().substring(0, Math.min(60, request.message().length())));

        // Cache key includes the document scope so different docs do not collide
        String cacheKey = (documentId == null ? "" : "doc:" + documentId + "|") + request.message();

        return cacheService.lookup(cacheKey)
                .flatMapMany(opt -> {
                    if (opt.isPresent()) {
                        var cached = opt.get();
                        String content = cached.content();
                        log.info("[RAG] cache hit | layer={} chars={}", cached.layer(),
                                content == null ? 0 : content.length());
                        // Defense: a stored-empty entry is useless and would render
                        // an empty bubble in the UI. Treat as miss so we re-query
                        // the LLM and overwrite the bad cache entry.
                        if (content == null || content.isBlank()) {
                            log.warn("[RAG] cache hit had empty content — treating as miss");
                        } else {
                            return Flux.concat(
                                    Flux.just(metaEvent(true, cached.layer())),
                                    chunked(content));
                        }
                    }
                    ChatClient client = registry.get(request.resolvedProvider());
                    RetrievalAugmentationAdvisor ragAdvisor = buildRagAdvisor(documentId);
                    StringBuilder buf = new StringBuilder();
                    Flux<ServerSentEvent<String>> tokens = client.prompt()
                            .system(RAG_SYSTEM_PROMPT)
                            .user(request.message())
                            .advisors(ragAdvisor)
                            .stream()
                            .content()
                            .doOnNext(buf::append)
                            .map(this::messageEvent);
                    return Flux.concat(
                            Flux.just(metaEvent(false, null)),
                            tokens
                    ).doOnComplete(() -> {
                        String full = buf.toString();
                        if (!full.isBlank()) {
                            cacheService.store(cacheKey, full).subscribe();
                        }
                    });
                })
                .onErrorResume(ex -> {
                    log.error("[RAG] stream error: {}", ex.getMessage());
                    return Flux.error(ex);
                });
    }

    // ─────────────────────────────────────────────────────────────────────────

    private RetrievalAugmentationAdvisor buildRagAdvisor(String documentId) {
        VectorStoreDocumentRetriever.Builder retrieverBuilder = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .topK(TOP_K)
                .similarityThreshold(SIMILARITY_THRESHOLD);

        if (documentId != null && !documentId.isBlank()) {
            FilterExpressionBuilder filter = new FilterExpressionBuilder();
            retrieverBuilder.filterExpression(
                    filter.eq("documentId", documentId).build());
        }

        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(retrieverBuilder.build())
                .build();
    }

    private ServerSentEvent<String> metaEvent(boolean cached, String layer) {
        String json = layer != null
                ? "{\"cached\":" + cached + ",\"layer\":\"" + layer + "\"}"
                : "{\"cached\":" + cached + "}";
        return ServerSentEvent.<String>builder()
                .event("meta")
                .data(json)
                .build();
    }

    private ServerSentEvent<String> messageEvent(String token) {
        return ServerSentEvent.<String>builder()
                .event("message")
                .data(token)
                .build();
    }

    /**
     * Splits a (potentially large) cached response into bite-sized SSE events.
     * Three reasons:
     * <ol>
     *   <li>Robustness — single huge {@code data:} payloads are more likely to
     *       hit serialization edge cases or proxy buffers.</li>
     *   <li>UX — the user sees the response stream in, matching the
     *       look-and-feel of a fresh LLM response.</li>
     *   <li>Backpressure — many small events flow through the reactive
     *       pipeline more naturally than one fat one.</li>
     * </ol>
     */
    private static final int CHUNK_SIZE = 80;

    private Flux<ServerSentEvent<String>> chunked(String content) {
        if (content.length() <= CHUNK_SIZE) {
            return Flux.just(messageEvent(content));
        }
        return Flux.create(sink -> {
            int i = 0;
            while (i < content.length()) {
                int end = Math.min(i + CHUNK_SIZE, content.length());
                sink.next(messageEvent(content.substring(i, end)));
                i = end;
            }
            sink.complete();
        });
    }
}
