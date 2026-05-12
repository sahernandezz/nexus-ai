package com.sahernandezz.nexusai.cache;

/**
 * A pgvector-backed cache entry (table: semantic_cache).
 */
public record SemanticCacheEntry(
        String prompt,
        String response,
        float[] embedding
) {}

