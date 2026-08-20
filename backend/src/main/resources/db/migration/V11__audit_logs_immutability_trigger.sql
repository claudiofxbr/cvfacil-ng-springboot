-- audit_logs é uma cadeia de hash (prev_hash/self_hash) append-only por design (ver AuditService),
-- mas até aqui essa garantia dependia inteiramente de nenhum código Java jamais rodar UPDATE/DELETE
-- nela — um script manual (ex.: os já quarentenados em legacy-sql-do-not-run/) poderia corromper a
-- cadeia sem deixar rastro. Este trigger torna a imutabilidade uma garantia do próprio banco.
CREATE OR REPLACE FUNCTION audit_logs_block_mutation() RETURNS TRIGGER AS $$
BEGIN
  RAISE EXCEPTION 'audit_logs é append-only: % não é permitido', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_logs_no_update
  BEFORE UPDATE ON audit_logs
  FOR EACH ROW EXECUTE FUNCTION audit_logs_block_mutation();

CREATE TRIGGER trg_audit_logs_no_delete
  BEFORE DELETE ON audit_logs
  FOR EACH ROW EXECUTE FUNCTION audit_logs_block_mutation();
