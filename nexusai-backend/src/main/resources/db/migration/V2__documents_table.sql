-- M3: Upload storage table (lightweight alternative to full R2DBC repo)
CREATE TABLE IF NOT EXISTS documents (
    id          VARCHAR(36)  PRIMARY KEY,
    filename    VARCHAR(512) NOT NULL,
    content_type VARCHAR(128),
    size_bytes  BIGINT,
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    error_msg   TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_documents_status ON documents(status);

