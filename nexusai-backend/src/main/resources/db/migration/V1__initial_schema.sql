-- ─── V1 — Initial Schema ───────────────────────────────────────────────────────
-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ─── Users ────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id         UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    username   VARCHAR(100) NOT NULL UNIQUE,
    email      VARCHAR(255) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    role       VARCHAR(20)  NOT NULL DEFAULT 'USER',
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ─── Conversations ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS conversations (
    id            UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id       UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title         VARCHAR(500),
    model         VARCHAR(100),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ─── Chat Messages (JDBC Chat Memory) ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS chat_messages (
    id              UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    conversation_id UUID         NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    session_id      VARCHAR(255) NOT NULL,
    role            VARCHAR(20)  NOT NULL,   -- USER | ASSISTANT | SYSTEM
    content         TEXT         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chat_messages_session ON chat_messages(session_id);
CREATE INDEX IF NOT EXISTS idx_chat_messages_conversation ON chat_messages(conversation_id);

-- ─── Documents ────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS documents (
    id          UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    filename    VARCHAR(500) NOT NULL,
    content_type VARCHAR(100),
    size_bytes  BIGINT,
    status      VARCHAR(30)  NOT NULL DEFAULT 'PENDING',   -- PENDING | PROCESSING | INDEXED | FAILED
    error_msg   TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ─── Vector Store (pgvector embeddings) ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS vector_store (
    id        UUID   PRIMARY KEY DEFAULT uuid_generate_v4(),
    content   TEXT   NOT NULL,
    metadata  JSONB,
    embedding vector(768)   -- nomic-embed-text-v1.5 (LM Studio local) = 768 dims
);

CREATE INDEX IF NOT EXISTS idx_vector_store_embedding
    ON vector_store USING ivfflat (embedding vector_cosine_ops) WITH (lists = 10);

-- ─── Audit Log ────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS audit_log (
    id          UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID         REFERENCES users(id),
    action      VARCHAR(100) NOT NULL,
    resource    VARCHAR(255),
    details     JSONB,
    ip_address  VARCHAR(45),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_log_user ON audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_created ON audit_log(created_at DESC);

-- ─── Default dev users (passwords: BCrypt of 'admin123', 'user123') ──────────
INSERT INTO users (username, email, password, role)
VALUES
    ('admin', 'admin@nexusai.local', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'ADMIN'),
    ('user',  'user@nexusai.local',  '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'USER')
ON CONFLICT (username) DO NOTHING;

