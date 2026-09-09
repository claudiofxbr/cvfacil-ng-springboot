-- PIN de 8 digitos exigido em TODO login/cadastro via Google (mesmo com sessao
-- ja ativa no navegador) — sem isso, qualquer pessoa com acesso fisico ao
-- navegador do dono da conta conseguia entrar direto, sem provar nada que so
-- o dono soubesse (ver OAuth2LoginSuccessHandler).
ALTER TABLE users
    ADD COLUMN pin_hash VARCHAR(100),
    ADD COLUMN pin_failed_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN pin_locked_until TIMESTAMPTZ;

-- Fluxo "esqueci meu PIN" — mesmo desenho de password_reset_tokens (V5), tabela
-- separada porque conta 100% Google nao tem password_hash pra reaproveitar o
-- fluxo existente.
CREATE TABLE pin_reset_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_pin_reset_tokens_user ON pin_reset_tokens(user_id);
