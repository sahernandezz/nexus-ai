package com.sahernandezz.nexusai.cache;

/**
 * Wrapper for a cached LLM response.
 *
 * @param content    the cached response text
 * @param fromCache  always true
 * @param layer      "exact" or "semantic"
 */
public record CachedResponse(String content, boolean fromCache, String layer) {}

