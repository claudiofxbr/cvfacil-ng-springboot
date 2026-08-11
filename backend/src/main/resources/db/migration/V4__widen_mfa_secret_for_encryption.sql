-- mfa_secret passa a ser cifrado em repouso (MfaSecretConverter, AES-256-GCM).
-- O texto cifrado em base64 (IV 12B + tag 16B + segredo TOTP ~32 chars) excede
-- os 64 caracteres da coluna original — amplia para 255.
ALTER TABLE users ALTER COLUMN mfa_secret TYPE VARCHAR(255);
