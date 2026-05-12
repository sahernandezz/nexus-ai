-- ─── V7 — Persistent chat history (cross-browser sync) ─────────────────────────
-- Extends chat_messages so the React frontend can store full message metadata
-- server-side (instead of localStorage), and adds tables for binary attachments
-- (images, PDFs) and asynchronous chat-export jobs (RabbitMQ -> MinIO).

-- ─── 1. Extend chat_messages ───────────────────────────────────────────────────
ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS message_index   INTEGER,                  -- ordering inside conversation
    ADD COLUMN IF NOT EXISTS attachments     JSONB,                    -- array of {refId, type, name, size, mimeType}
    ADD COLUMN IF NOT EXISTS rag_action      JSONB,                    -- {state, filename, docId}
    ADD COLUMN IF NOT EXISTS cached          BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS cache_layer     VARCHAR(20),
    ADD COLUMN IF NOT EXISTS model           VARCHAR(80),
    ADD COLUMN IF NOT EXISTS client_id       UUID;                     -- id chosen by the client (idempotent upsert key)

-- chat_messages.session_id was originally NOT NULL because Spring AI's JDBC chat
-- memory writes there too. The new frontend writes don't carry a session id —
-- relax the constraint so both producers can coexist. We default to the
-- conversation_id casted as text when the column is missing.
ALTER TABLE chat_messages
    ALTER COLUMN session_id DROP NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_chat_messages_client_id ON chat_messages(client_id) WHERE client_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_chat_messages_conv_order      ON chat_messages(conversation_id, message_index);

-- ─── 2. chat_attachments — files in MinIO referenced by messages ──────────────
CREATE TABLE IF NOT EXISTS chat_attachments (
    id              UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    conversation_id UUID         REFERENCES conversations(id) ON DELETE SET NULL,
    filename        VARCHAR(500) NOT NULL,
    content_type    VARCHAR(100),
    size_bytes      BIGINT,
    minio_key       VARCHAR(500) NOT NULL,
    kind            VARCHAR(20)  NOT NULL DEFAULT 'inline',   -- inline | rag-doc | export
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chat_attachments_user ON chat_attachments(user_id);
CREATE INDEX IF NOT EXISTS idx_chat_attachments_conv ON chat_attachments(conversation_id);

-- ─── 3. chat_exports — async export jobs (RabbitMQ -> zip -> MinIO) ───────────
CREATE TABLE IF NOT EXISTS chat_exports (
    id              UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    conversation_id UUID         REFERENCES conversations(id) ON DELETE SET NULL,  -- NULL = export all conversations
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING | PROCESSING | READY | FAILED
    attachment_id   UUID         REFERENCES chat_attachments(id) ON DELETE SET NULL,
    error_msg       TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chat_exports_user ON chat_exports(user_id);
CREATE INDEX IF NOT EXISTS idx_chat_exports_status ON chat_exports(status);

-- ─── 4. Seed missing dev users so usernames map cleanly to user_ids ──────────
INSERT INTO users (username, email, password, role)
VALUES
    ('agent', 'agent@nexusai.local', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'AGENT')
ON CONFLICT (username) DO NOTHING;
