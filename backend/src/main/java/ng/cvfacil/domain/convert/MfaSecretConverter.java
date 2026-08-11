package ng.cvfacil.domain.convert;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import ng.cvfacil.service.AesGcmCipherService;
import org.springframework.stereotype.Component;

/**
 * Cifra mfa_secret em repouso com o mesmo AesGcmCipherService usado para Resume.contentEnc
 * (AES-256-GCM). O campo era armazenado em texto plano — a feature de MFA ainda não tem nenhum
 * fluxo que leia/grave este campo (ROOT_MASTER MFA é scaffolding), mas corrigimos o armazenamento
 * agora para que a implementação futura já nasça segura por padrão.
 */
@Component
@Converter
public class MfaSecretConverter implements AttributeConverter<String, String> {

  private final AesGcmCipherService cipher;

  public MfaSecretConverter(AesGcmCipherService cipher) {
    this.cipher = cipher;
  }

  @Override
  public String convertToDatabaseColumn(String attribute) {
    if (attribute == null || attribute.isBlank()) return null;
    byte[] enc = cipher.encrypt(attribute.getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(enc);
  }

  @Override
  public String convertToEntityAttribute(String dbData) {
    if (dbData == null || dbData.isBlank()) return null;
    byte[] dec = cipher.decrypt(Base64.getDecoder().decode(dbData));
    return new String(dec, StandardCharsets.UTF_8);
  }
}
