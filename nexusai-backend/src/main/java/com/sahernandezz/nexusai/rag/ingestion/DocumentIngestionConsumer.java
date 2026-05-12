package com.sahernandezz.nexusai.rag.ingestion;

import com.sahernandezz.nexusai.config.MinioStorageService;
import com.sahernandezz.nexusai.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.core.io.InputStreamResource;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Async RabbitMQ consumer that:
 * 1. Downloads the file from MinIO
 * 2. Reads PDF / plain text → Spring AI Documents
 * 3. Splits with TokenTextSplitter
 * 4. Adds embeddings to PgVectorStore
 * 5. Updates document status
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentIngestionConsumer {

    private final VectorStore vectorStore;
    private final DocumentIngestionService ingestionService;
    private final MinioStorageService minioStorageService;

    private final TokenTextSplitter splitter = new TokenTextSplitter(
            800,    // defaultChunkSize (tokens)
            350,    // minChunkSizeChars
            5,      // minChunkLengthToEmbed
            10_000, // maxNumChunks
            true    // keepSeparator
    );

    @RabbitListener(queues = RabbitMQConfig.DOCUMENT_INGEST_QUEUE)
    public void consume(@Payload IngestEvent event) {
        String docId = event.documentId();
        log.info("[RAG] Consuming ingest event: docId={} minioKey={}", docId, event.storagePath());

        ingestionService.updateStatus(docId, DocumentStatus.PROCESSING, null);

        try {
            List<Document> chunks = readAndChunk(event);
            log.info("[RAG] Chunked {} → {} chunks", event.filename(), chunks.size());

            vectorStore.add(chunks);
            log.info("[RAG] Indexed {} chunks for docId={}", chunks.size(), docId);

            ingestionService.updateStatus(docId, DocumentStatus.INDEXED, null);
        } catch (Exception e) {
            log.error("[RAG] Failed to ingest docId={}: {}", docId, e.getMessage(), e);
            ingestionService.updateStatus(docId, DocumentStatus.FAILED, e.getMessage());
        }
        // NOTE: we do NOT delete from MinIO here so documents can be re-processed if needed
    }

    // ─────────────────────────────────────────────────────────────────────────

    private List<Document> readAndChunk(IngestEvent event) {
        // Download file from MinIO
        try (InputStream inputStream = minioStorageService.download(event.storagePath())) {

            List<Document> rawDocs;
            if ("application/pdf".equals(event.contentType())
                    || event.filename().toLowerCase().endsWith(".pdf")) {

                PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                        .withPagesPerDocument(1)
                        .build();
                // Buffer stream into a resource (PDF reader needs mark/reset support)
                byte[] bytes = inputStream.readAllBytes();
                InputStreamResource resource = new InputStreamResource(
                        new java.io.ByteArrayInputStream(bytes));
                PagePdfDocumentReader reader = new PagePdfDocumentReader(resource, config);
                rawDocs = reader.read();
            } else {
                // Fallback: treat as plain text
                String text = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                rawDocs = List.of(new Document(
                        text,
                        Map.of("source", event.filename(), "documentId", event.documentId())));
            }

            // Attach metadata to all chunks
            rawDocs.forEach(doc -> {
                doc.getMetadata().put("documentId", event.documentId());
                doc.getMetadata().put("filename",   event.filename());
                doc.getMetadata().put("minioKey",   event.storagePath());
            });

            return splitter.apply(rawDocs);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read document from MinIO: " + e.getMessage(), e);
        }
    }
}
