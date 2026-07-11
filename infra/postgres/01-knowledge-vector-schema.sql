-- SmartCS Knowledge pgvector index schema.
-- Run this script in the smartcs_knowledge PostgreSQL database.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS knowledge_faq_embedding (
    faq_id               VARCHAR(64)  PRIMARY KEY,
    question             TEXT         NOT NULL,
    answer               TEXT         NOT NULL,
    category             VARCHAR(64)  NOT NULL DEFAULT 'general',
    status               VARCHAR(16)  NOT NULL,
    content_hash         CHAR(64)     NOT NULL,
    source_updated_at    TIMESTAMPTZ  NOT NULL,
    embedding_model      VARCHAR(128) NOT NULL,
    embedding_dimensions INTEGER      NOT NULL DEFAULT 1024,
    embedding            vector(1024) NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_knowledge_faq_embedding_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'DISABLED')),
    CONSTRAINT chk_knowledge_faq_embedding_dimensions
        CHECK (embedding_dimensions = 1024)
);

CREATE INDEX IF NOT EXISTS idx_knowledge_faq_embedding_status_category
    ON knowledge_faq_embedding (status, category);

CREATE INDEX IF NOT EXISTS idx_knowledge_faq_embedding_content_hash
    ON knowledge_faq_embedding (content_hash);

CREATE INDEX IF NOT EXISTS idx_knowledge_faq_embedding_hnsw_cosine
    ON knowledge_faq_embedding
    USING hnsw (embedding vector_cosine_ops)
    WHERE status = 'ACTIVE';
