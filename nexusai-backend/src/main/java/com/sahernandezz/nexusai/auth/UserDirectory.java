package com.sahernandezz.nexusai.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the authenticated user's database UUID from their JWT username.
 *
 * Spring Security's principal in this app carries the username (string). All
 * persistence is keyed by the {@code users.id} UUID, so we maintain a tiny
 * in-process cache from username → UUID, populated lazily from the
 * {@code users} table. New users are auto-inserted on first lookup so the
 * in-memory user store ({@link com.sahernandezz.nexusai.config.SecurityConfig})
 * stays in sync with the database without a separate migration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserDirectory {

    private final DatabaseClient db;
    private final ConcurrentHashMap<String, UUID> cache = new ConcurrentHashMap<>();

    /** Returns the current authenticated principal's user UUID. */
    public Mono<UUID> currentUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getName())
                .flatMap(this::resolve);
    }

    /** Resolves a username → UUID, auto-inserting a row if missing. */
    public Mono<UUID> resolve(String username) {
        UUID cached = cache.get(username);
        if (cached != null) return Mono.just(cached);

        return db.sql("SELECT id FROM users WHERE username = :u")
                .bind("u", username)
                .map(row -> row.get("id", UUID.class))
                .one()
                .switchIfEmpty(Mono.defer(() -> insertUser(username)))
                .doOnNext(id -> cache.put(username, id));
    }

    private Mono<UUID> insertUser(String username) {
        UUID newId = UUID.randomUUID();
        // Stub email + bcrypt placeholder; the JWT layer is the actual
        // auth source of truth, this row only exists so foreign keys resolve.
        return db.sql("""
                INSERT INTO users (id, username, email, password, role)
                VALUES (:id, :u, :email, '$2a$10$placeholderxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx', 'USER')
                ON CONFLICT (username) DO UPDATE SET updated_at = NOW()
                RETURNING id
                """)
                .bind("id", newId)
                .bind("u", username)
                .bind("email", username + "@nexusai.local")
                .map(row -> row.get("id", UUID.class))
                .one()
                .doOnNext(id -> log.info("[UserDirectory] Auto-provisioned user row for '{}' → {}", username, id));
    }
}
