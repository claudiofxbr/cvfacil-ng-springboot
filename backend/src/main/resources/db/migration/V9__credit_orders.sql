-- Pedidos de compra de créditos junto ao gateway PagSeguro/PagBank.
-- Ledger separado de credit_transactions: aqui rastreamos o ciclo de vida do
-- pedido (PENDING -> PAID/FAILED) ANTES de creditar; credit_transactions só
-- recebe uma linha quando o pagamento é de fato confirmado (grantPurchase).
CREATE TABLE credit_orders (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    package             VARCHAR(20) NOT NULL, -- PACK_3 | PACK_6 | PACK_9
    pagseguro_order_id  VARCHAR(64) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING | PAID | FAILED
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_credit_orders_pagseguro_order_id UNIQUE (pagseguro_order_id)
);

CREATE INDEX idx_credit_orders_user ON credit_orders(user_id, created_at DESC);
