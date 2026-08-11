-- CVFacil.NG — V2: converte audit_logs.ip e details_json para tipos compatíveis com pgjdbc 42.6+
--
-- Problema: pgjdbc 42.6+ usa type checking estrito em PreparedStatement.
--   - setString() em coluna INET  → PSQLException: expression is of type character varying
--   - setNull(Types.VARCHAR) em coluna JSONB → mesmo erro
-- Isso causava falha 500 em /api/auth/register e /api/auth/login mesmo após salvar o User.

ALTER TABLE audit_logs
    ALTER COLUMN ip          TYPE VARCHAR(64) USING ip::text;

ALTER TABLE audit_logs
    ALTER COLUMN details_json TYPE TEXT USING details_json::text;
