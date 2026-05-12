package com.sahernandezz.nexusai.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * pgvector cosine-similarity query for semantic cache lookups.
 * Uses native SQL with the {@code <=>} operator (cosine distance).
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class SemanticCacheRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final String INSERT_SQL = """
            INSERT INTO semantic_cache (prompt, response, embedding)
            VALUES (?, ?, ?::vector)
            ON CONFLICT DO NOTHING
            """;

    private static final String LOOKUP_SQL = """
            SELECT response
            FROM semantic_cache
            WHERE 1 - (embedding <=> ?::vector) >= ?
            ORDER BY embedding <=> ?::vector
            LIMIT 1
            """;

    public void save(SemanticCacheEntry entry) {
        String vectorLiteral = toVectorLiteral(entry.embedding());
        jdbcTemplate.update(INSERT_SQL, entry.prompt(), entry.response(), vectorLiteral);
    }

    public Optional<SemanticCacheEntry> findBySimilarity(float[] queryEmbedding, double threshold) {
        String vectorLiteral = toVectorLiteral(queryEmbedding);
        var results = jdbcTemplate.query(
                LOOKUP_SQL,
                (rs, rowNum) -> new SemanticCacheEntry(null, rs.getString("response"), null),
                vectorLiteral, threshold, vectorLiteral);

        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    private static String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(embedding[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}

