-- ─── V6 — Update vector dimensions: 768 → 1536 ───────────────────────────────
-- Switched from LM Studio nomic-embed-text (768 dims)
-- to OpenAI text-embedding-3-small (1536 dims).
-- Existing embeddings are incompatible — both tables are truncated.

-- ─── 1. vector_store ──────────────────────────────────────────────────────────
DROP INDEX IF EXISTS idx_vector_store_embedding;

TRUNCATE TABLE vector_store;

ALTER TABLE vector_store
    ALTER COLUMN embedding TYPE vector(1536)
    USING NULL;

CREATE INDEX IF NOT EXISTS idx_vector_store_embedding
    ON vector_store USING ivfflat (embedding vector_cosine_ops) WITH (lists = 10);

-- ─── 2. semantic_cache ────────────────────────────────────────────────────────
DROP INDEX IF EXISTS idx_semantic_cache_embedding;

TRUNCATE TABLE semantic_cache;

ALTER TABLE semantic_cache
    ALTER COLUMN embedding TYPE vector(1536)
    USING NULL;

CREATE INDEX IF NOT EXISTS idx_semantic_cache_embedding
    ON semantic_cache USING ivfflat (embedding vector_cosine_ops) WITH (lists = 10);

