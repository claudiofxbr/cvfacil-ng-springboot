package ng.cvfacil.config;

import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração da chave mestra AES-256-GCM usada para cifragem column-level (SEC-09).
 *
 * <p>A chave vem de ENCRYPTION_MASTER_KEY (base64 de 32 bytes). Em produção, a chave é provisionada
 * via KMS; nunca deve aparecer no repositório.
 */
@Configuration
public class CryptoConfig {

  @Value("${cvfacil.encryption.master-key:}")
  private String masterKeyBase64;

  @Bean
  public SecretKey masterAesKey() {
    if (masterKeyBase64 == null || masterKeyBase64.isBlank()) {
      // Modo dev: permite subir o app sem chave real. NUNCA em produção.
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
