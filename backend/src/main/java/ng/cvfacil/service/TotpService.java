package ng.cvfacil.service;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * TOTP (RFC 6238) sobre HMAC-SHA1, implementado sem dependência externa — o algoritmo é pequeno e
 * bem definido, e adicionar uma lib só para isso não se justificaria (ver CLAUDE.md).
 *
 * <p>Passo de 30s, 6 dígitos, janela de tolerância de ±1 passo (±30s) para absorver deriva de
 * relógio do usuário — mesmo parâmetro usado pela maioria dos apps autenticadores (Google
 * Authenticator, Authy).
 */
@Service
public class TotpService {

  private static final int TIME_STEP_SECONDS = 30;
  private static final int CODE_DIGITS = 6;
  private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

  /** Gera um segredo aleatório de 160 bits (20 bytes), codificado em Base32 sem padding. */
  public String generateSecret() {
    byte[] bytes = new byte[20];
    new SecureRandom().nextBytes(bytes);
    return base32Encode(bytes);
  }

  /**
   * URI padrão otpauth:// — o usuário cola no app autenticador (ou digita o secret manualmente).
   */
  public String buildOtpAuthUri(String secret, String accountEmail) {
    String issuer = "CVFacil.NG";
    return String.format(
        "otpauth://totp/%s:%s?secret=%s&issuer=%s&digits=%d&period=%d",
        issuer, accountEmail, secret, issuer, CODE_DIGITS, TIME_STEP_SECONDS);
  }

  /** Verifica um código de 6 dígitos contra o passo atual e os dois vizinhos (±30s). */
  public boolean verifyCode(String secret, String code) {
    if (secret == null || code == null || !code.matches("\\d{6}")) return false;
    long currentStep = System.currentTimeMillis() / 1000 / TIME_STEP_SECONDS;
    for (long step = currentStep - 1; step <= currentStep + 1; step++) {
      if (code.equals(generateCode(secret, step))) return true;
    }
    return false;
  }

  private String generateCode(String secret, long step) {
    try {
      byte[] key = base32Decode(secret);
      byte[] msg = ByteBuffer.allocate(8).putLong(step).array();
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(key, "HmacSHA1"));
      byte[] hash = mac.doFinal(msg);

      int offset = hash[hash.length - 1] & 0x0F;
      int binary =
          ((hash[offset] & 0x7f) << 24)
              | ((hash[offset + 1] & 0xff) << 16)
              | ((hash[offset + 2] & 0xff) << 8)
              | (hash[offset + 3] & 0xff);

      int otp = binary % (int) Math.pow(10, CODE_DIGITS);
      return String.format(Locale.ROOT, "%0" + CODE_DIGITS + "d", otp);
    } catch (Exception e) {
      throw new IllegalStateException("[TotpService] Falha ao gerar código TOTP", e);
    }
  }

  private static String base32Encode(byte[] data) {
    StringBuilder sb = new StringBuilder();
    int bits = 0, value = 0;
    for (byte b : data) {
      value = (value << 8) | (b & 0xFF);
      bits += 8;
      while (bits >= 5) {
        sb.append(BASE32_ALPHABET.charAt((value >>> (bits - 5)) & 0x1F));
        bits -= 5;
      }
    }
    if (bits > 0) {
      sb.append(BASE32_ALPHABET.charAt((value << (5 - bits)) & 0x1F));
    }
    return sb.toString();
  }

  private static byte[] base32Decode(String encoded) {
    String clean = encoded.trim().toUpperCase(Locale.ROOT).replace("=", "");
    int bits = 0, value = 0, index = 0;
    byte[] output = new byte[clean.length() * 5 / 8];
    for (int i = 0; i < clean.length(); i++) {
      value = (value << 5) | BASE32_ALPHABET.indexOf(clean.charAt(i));
      bits += 5;
      if (bits >= 8) {
        output[index++] = (byte) ((value >>> (bits - 8)) & 0xFF);
        bits -= 8;
      }
    }
    return output;
  }
}
