-- credit_transactions e credit_orders são ledgers financeiros (histórico de compras via
-- PagSeguro) que precisam sobreviver à exclusão da conta (LGPD Art. 18/GDPR), pelo mesmo motivo
-- que audit_logs já sobrevive: obrigação de retenção de comprovante fiscal e trilha de auditoria
-- de pagamento. Antes desta migration, ON DELETE CASCADE apagava esse histórico junto com o
-- usuário — agora o vínculo com o titular é desfeito (user_id vira NULL), mas a linha permanece.
ALTER TABLE credit_transactions ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE credit_transactions DROP CONSTRAINT credit_transactions_user_id_fkey;
ALTER TABLE credit_transactions
  ADD CONSTRAINT credit_transactions_user_id_fkey
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE credit_orders ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE credit_orders DROP CONSTRAINT credit_orders_user_id_fkey;
ALTER TABLE credit_orders
  ADD CONSTRAINT credit_orders_user_id_fkey
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;
