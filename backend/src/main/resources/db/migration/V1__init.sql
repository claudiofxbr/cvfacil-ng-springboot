-- CVFacil.NG — Migration inicial (V1)
-- Requer PostgreSQL 16+ e a extensão pgcrypto habilitada.

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS citext;

-- Usuários
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           CITEXT      NOT NULL UNIQUE,
    password_hash   VARCHAR(100),                           -- BCrypt ($2b$12$...)
    display_name    VARCHAR(120),
    locale          VARCHAR(10) NOT NULL DEFAULT 'pt-BR',
    role            VARCHAR(32) NOT NULL DEFAULT 'USER',    -- USER | ROOT_MASTER
    email_verified  BOOLEAN     NOT NULL DEFAULT FALSE,
    mfa_secret      VARCHAR(64),                             -- TOTP (apenas ROOT_MASTER em V1)
    failed_logins   INT         NOT NULL DEFAULT 0,
    locked_until    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_users_role ON users(role);

-- Contas OAuth federadas
CREATE TABLE oauth_accounts (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider          VARCHAR(32) NOT NULL,               -- GOOGLE
    provider_user_id  VARCHAR(128) NOT NULL,
    refresh_token_enc BYTEA,                              -- AES-256-GCM cipher
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_user_id)
);

-- Currículos
CREATE TABLE resumes (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    layout_id     VARCHAR(32) NOT NULL,
    locale        VARCHAR(10) NOT NULL DEFAULT 'pt-BR',
    version       INT         NOT NULL DEFAULT 1,
    content_enc   BYTEA       NOT NULL,                    -- pgcrypto AES-256-GCM
    photo_url     VARCHAR(500),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_resumes_user_id ON resumes(user_id);

-- Fotos 3x4
CREATE TABLE resume_photos (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    resume_id   UUID REFERENCES resumes(id) ON DELETE SET NULL,
    file_key    VARCHAR(500) NOT NULL,
    frame_style VARCHAR(32)  NOT NULL DEFAULT 'thin',
    checksum    VARCHAR(128) NOT NULL,
    bytes       INT          NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_resume_photos_user_id ON resume_photos(user_id);

-- Jobs de importação por IA
CREATE TABLE ai_import_jobs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status              VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    input_hash          VARCHAR(128) NOT NULL,
    token_count         INT,
    provider_latency_ms INT,
    error_code          VARCHAR(64),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at        TIMESTAMPTZ
);
CREATE INDEX idx_ai_jobs_user ON ai_import_jobs(user_id, created_at DESC);

-- Auditoria imutável append-only
CREATE TABLE audit_logs (
    id           BIGSERIAL PRIMARY KEY,
    user_id      UUID,
    action       VARCHAR(64) NOT NULL,
    ip           INET,
    user_agent   VARCHAR(500),
    details_json JSONB,
    prev_hash    VARCHAR(128),
    self_hash    VARCHAR(128) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_user_time ON audit_logs(user_id, created_at DESC);

-- IP allowlist para RootMaster
CREATE TABLE root_ip_allowlist (
    id         BIGSERIAL PRIMARY KEY,
    cidr       CIDR        NOT NULL,
    note       VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Seed: 1 RootMaster inicial. A senha deve ser trocada imediatamente após o primeiro login.
-- Hash BCrypt cost 12 de 'ChangeThisRoot!2026' (APENAS PARA DEV; em produção, use seed separado).
INSERT INTO users (email, password_hash, display_name, role, email_verified)
VALUES (
  'root@cvfacil.ng',
  '$2b$12$ZH2Pkd.0syu7RO/YbUOFau/eI.QDniBJp9A8VtdVsynA1TII1hv/u',
  'Root Master',
  'ROOT_MASTER',
  TRUE
);
-- Senha local de desenvolvimento: Admin@2026!  (trocar em produção)
