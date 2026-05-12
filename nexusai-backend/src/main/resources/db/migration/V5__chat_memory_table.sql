-- V5: Spring AI JDBC Chat Memory table
-- Required by spring-ai-starter-model-chat-memory-repository-jdbc (Spring AI 1.0.0)
-- Each row is one message in a conversation / session.

CREATE TABLE IF NOT EXISTS spring_ai_chat_memory (
    conversation_id VARCHAR(256) NOT NULL,
    content         TEXT         NOT NULL,
    type            VARCHAR(20)  NOT NULL,
    "timestamp"     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_spring_ai_chat_memory_conversation_id
    ON spring_ai_chat_memory (conversation_id);

