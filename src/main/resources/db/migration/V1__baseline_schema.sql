-- =====================================================================
-- EnterpriseAI Hub - baseline relational + vector schema
-- Requires the pgvector extension (image: pgvector/pgvector:pg16)
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ------------------------------ identity ------------------------------
CREATE TABLE roles (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL,
    description VARCHAR(255),
    CONSTRAINT uk_roles_name UNIQUE (name)
);

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(180) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(150) NOT NULL,
    organization  VARCHAR(150),
    job_title     VARCHAR(150),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_login_at TIMESTAMPTZ,
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
);
CREATE INDEX idx_user_roles_role ON user_roles (role_id);

CREATE TABLE refresh_tokens (
    id         BIGSERIAL PRIMARY KEY,
    token_hash VARCHAR(64)  NOT NULL,
    user_id    BIGINT       NOT NULL,
    issued_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ  NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    user_agent VARCHAR(255),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens (expires_at);

-- ---------------------------- knowledge base ---------------------------
CREATE TABLE documents (
    id               BIGSERIAL PRIMARY KEY,
    title            VARCHAR(255) NOT NULL,
    file_name        VARCHAR(255) NOT NULL,
    content_type     VARCHAR(120) NOT NULL,
    file_size        BIGINT       NOT NULL,
    storage_path     VARCHAR(500) NOT NULL,
    checksum         VARCHAR(64)  NOT NULL,
    status           VARCHAR(30)  NOT NULL,
    category         VARCHAR(40),
    summary          TEXT,
    page_count       INTEGER,
    chunk_count      INTEGER      NOT NULL DEFAULT 0,
    character_count  INTEGER      NOT NULL DEFAULT 0,
    processing_error TEXT,
    processing_ms    BIGINT,
    uploaded_by      BIGINT       NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    processed_at     TIMESTAMPTZ,
    CONSTRAINT fk_documents_user FOREIGN KEY (uploaded_by) REFERENCES users (id),
    CONSTRAINT ck_documents_status CHECK (status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED'))
);
CREATE INDEX idx_documents_status ON documents (status);
CREATE INDEX idx_documents_uploaded_by ON documents (uploaded_by);
CREATE INDEX idx_documents_created_at ON documents (created_at DESC);
CREATE INDEX idx_documents_category ON documents (category);
CREATE INDEX idx_documents_title_trgm ON documents USING gin (title gin_trgm_ops);

CREATE TABLE document_chunks (
    id             BIGSERIAL PRIMARY KEY,
    document_id    BIGINT      NOT NULL,
    chunk_index    INTEGER     NOT NULL,
    content        TEXT        NOT NULL,
    character_count INTEGER    NOT NULL,
    token_estimate INTEGER     NOT NULL,
    page_number    INTEGER,
    section        VARCHAR(255),
    embedding      vector(1536),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_chunks_document FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE,
    CONSTRAINT uk_chunks_document_index UNIQUE (document_id, chunk_index)
);
CREATE INDEX idx_chunks_document ON document_chunks (document_id);

-- Approximate nearest-neighbour index for cosine distance.
-- HNSW gives better recall/latency than IVFFlat for this data volume and
-- does not require a training step after bulk loading.
CREATE INDEX idx_chunks_embedding_hnsw
    ON document_chunks USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- ---------------------------- conversations ----------------------------
CREATE TABLE conversations (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    title         VARCHAR(200) NOT NULL,
    archived      BOOLEAN      NOT NULL DEFAULT FALSE,
    message_count INTEGER      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_conversations_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_conversations_user_updated ON conversations (user_id, updated_at DESC);

CREATE TABLE messages (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT      NOT NULL,
    role            VARCHAR(20) NOT NULL,
    content         TEXT        NOT NULL,
    model           VARCHAR(100),
    retrieval_ms    BIGINT,
    llm_ms          BIGINT,
    total_ms        BIGINT,
    retrieved_chunks INTEGER,
    top_similarity  DOUBLE PRECISION,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE,
    CONSTRAINT ck_messages_role CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM'))
);
CREATE INDEX idx_messages_conversation_created ON messages (conversation_id, created_at);

CREATE TABLE message_citations (
    id          BIGSERIAL PRIMARY KEY,
    message_id  BIGINT           NOT NULL,
    document_id BIGINT           NOT NULL,
    chunk_id    BIGINT,
    page_number INTEGER,
    section     VARCHAR(255),
    excerpt     TEXT,
    similarity  DOUBLE PRECISION NOT NULL,
    citation_rank INTEGER        NOT NULL,
    CONSTRAINT fk_citations_message FOREIGN KEY (message_id) REFERENCES messages (id) ON DELETE CASCADE,
    CONSTRAINT fk_citations_document FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE
);
CREATE INDEX idx_citations_message ON message_citations (message_id);
CREATE INDEX idx_citations_document ON message_citations (document_id);

-- ------------------------------- audit --------------------------------
CREATE TABLE audit_logs (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT,
    actor_email   VARCHAR(180),
    action        VARCHAR(80)  NOT NULL,
    resource_type VARCHAR(60),
    resource_id   VARCHAR(60),
    details       VARCHAR(1000),
    request_id    VARCHAR(64),
    ip_address    VARCHAR(64),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
);
CREATE INDEX idx_audit_created_at ON audit_logs (created_at DESC);
CREATE INDEX idx_audit_user ON audit_logs (user_id);
CREATE INDEX idx_audit_action ON audit_logs (action);
