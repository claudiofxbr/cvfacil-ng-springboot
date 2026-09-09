package ng.cvfacil.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import ng.cvfacil.domain.PinResetToken;
import ng.cvfacil.domain.User;
import ng.cvfacil.repository.PinResetTokenRepository;
import ng.cvfacil.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Testes unitários para PinService — hash/verificação/bloqueio do PIN de 8 dígitos e o fluxo
 * "esqueci meu PIN". Usa BCryptPasswordEncoder real (não mock) porque o comportamento sob teste é
 * justamente "o hash bate/não bate", não uma interação a verificar.
 */
class PinServiceTest {

  private UserRepository users;
  private PinResetTokenRepository resetTokens;
  private PasswordEncoder encoder;
  private JavaMailSender mailSender;
  private AuditService audit;
  private PinService service;

  @BeforeEach
  void setup() {
    users = mock(UserRepository.class);
    resetTokens = mock(PinResetTokenRepository.class);
    encoder = new BCryptPasswordEncoder(4); // custo baixo só para acelerar os testes
    mailSender = mock(JavaMailSender.class);
    audit = mock(AuditService.class);

    service = new PinService(users, resetTokens, encoder, mailSender, audit);
    ReflectionTestUtils.setField(service, "frontendBaseUrl", "https://cvfacil.ng");

    when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    when(resetTokens.save(any(PinResetToken.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  private User userSemPin() {
    User u = new User();
    ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
    u.setEmail("teste@gmail.com");
    return u;
  }

  @Test
  void isValidPin_aceitaExatamente8Digitos() {
    assertThat(service.isValidPin("12345678")).isTrue();
    assertThat(service.isValidPin("1234567")).isFalse(); // 7 dígitos
    assertThat(service.isValidPin("123456789")).isFalse(); // 9 dígitos
    assertThat(service.isValidPin("1234abcd")).isFalse(); // não numérico
    assertThat(service.isValidPin(null)).isFalse();
  }

  @Test
  void setupPin_gravaHashBcryptEZeraContadores() {
    User u = userSemPin();
    u.setPinFailedAttempts(3);

    service.setupPin(u, "12345678", "203.0.113.10", "JUnit");

    assertThat(u.getPinHash()).isNotNull().isNotEqualTo("12345678");
    assertThat(encoder.matches("12345678", u.getPinHash())).isTrue();
    assertThat(u.getPinFailedAttempts()).isZero();
    assertThat(u.getPinLockedUntil()).isNull();
    verify(audit).record(eq(u.getId()), eq("PIN_SETUP"), any(), any(), isNull());
  }

  @Test
  void verifyPin_pinCorreto_zeraContadorERetornaTrue() {
    User u = userSemPin();
    service.setupPin(u, "12345678", null, null);
    u.setPinFailedAttempts(2);

    boolean ok = service.verifyPin(u, "12345678", "203.0.113.10", "JUnit");

    assertThat(ok).isTrue();
    assertThat(u.getPinFailedAttempts()).isZero();
    verify(audit).record(eq(u.getId()), eq("PIN_VERIFY_SUCCESS"), any(), any(), isNull());
  }

  @Test
  void verifyPin_pinErrado_incrementaContadorERetornaFalse() {
    User u = userSemPin();
    service.setupPin(u, "12345678", null, null);

    boolean ok = service.verifyPin(u, "00000000", "203.0.113.10", "JUnit");

    assertThat(ok).isFalse();
    assertThat(u.getPinFailedAttempts()).isEqualTo(1);
    assertThat(u.getPinLockedUntil()).isNull();
    verify(audit).record(eq(u.getId()), eq("PIN_VERIFY_FAILURE"), any(), any(), isNull());
  }

  @Test
  void verifyPin_quintaTentativaErrada_bloqueiaConta() {
    User u = userSemPin();
    service.setupPin(u, "12345678", null, null);

    for (int i = 0; i < 4; i++) {
      service.verifyPin(u, "00000000", null, null);
    }
    assertThat(u.getPinLockedUntil()).isNull(); // ainda não travou nas 4 primeiras

    service.verifyPin(u, "00000000", null, null); // 5ª tentativa errada

    assertThat(u.getPinFailedAttempts()).isEqualTo(5);
    assertThat(u.getPinLockedUntil()).isAfter(Instant.now());
  }

  @Test
  void isLocked_refletExatamenteOCampoPinLockedUntil() {
    User u = userSemPin();
    assertThat(service.isLocked(u)).isFalse();

    u.setPinLockedUntil(Instant.now().plusSeconds(60));
    assertThat(service.isLocked(u)).isTrue();

    u.setPinLockedUntil(Instant.now().minusSeconds(60));
    assertThat(service.isLocked(u)).isFalse();
  }

  @Test
  void changePin_pinAtualCorreto_troca() {
    User u = userSemPin();
    service.setupPin(u, "12345678", null, null);

    boolean ok = service.changePin(u, "12345678", "87654321", "203.0.113.10", "JUnit");

    assertThat(ok).isTrue();
    assertThat(encoder.matches("87654321", u.getPinHash())).isTrue();
    verify(audit).record(eq(u.getId()), eq("PIN_CHANGED"), any(), any(), isNull());
  }

  @Test
  void changePin_pinAtualErrado_naoTroca() {
    User u = userSemPin();
    service.setupPin(u, "12345678", null, null);
    String hashAntes = u.getPinHash();

    boolean ok = service.changePin(u, "00000000", "87654321", "203.0.113.10", "JUnit");

    assertThat(ok).isFalse();
    assertThat(u.getPinHash()).isEqualTo(hashAntes);
  }

  @Test
  void changePin_semPinCadastrado_naoTroca() {
    User u = userSemPin(); // sem setupPin — pinHash null

    boolean ok = service.changePin(u, "12345678", "87654321", null, null);

    assertThat(ok).isFalse();
  }

  @Test
  void requestReset_semPinCadastrado_naoEnviaEmailNemCriaToken() {
    User u = userSemPin(); // pinHash null

    service.requestReset(u);

    verifyNoInteractions(mailSender);
    verify(resetTokens, never()).save(any());
  }

  @Test
  void requestReset_comPinCadastrado_criaTokenEEnviaEmail() {
    User u = userSemPin();
    service.setupPin(u, "12345678", null, null);

    service.requestReset(u);

    verify(resetTokens).save(any(PinResetToken.class));
    verify(mailSender).send(any(org.springframework.mail.SimpleMailMessage.class));
  }

  @Test
  void resetPin_tokenValido_limpaPinDaConta() {
    User u = userSemPin();
    service.setupPin(u, "12345678", null, null);
    when(users.findById(u.getId())).thenReturn(Optional.of(u));

    // Captura o token cru enviado por e-mail para poder "clicar no link" no teste.
    ArgumentCaptor<org.springframework.mail.SimpleMailMessage> msgCaptor =
        ArgumentCaptor.forClass(org.springframework.mail.SimpleMailMessage.class);
    ArgumentCaptor<PinResetToken> tokenCaptor = ArgumentCaptor.forClass(PinResetToken.class);

    service.requestReset(u);
    verify(resetTokens).save(tokenCaptor.capture());
    verify(mailSender).send(msgCaptor.capture());
    String rawToken = extractTokenFromEmail(msgCaptor.getValue().getText());

    String tokenHash = tokenCaptor.getValue().getTokenHash();
    when(resetTokens.findByTokenHash(tokenHash)).thenReturn(Optional.of(tokenCaptor.getValue()));

    boolean ok = service.resetPin(rawToken);

    assertThat(ok).isTrue();
    assertThat(u.getPinHash()).isNull();
    assertThat(u.getPinFailedAttempts()).isZero();
    assertThat(u.getPinLockedUntil()).isNull();
    verify(audit).record(eq(u.getId()), eq("PIN_RESET"), isNull(), isNull(), isNull());
  }

  @Test
  void resetPin_tokenInexistente_retornaFalse() {
    when(resetTokens.findByTokenHash(any())).thenReturn(Optional.empty());
    assertThat(service.resetPin("token-qualquer")).isFalse();
  }

  @Test
  void resetPin_tokenJaUsado_retornaFalse() {
    PinResetToken token = new PinResetToken();
    token.setUserId(UUID.randomUUID());
    token.setExpiresAt(Instant.now().plusSeconds(600));
    token.setUsedAt(Instant.now().minusSeconds(60)); // já usado
    // findByTokenHash é mockado para qualquer hash — o teste não precisa replicar o SHA-256
    // interno do service, só verificar que um token com usedAt preenchido é sempre rejeitado.
    when(resetTokens.findByTokenHash(any())).thenReturn(Optional.of(token));

    assertThat(service.resetPin("token-qualquer")).isFalse();
  }

  @Test
  void resetPin_tokenExpirado_retornaFalse() {
    PinResetToken token = new PinResetToken();
    token.setUserId(UUID.randomUUID());
    token.setExpiresAt(Instant.now().minusSeconds(60)); // expirado
    when(resetTokens.findByTokenHash(any())).thenReturn(Optional.of(token));

    assertThat(service.resetPin("token-qualquer")).isFalse();
  }

  private String extractTokenFromEmail(String body) {
    int idx = body.indexOf("token=");
    String tail = body.substring(idx + "token=".length());
    return tail.split("\\s", 2)[0].trim();
  }
}
