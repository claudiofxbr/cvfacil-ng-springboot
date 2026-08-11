package ng.cvfacil.service;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.springframework.stereotype.Service;

/**
 * AES-256-GCM com IV aleatório por operação (12 bytes). Layout do ciphertext: [IV(12) ||
 * TAG+CIPHERTEXT]
 */
@Service
public class AesGcmCipherService {

  private static final int IV_LENGTH = 12;
  private static final int TAG_BITS = 128;
  private final SecretKey key;
  private final SecureRandom random = new SecureRandom();

  public AesGcmCipherService(SecretKey masterAesKey) {
    this.key = masterAesKey;
  }

  public byte[] encrypt(byte[] plaintext) {
    try {
      byte[] iv = new byte[IV_LENGTH];
      random.nextBytes(iv);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      byte[] ct = c.doFinal(plaintext);
      return ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array();
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao cifrar", e);
    }
  }

  public byte[] decrypt(byte[] packed) {
    try {
      ByteBuffer bb = ByteBuffer.wrap(packed);
      byte[] iv = new byte[IV_LENGTH];
      bb.get(iv);
      byte[] ct = new byte[bb.remaining()];
      bb.get(ct);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      return c.doFinal(ct);
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao decifrar", e);
    }
  }
}
