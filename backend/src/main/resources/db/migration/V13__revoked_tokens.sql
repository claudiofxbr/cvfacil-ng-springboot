-- Refresh tokens sao JWTs stateless: antes desta tabela, um logout so apagava o cookie no
-- navegador do usuario, mas o token continuava criptograficamente valido por ate 7 dias (refresh-ttl-days)
-- em qualquer outro lugar onde tivesse sido copiado. Esta tabela registra o jti (JWT ID) de todo
-- refresh token consumido (por rotacao em /api/auth/refresh) ou explicitamente revogado (logout),
-- permitindo que o backend rejeite reuso mesmo com assinatura valida.
CREATE TABLE revoked_tokens (
  jti UUID PRIMARY KEY,
  expires_at TIMESTAMPTZ NOT NULL
);

-- Suporta o cleanup periodico de entradas ja expiradas (TokenRevocationService).
CREATE INDEX idx_revoked_tokens_expires_at ON revoked_tokens (expires_at);
