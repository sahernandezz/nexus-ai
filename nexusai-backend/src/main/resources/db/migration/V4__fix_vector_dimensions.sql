-- ─── V4 — Fix vector dimensions: 1536 → 768 ──────────────────────────────────
-- nomic-embed-text-v1.5 (LM Studio local model) produces 768-dimensional embeddings.
-- The initial schema used 1536 (OpenAI text-embedding-3-small).
-- This migration drops the mismatched indexes, truncates stale data, and
-- re-creates both columns and indexes with the correct 768 dimensions.

-- ─── 1. vector_store ──────────────────────────────────────────────────────────
-- Drop the dimension-specific ivfflat index first (required before altering type)
DROP INDEX IF EXISTS idx_vector_store_embedding;

-- Clear stale 1536-dim rows — they cannot be reused or cast to 768 dims
TRUNCATE TABLE vector_store;

-- Re-type the column to 768 dimensions
ALTER TABLE vector_store
    ALTER COLUMN embedding TYPE vector(768)
    USING NULL;    -- rows are gone; USING NULL avoids cast errors on empty table

-- Rebuild the ivfflat index for 768-dim cosine search
-- (lists = 10 is fine for a freshly empty table; pgvector recommends ≈ sqrt(rows))
CREATE INDEX IF NOT EXISTS idx_vector_store_embedding
    ON vector_store USING ivfflat (embedding vector_cosine_ops) WITH (lists = 10);

-- ─── 2. semantic_cache ────────────────────────────────────────────────────────
DROP INDEX IF EXISTS idx_semantic_cache_embedding;

TRUNCATE TABLE semantic_cache;

ALTER TABLE semantic_cache
    ALTER COLUMN embedding TYPE vector(768)
    USING NULL;

CREATE INDEX IF NOT EXISTS idx_semantic_cache_embedding
    ON semantic_cache USING ivfflat (embedding vector_cosine_ops) WITH (lists = 10);

