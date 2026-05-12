package com.sahernandezz.nexusai.config;

import com.sahernandezz.nexusai.auth.dto.LoginRequest;
import com.sahernandezz.nexusai.auth.dto.LoginResponse;
import com.sahernandezz.nexusai.cache.CachedResponse;
import com.sahernandezz.nexusai.chat.dto.ChatRequest;
import com.sahernandezz.nexusai.health.dto.HealthResponse;
import com.sahernandezz.nexusai.rag.dto.DocumentDto;
import com.sahernandezz.nexusai.rag.ingestion.IngestEvent;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * GraalVM Native Image — Runtime Hints Registration.
 *
 * <p>Registers all types that require reflective access, resources, proxies,
 * or serialization at AOT compile time. Spring Boot's native build plugin
 * picks these up via the {@code @ImportRuntimeHints} on {@code NexusAiApplication}.
 *
 * <h2>Build commands</h2>
 * <pre>{@code
 * # Requires GraalVM 25+ (native-image on PATH)
 * ./gradlew nativeBuild           → binary at build/native/nativeBuild/nexusai-backend
 * ./gradlew nativeTest            → run tests as native binary
 * ./gradlew bootBuildImage        → OCI image via Buildpacks (no local GraalVM needed)
 * }</pre>
 */
public class NativeRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(@NonNull RuntimeHints hints, @Nullable ClassLoader classLoader) {

        // ── Application DTOs (Records) ────────────────────────────────────────
        // Java records need full reflection for Jackson serialization / deserialization
        registerAllMembers(hints,
                ChatRequest.class,
                LoginRequest.class,
                LoginResponse.class,
                HealthResponse.class,
                DocumentDto.class,
                IngestEvent.class,
                CachedResponse.class
        );

        // ── JJWT — JWT parsing uses heavy reflection ──────────────────────────
        registerClassesByName(hints,
                "io.jsonwebtoken.Claims",
                "io.jsonwebtoken.Header",
                "io.jsonwebtoken.JwsHeader",
                "io.jsonwebtoken.impl.DefaultClaims",
                "io.jsonwebtoken.impl.DefaultHeader",
                "io.jsonwebtoken.impl.DefaultJwsHeader",
                "io.jsonwebtoken.impl.DefaultJws",
                "io.jsonwebtoken.impl.DefaultJwtParser",
                "io.jsonwebtoken.impl.DefaultJwtParserBuilder",
                "io.jsonwebtoken.impl.DefaultJwtBuilder",
                "io.jsonwebtoken.jackson.io.JacksonDeserializer",
                "io.jsonwebtoken.jackson.io.JacksonSerializer"
        );

        // ── Spring AI — Chat / Embedding response types ───────────────────────
        registerClassesByName(hints,
                "org.springframework.ai.chat.messages.AssistantMessage",
                "org.springframework.ai.chat.messages.UserMessage",
                "org.springframework.ai.chat.messages.SystemMessage",
                "org.springframework.ai.chat.metadata.ChatGenerationMetadata",
                "org.springframework.ai.chat.metadata.DefaultChatResponseMetadata",
                "org.springframework.ai.openai.api.OpenAiApi$ChatCompletion",
                "org.springframework.ai.openai.api.OpenAiApi$ChatCompletionChunk",
                "org.springframework.ai.openai.api.OpenAiApi$ChatCompletionMessage",
                "org.springframework.ai.openai.api.OpenAiApi$EmbeddingList",
                "org.springframework.ai.openai.api.OpenAiApi$Embedding"
        );

        // ── Reactor / Project Reactor ─────────────────────────────────────────
        registerClassesByName(hints,
                "reactor.core.publisher.Flux",
                "reactor.core.publisher.Mono"
        );

        // ── Application resources ─────────────────────────────────────────────
        hints.resources().registerPattern("application.yml");
        hints.resources().registerPattern("application-dev.yml");
        hints.resources().registerPattern("application-prod.yml");
        hints.resources().registerPattern("application-test.yml");
        hints.resources().registerPattern("db/migration/*.sql");
        // Spring AI internal metadata
        hints.resources().registerPattern("org/springframework/ai/**/*.json");
        hints.resources().registerPattern("org/springframework/ai/**/*.yaml");
        // Logback
        hints.resources().registerPattern("logback-spring.xml");
        hints.resources().registerPattern("logback.xml");

        // ── JDK proxy interfaces (Spring AOP / transactional if used) ─────────
        // WebFlux functional router — no classic @RestController proxies needed

        // ── Serialization (Jackson) ───────────────────────────────────────────
        // Java records already registered above; add any remaining Jackson types
        registerClassesByName(hints,
                "com.fasterxml.jackson.databind.ser.std.StringSerializer",
                "com.fasterxml.jackson.databind.deser.std.StringDeserializer"
        );
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static void registerAllMembers(RuntimeHints hints, Class<?>... classes) {
        for (Class<?> clazz : classes) {
            hints.reflection().registerType(clazz,
                    MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                    MemberCategory.INVOKE_PUBLIC_METHODS,
                    MemberCategory.PUBLIC_FIELDS,
                    MemberCategory.DECLARED_FIELDS
            );
        }
    }

    private static void registerClassesByName(RuntimeHints hints, String... classNames) {
        for (String name : classNames) {
            try {
                Class<?> clazz = Class.forName(name);
                hints.reflection().registerType(clazz,
                        MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                        MemberCategory.INVOKE_PUBLIC_METHODS,
                        MemberCategory.PUBLIC_FIELDS,
                        MemberCategory.DECLARED_FIELDS
                );
            } catch (ClassNotFoundException ignored) {
                // class may not be on classpath (optional dependency) — skip
            }
        }
    }
}

