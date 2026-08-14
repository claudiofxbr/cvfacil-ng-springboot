package ng.cvfacil.config;

import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Configuração da chave mestra AES-256-GCM usada para cifragem column-level (SEC-09).
 *
 * <p>A chave vem de ENCRYPTION_MASTER_KEY (base64 de 32 bytes). Em produção, a chave é provisionada
 * via KMS; nunca deve aparecer no repositório.
 */
@Configuration
public class CryptoConfig {

  private static final Logger log = LoggerFactory.getLogger(CryptoConfig.class);

  @Value("${cvfacil.encryption.master-key:}")
  private String masterKeyBase64;

  @Bean
  public SecretKey masterAesKey(Environment env) {
    if (masterKeyBase64 == null || masterKeyBase64.isBlank()) {
      // Chave zerada só é aceitável no profile "local" (dev). Fora dele, subir sem
      // ENCRYPTION_MASTER_KEY cifraria dados sensíveis (ex: mfa_secret) com uma chave
      // pública/trivial sem qualquer sinal de erro — falha o startup em vez disso.
      if (!env.acceptsProfiles(org.springframework.core.env.Profiles.of("local"))) {
        throw new IllegalStateException(
            "[STARTUP ABORTADO] ENCRYPTION_MASTER_KEY nao configurada fora do profile 'local'. "
                + "Dados cifrados em coluna (mfa_secret, etc.) nao podem usar chave zerada em "
                + "producao.");
      }
      log.warn(
          "[CryptoConfig] ENCRYPTION_MASTER_KEY nao configurada — usando chave AES zerada. "
              + "Aceitavel apenas no profile 'local'.");
      return new SecretKeySpec(new byte[32], "AES");
    }
    String stripped = masterKeyBase64.replaceFirst("^base64:", "");
    byte[] raw = Base64.getDecoder().decode(stripped);
    if (raw.length != 32) {
      throw new IllegalStateException("ENCRYPTION_MASTER_KEY deve ter 32 bytes (AES-256).");
    }
    return new SecretKeySpec(raw, "AES");
  }
}
