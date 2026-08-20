-- Colunas que espelham enums Java (@Enumerated(EnumType.STRING)) aceitavam qualquer string, sem
-- validacao do proprio banco. Um UPDATE manual malformado (ex.: um dos scripts em
-- legacy-sql-do-not-run/) gravando um valor fora do enum so quebraria na proxima leitura via
-- Hibernate (erro ao desserializar), em vez de ser rejeitado na escrita.
ALTER TABLE users
  ADD CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN', 'ROOT_MASTER'));

ALTER TABLE credit_transactions
  ADD CONSTRAINT chk_credit_transactions_type
  CHECK (type IN ('COURTESY', 'PURCHASE', 'ADMIN_GRANT', 'CONSUMPTION'));

ALTER TABLE credit_orders
  ADD CONSTRAINT chk_credit_orders_status CHECK (status IN ('PENDING', 'PAID', 'FAILED'));

ALTER TABLE credit_orders
  ADD CONSTRAINT chk_credit_orders_package CHECK (package IN ('PACK_3', 'PACK_6', 'PACK_9'));
