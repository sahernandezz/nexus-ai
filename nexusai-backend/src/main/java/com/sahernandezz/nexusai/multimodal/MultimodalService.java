package com.sahernandezz.nexusai.multimodal;

import com.sahernandezz.nexusai.chat.ChatClientRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Multimodal Service.
 *
 * <ul>
 * <li><b>image -> text</b>: GPT-4o (or whatever the chat model is) with
 * vision</li>
 * <li><b>text -> image</b>: configurable — defaults to gpt-image-1 (sharp text
 * in
 * diagrams), falls back to dall-e-3 if your org isn't verified.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultimodalService {

    private final ChatClientRegistry registry;

    @Autowired(required = false)
    private ImageModel imageModel;

    @Value("${spring.ai.openai.image.options.model:gpt-image-1}")
    private String imageModelName;

    @Value("${spring.ai.openai.image.options.quality:high}")
    private String imageQuality;

    // ── Image -> Text (vision via OpenAI) ───────────────────────────────────

    public Mono<String> describeImage(Resource imageResource, String mimeType, String instruction) {
        return Mono.fromCallable(() -> {
            log.info("[Multimodal] describeImage | mimeType={} file={}", mimeType, imageResource.getFilename());

            UserMessage userMessage = UserMessage.builder()
                    .text(instruction != null ? instruction : "Describe this image in detail.")
                    .media(new Media(MimeType.valueOf(mimeType), imageResource))
                    .build();

            return registry.get("openai")
                    .prompt(new Prompt(userMessage))
                    .call()
                    .content();
        }).subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(ex -> {
                    log.error("[Multimodal] describeImage error: {}", ex.getMessage());
                    return Mono.error(ex);
                });
    }

    // ── Text -> Image (gpt-image-1 / dall-e-3) ──────────────────────────────

    public Mono<String> generateImage(String prompt, String size, String quality, String modelOverride) {
        return Mono.fromCallable(() -> {
            if (imageModel == null) {
                log.warn("[Multimodal] generateImage — no ImageModel configured (set OPENAI_API_KEY)");
                return "Image generation requires OPENAI_API_KEY. " +
                        "Set the environment variable and restart the application.";
            }
            String resolvedModel = (modelOverride != null && !modelOverride.isBlank())
                    ? modelOverride
                    : (imageModelName == null || imageModelName.isBlank() ? "gpt-image-1" : imageModelName);
            String resolvedQuality = quality == null || quality.isBlank()
                    ? imageQuality
                    : quality;
            String resolvedSize = size == null || size.isBlank()
                    ? "1024x1024"
                    : size;
            int[] dims = parseSize(resolvedSize);

            log.info("[Multimodal] generateImage | model={} quality={} size={} prompt={}",
                    resolvedModel, resolvedQuality, resolvedSize,
                    prompt.substring(0, Math.min(80, prompt.length())));

            var response = imageModel.call(new ImagePrompt(prompt,
                    OpenAiImageOptions.builder()
                            .model(resolvedModel)
                            .quality(normalizeQuality(resolvedModel, resolvedQuality))
                            .width(dims[0])
                            .height(dims[1])
                            .build()));

            var output = response.getResult().getOutput();
            // dall-e-3 returns a hosted URL; gpt-image-1 returns base64 in b64Json.
            if (output.getUrl() != null && !output.getUrl().isBlank()) {
                return output.getUrl();
            }
            if (output.getB64Json() != null && !output.getB64Json().isBlank()) {
                return "data:image/png;base64," + output.getB64Json();
            }
            throw new IllegalStateException("Image response had neither URL nor base64 payload");
        }).subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(ex -> {
                    log.error("[Multimodal] generateImage error: {}", ex.getMessage());
                    return Mono.error(ex);
                });
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static int[] parseSize(String s) {
        try {
            String[] parts = s.toLowerCase().split("x");
            return new int[] { Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()) };
        } catch (Exception e) {
            return new int[] { 1024, 1024 };
        }
    }

    /**
     * dall-e-3 accepts: standard | hd
     * gpt-image-1 accepts: low | medium | high | auto
     * Map between them so the same client option works on either model.
     */
    private static String normalizeQuality(String model, String quality) {
        boolean isGptImage = model != null && model.toLowerCase().startsWith("gpt-image");
        if (isGptImage) {
            return switch (quality.toLowerCase()) {
                case "standard" -> "medium";
                case "hd" -> "high";
                case "low", "medium", "high", "auto" -> quality.toLowerCase();
                default -> "high";
            };
        }
        // dall-e-* models
        return switch (quality.toLowerCase()) {
            case "high", "hd" -> "hd";
            default -> "standard";
        };
    }
}
