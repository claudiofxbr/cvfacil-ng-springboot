-- Registra o consentimento aos Termos de Uso / Politica de Privacidade no cadastro
-- (LGPD Art. 8, GDPR Art. 7 — consentimento deve ser verificavel, com data e versao
-- do documento aceito). Nullable: contas criadas antes desta migration ou via OAuth
-- (fluxo ainda nao cobre o consentimento) nao tem esse dado, e isso deve refletir a
-- realidade, nao ser mascarado com um valor falso.
ALTER TABLE users
    ADD COLUMN terms_accepted_at TIMESTAMPTZ,
    ADD COLUMN terms_version     VARCHAR(20);
