package ng.cvfacil.service;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Testes unitários para AesGcmCipherService.
 *
 * <p>Usa uma chave AES-256 de 32 bytes fixos (apenas para teste).
 */
class AesGcmCipherServiceTest {

  private AesGcmCipherService cipher;

  @BeforeEach
  void setup() {
    byte[] keyBytes = new byte[32]; // zeros — só para teste
    SecretKey key = new SecretKeySpec(keyBytes, "AES");
    cipher = new AesGcmCipherService(key);
  }

  @Test
  void encrypt_thenDecrypt_roundtrip() {
    byte[] plaintext = "Olá, mundo! Currículo JSON aqui.".getBytes(StandardCharsets.UTF_8);
    byte[] encrypted = cipher.encrypt(plaintext);
    byte[] decrypted = cipher.decrypt(encrypted);
    assertThat(decrypted).isEqualTo(plaintext);
  }

  @Test
  void encrypt_producesDifferentCiphertextEachCall() {
    // IV aleatório → cada chamada deve produzir ciphertext diferente
    byte[] plaintext = "mesmo conteúdo".getBytes(StandardCharsets.UTF_8);
    byte[] c1 = cipher.encrypt(plaintext);
    byte[] c2 = cipher.encrypt(plaintext);
    assertThat(c1).isNotEqualTo(c2);
  }

  @Test
  void encrypt_outputIsLargerThanInput() {
    // Output = 12 bytes IV + N bytes ciphertext+tag (tag = 16 bytes)
    byte[] plaintext = "abc".getBytes(StandardCharsets.UTF_8);
    byte[] encrypted = cipher.encrypt(plaintext);
    assertThat(encrypted.length).isGreaterThan(plaintext.length + 12);
  }

  @Test
  void decrypt_withTamperedData_throwsIllegalState() {
    byte[] plaintext = "dado sensível".getBytes(StandardCharsets.UTF_8);
    byte[] encrypted = cipher.encrypt(plaintext);
    // Altera um byte do ciphertext (após o IV de 12 bytes)
    encrypted[15] ^= 0xFF;
    assertThatThrownBy(() -> cipher.decrypt(encrypted))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("decifrar");
  }

  @Test
  void decrypt_emptyInput_throwsIllegalState() {
    assertThatThrownBy(() -> cipher.decrypt(new byte[0])).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void encrypt_emptyPlaintext_works() {
    byte[] empty = new byte[0];
    byte[] encrypted = cipher.encrypt(empty);
    byte[] decrypted = cipher.decrypt(encrypted);
    assertThat(decrypted).isEqualTo(empty);
  }

  @Test
  void encrypt_largePayload_roundtrip() {
    byte[] big = new byte[64 * 1024]; // 64 KB
    Arrays.fill(big, (byte) 'A');
    byte[] encrypted = cipher.encrypt(big);
    byte[] decrypted = cipher.decrypt(encrypted);
    assertThat(decrypted).isEqualTo(big);
  }
}
