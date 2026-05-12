-- M5: Semantic cache table using pgvector
CREATE TABLE IF NOT EXISTS semantic_cache (
    id         BIGSERIAL    PRIMARY KEY,
    prompt     TEXT         NOT NULL,
    response   TEXT         NOT NULL,
    embedding  vector(768),   -- nomic-embed-text-v1.5 = 768 dims
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_semantic_cache_embedding
    ON semantic_cache USING ivfflat (embedding vector_cosine_ops) WITH (lists = 10);

