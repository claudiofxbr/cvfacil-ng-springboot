package ng.cvfacil.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import ng.cvfacil.domain.PinResetToken;
import ng.cvfacil.domain.User;
import ng.cvfacil.repository.PinResetTokenRepository;
import ng.cvfacil.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PIN de 8 dígitos — segundo fator obrigatório em todo login/cadastro via Google, mesmo com sessão
 * já ativa no navegador (ver OAuth2LoginSuccessHandler). Sem isso, qualquer pessoa com acesso
 * físico ao navegador do dono da conta entrava direto, sem provar nada que só o dono soubesse.
 *
 * <p>Bloqueio após tentativas erradas usa os mesmos campos {@code pin_failed_attempts}/{@code
 * pin_locked_until} do próprio User (não Redis) — precisa sobreviver independente de rate limit por
 * IP, já que o ataque relevante aqui é "mesmo navegador, dono ausente", não IPs diferentes.
 */
@Service
public class PinService {

  private static final Logger log = LoggerFactory.getLogger(PinService.class);
  private static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(30);
  private static final int MAX_ATTEMPTS = 5;
  private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

  private final UserRepository users;
  private final PinResetTokenRepository resetTokens;
  private final PasswordEncoder encoder;
  private final JavaMailSender mailSender;
  private final AuditService audit;

  @Value("${cvfacil.frontend.base-url:http://localhost:3000}")
  private String frontendBaseUrl;

  @Value("${spring.mail.username:}")
  private String mailFrom;

  public PinService(
      UserRepository users,
      PinResetTokenRepository resetTokens,
      PasswordEncoder encoder,
      JavaMailSender mailSender,
      AuditService audit) {
    this.users = users;
    this.resetTokens = resetTokens;
    this.encoder = encoder;
    this.mailSender = mailSender;
    this.audit = audit;
  }

  public boolean isValidPin(String pin) {
    return pin != null && pin.matches("\\d{8}");
  }

  /** {@code true} se a conta está temporariamente bloqueada por tentativas erradas de PIN. */
  public boolean isLocked(User u) {
    return u.getPinLockedUntil() != null && u.getPinLockedUntil().isAfter(Instant.now());
  }

  /** Cria o PIN da conta — só deve ser chamado quando {@code user.getPinHash() == null}. */
  @Transactional
  public void setupPin(User u, String pin, String ip, String userAgent) {
    u.setPinHash(encoder.encode(pin));
    u.setPinFailedAttempts(0);
    u.setPinLockedUntil(null);
    users.save(u);
    audit.record(u.getId(), "PIN_SETUP", ip, userAgent, null);
  }

  /**
   * Verifica o PIN informado contra o hash da conta, aplicando bloqueio progressivo. Retorna {@code
   * true} em caso de acerto (e já zera o contador de tentativas).
   */
  @Transactional
  public boolean verifyPin(User u, String pin, String ip, String userAgent) {
    if (!encoder.matches(pin, u.getPinHash())) {
      u.setPinFailedAttempts(u.getPinFailedAttempts() + 1);
      if (u.getPinFailedAttempts() >= MAX_ATTEMPTS) {
        u.setPinLockedUntil(Instant.now().plus(LOCK_DURATION));
      }
      users.save(u);
      audit.record(u.getId(), "PIN_VERIFY_FAILURE", ip, userAgent, null);
      return false;
    }
    u.setPinFailedAttempts(0);
    u.setPinLockedUntil(null);
    users.save(u);
    audit.record(u.getId(), "PIN_VERIFY_SUCCESS", ip, userAgent, null);
    return true;
  }

  /**
   * Troca o PIN de uma conta já autenticada (fluxo self-service na aba Segurança) — exige o PIN
   * atual, mesmo padrão de {@code AuthController.changePassword}.
   */
  @Transactional
  public boolean changePin(User u, String currentPin, String newPin, String ip, String userAgent) {
    if (u.getPinHash() == null || !encoder.matches(currentPin, u.getPinHash())) {
      return false;
    }
    u.setPinHash(encoder.encode(newPin));
    users.save(u);
    audit.record(u.getId(), "PIN_CHANGED", ip, userAgent, null);
    return true;
  }

  /**
   * Envia o e-mail de "esqueci meu PIN". Sempre silencioso do ponto de vista do chamador — quem usa
   * isto (PinController) sempre responde 200 independente do resultado, para não revelar se a conta
   * existe.
   */
  @Transactional
  public void requestReset(User u) {
    if (u == null || u.getPinHash() == null) {
      // Sem PIN cadastrado ainda — não há nada para "esquecer"; o próximo login com Google já
      // cai direto na tela de criação.
      return;
    }
    String rawToken = generateToken();
    PinResetToken entity = new PinResetToken();
    entity.setUserId(u.getId());
    entity.setTokenHash(sha256Hex(rawToken));
    entity.setExpiresAt(Instant.now().plus(RESET_TOKEN_TTL));
    resetTokens.save(entity);
    sendResetEmail(u.getEmail(), rawToken);
  }

  /**
   * Confirma a redefinição: limpa o PIN da conta (não define um novo aqui) — o próximo login com
   * Google cai automaticamente na tela de "criar PIN", reaproveitando o fluxo de setup normal em
   * vez de duplicar a UI de "escolher novo PIN" num link de e-mail.
   *
   * @return true se o token era válido; false se inválido/expirado/já usado.
   */
  @Transactional
  public boolean resetPin(String rawToken) {
    PinResetToken entity = resetTokens.findByTokenHash(sha256Hex(rawToken)).orElse(null);
    if (entity == null
        || entity.getUsedAt() != null
        || entity.getExpiresAt().isBefore(Instant.now())) {
      return false;
    }
    User u = users.findById(entity.getUserId()).orElse(null);
    if (u == null) return false;

    u.setPinHash(null);
    u.setPinFailedAttempts(0);
    u.setPinLockedUntil(null);
    users.save(u);

    entity.setUsedAt(Instant.now());
    resetTokens.save(entity);

    audit.record(u.getId(), "PIN_RESET", null, null, null);
    return true;
  }

  private void sendResetEmail(String to, String rawToken) {
    try {
      String link = frontendBaseUrl + "/pin-reset?token=" + rawToken;
      SimpleMailMessage msg = new SimpleMailMessage();
      if (mailFrom != null && !mailFrom.isBlank()) msg.setFrom(mailFrom);
      msg.setTo(to);
      msg.setSubject("CVFacil.NG — Redefinição do PIN de acesso");
      msg.setText(
          "Recebemos um pedido para redefinir o PIN de acesso via Google da sua conta.\n\n"
              + "Clique no link abaixo para confirmar (válido por 30 minutos). Depois disso, seu "
              + "próximo login com Google vai pedir a criação de um PIN novo:\n"
              + link
              + "\n\nSe você não pediu isso, ignore este e-mail — seu PIN continua o mesmo.");
      mailSender.send(msg);
    } catch (Exception e) {
      // Falha de envio nunca deve vazar para o chamador (resposta do endpoint é sempre 200) — só
      // loga para investigação, mesmo padrão de PasswordResetService.
      log.error("[pin-reset] Falha ao enviar e-mail de redefinição: {}", e.getMessage());
    }
  }

  private String generateToken() {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String sha256Hex(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : digest) sb.append(String.format("%02x", b));
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
