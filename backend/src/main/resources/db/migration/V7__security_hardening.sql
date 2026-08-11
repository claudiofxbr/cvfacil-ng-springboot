-- PRD §4.4 — MFA obrigatório para RootMaster e histórico/rotação de senha.
-- Ver MfaService, PasswordPolicyService e o fluxo de login em AuthController.
--
-- Nota: a allowlist de IP do RootMaster (root_ip_allowlist) já existe desde a
-- V1__init.sql (coluna `cidr`, tipo CIDR) — nunca tinha sido consumida por
-- código nenhum até agora; ver RootIpAllowlistService, que lê essa tabela
-- via query nativa (containment `cidr >>= :ip::inet`) em vez de mapeá-la como
-- @Entity, para não brigar com o tipo CIDR do Postgres sob ddl-auto=validate.

ALTER TABLE users ADD COLUMN mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN password_changed_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE TABLE password_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    password_hash VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_password_history_user ON password_history(user_id, created_at DESC);
