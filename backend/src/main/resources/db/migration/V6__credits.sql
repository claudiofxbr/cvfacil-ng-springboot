-- Sistema de créditos de criação de currículo: 1 crédito = 1 currículo criado.
-- credits em users é o saldo (leitura rápida); credit_transactions é o
-- ledger append-only (mesma ideia de audit_logs: nunca editar/apagar linhas).
ALTER TABLE users ADD COLUMN credits INT NOT NULL DEFAULT 0;

CREATE TABLE credit_transactions (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type          VARCHAR(20) NOT NULL, -- COURTESY | PURCHASE | ADMIN_GRANT | CONSUMPTION
    amount        INT NOT NULL,         -- positivo (crédito) ou negativo (consumo)
    balance_after INT NOT NULL,
    reference     VARCHAR(255),         -- ex.: packageId da compra, id do admin que concedeu
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_credit_transactions_user ON credit_transactions(user_id, created_at DESC);
