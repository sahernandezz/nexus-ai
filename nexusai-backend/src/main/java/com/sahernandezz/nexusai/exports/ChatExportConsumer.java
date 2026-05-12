package com.sahernandezz.nexusai.exports;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sahernandezz.nexusai.config.RabbitMQConfig;
import com.sahernandezz.nexusai.conversations.ConversationService;
import com.sahernandezz.nexusai.conversations.dto.ConversationDto;
import com.sahernandezz.nexusai.files.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Consumes export jobs from RabbitMQ, builds a zip with one JSON per
 * conversation, uploads it to MinIO via {@link FileService}, and links the
 * resulting attachment back to the export record.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatExportConsumer {

        private final ChatExportService exportService;
        private final ConversationService conversationService;
        private final FileService fileService;
        private final ObjectMapper objectMapper;

        @RabbitListener(queues = RabbitMQConfig.EXPORT_JOB_QUEUE)
        public void onExportJob(@Payload ExportJobEvent event) {
                log.info("[Export] Worker picked up exportId={}", event.exportId());

                exportService.markProcessing(event.exportId())
                                .then(loadConversations(event))
                                .flatMap(convs -> buildAndStoreZip(event, convs))
                                .flatMap(attachmentId -> exportService.markReady(event.exportId(), attachmentId))
                                .doOnSuccess(e -> log.info("[Export] Ready exportId={} attachmentId={}",
                                                event.exportId(), e.attachmentId()))
                                .onErrorResume(ex -> {
                                        log.error("[Export] Failed exportId={}: {}", event.exportId(), ex.getMessage(),
                                                        ex);
                                        return exportService.markFailed(event.exportId(), ex.getMessage());
                                })
                                .block(); // Rabbit listener thread is fine to block on the reactive chain
        }

        private reactor.core.publisher.Mono<List<ConversationDto>> loadConversations(ExportJobEvent event) {
                Flux<ConversationDto> source = event.conversationId() != null
                                ? conversationService.getWithMessages(event.conversationId(), event.userId()).flux()
                                : conversationService.listForUser(event.userId())
                                                .flatMap(meta -> conversationService.getWithMessages(meta.id(),
                                                                event.userId()));
                return source.collectList();
        }

        private reactor.core.publisher.Mono<java.util.UUID> buildAndStoreZip(
                        ExportJobEvent event, List<ConversationDto> convs) {
                return reactor.core.publisher.Mono.fromCallable(() -> {
                        // ZIP building is CPU-bound; run on bounded elastic to avoid blocking IO
                        // threads
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        try (ZipOutputStream zip = new ZipOutputStream(out)) {
                                // Manifest with metadata (count, timestamp, etc.)
                                zip.putNextEntry(new ZipEntry("manifest.json"));
                                zip.write(objectMapper.writerWithDefaultPrettyPrinter()
                                                .writeValueAsBytes(new ExportManifest(
                                                                event.exportId().toString(),
                                                                event.userId().toString(),
                                                                java.time.Instant.now().toString(),
                                                                convs.size())));
                                zip.closeEntry();

                                for (ConversationDto conv : convs) {
                                        String safeTitle = conv.title() == null ? "untitled"
                                                        : conv.title().replaceAll("[^a-zA-Z0-9-_ ]", "").trim();
                                        if (safeTitle.isEmpty())
                                                safeTitle = conv.id().toString();
                                        String entry = "conversations/" + safeTitle + " — " + conv.id() + ".json";
                                        zip.putNextEntry(new ZipEntry(entry));
                                        zip.write(objectMapper.writerWithDefaultPrettyPrinter()
                                                        .writeValueAsBytes(conv));
                                        zip.closeEntry();
                                }
                        }
                        return out.toByteArray();
                })
                                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                                .flatMap(bytes -> {
                                        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                                                        .withZone(java.time.ZoneId.systemDefault())
                                                        .format(java.time.Instant.now());
                                        String filename = "nexusai-chats-" + stamp + ".zip";
                                        return fileService.storeBytes(
                                                        event.userId(), event.conversationId(),
                                                        bytes, filename, "application/zip", "export")
                                                        .map(att -> att.id());
                                });
        }

        private record ExportManifest(String exportId, String userId, String generatedAt, int conversations) {
        }
}
