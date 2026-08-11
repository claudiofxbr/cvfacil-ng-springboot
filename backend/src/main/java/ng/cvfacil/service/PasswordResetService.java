package ng.cvfacil.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import ng.cvfacil.domain.PasswordResetToken;
import ng.cvfacil.domain.User;
import ng.cvfacil.repository.PasswordResetTokenRepository;
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
 * Fluxo de "esqueci minha senha" — antes AuthController.forgotPassword() retornava 200 sem gerar
 * token nem enviar e-mail (TODO explícito, ver commit anterior).
 *
 * <p>Token de reset: 32 bytes aleatórios (SecureRandom), enviado por e-mail em claro; só o SHA-256
 * do token é persistido (mesmo princípio de nunca guardar segredos em claro usado para
 * password_hash/mfa_secret). Expira em 30 minutos e é de uso único (usedAt).
 */
@Service
public class PasswordResetService {

  private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
  private static final Duration TOKEN_TTL = Duration.ofMinutes(30);

  private final UserRepository users;
  private final PasswordResetTokenRepository tokens;
  private final PasswordEncoder encoder;
  private final JavaMailSender mailSender;
  private final AuditService audit;
  private final PasswordPolicyService passwordPolicy;

  @Value("${cvfacil.frontend.base-url:http://localhost:3000}")
  private String frontendBaseUrl;

  @Value("${spring.mail.username:}")
  private String mailFrom;

  public PasswordResetService(
      UserRepository users,
      PasswordResetTokenRepository tokens,
      PasswordEncoder encoder,
      JavaMailSender mailSender,
      AuditService audit,
      PasswordPolicyService passwordPolicy) {
    this.users = users;
    this.tokens = tokens;
    this.encoder = encoder;
    this.mailSender = mailSender;
    this.audit = audit;
    this.passwordPolicy = passwordPolicy;
  }

  /**
   * Sempre "silencioso" do ponto de vista do chamador — não revela se o e-mail existe nem se é uma
   * conta OAuth-only (o AuthController sempre responde 200).
   */
  @Transactional
  public void requestReset(String email) {
    users
        .findByEmailIgnoreCase(email)
        .ifPresent(
            user -> {
              if (user.getPasswordHash() == null) {
                // Conta OAuth-only: não tem senha local para redefinir.
                return;
              }
              String rawToken = generateToken();
              PasswordResetToken entity = new PasswordResetToken();
              entity.setUserId(user.getId());
              entity.setTokenHash(sha256Hex(rawToken));
              entity.setExpiresAt(Instant.now().plus(TOKEN_TTL));
              tokens.save(entity);
              sendResetEmail(user.getEmail(), rawToken);
            });
  }

  /**
   * @return true se a senha foi redefinida; false se o token é inválido/expirado/já usado.
   */
  @Transactional
  public boolean resetPassword(String rawToken, String newPassword) {
    PasswordResetToken entity = tokens.findByTokenHash(sha256Hex(rawToken)).orElse(null);
    if (entity == null
        || entity.getUsedAt() != null
        || entity.getExpiresAt().isBefore(Instant.now())) {
      return false;
    }
    User user = users.findById(entity.getUserId()).orElse(null);
    if (user == null) return false;

    try {
      passwordPolicy.validateNewPassword(user, newPassword);
    } catch (PasswordPolicyService.PolicyViolationException e) {
      return false;
    }

    passwordPolicy.recordChange(user.getId(), user.getPasswordHash());
    user.setPasswordHash(encoder.encode(newPassword));
    user.setPasswordChangedAt(Instant.now());
    user.setFailedLogins(0);
    user.setLockedUntil(null);
    users.save(user);

    entity.setUsedAt(Instant.now());
    tokens.save(entity);

    audit.record(user.getId(), "PASSWORD_RESET", null, null, null);
    return true;
  }

  private void sendResetEmail(String to, String rawToken) {
    try {
      String link = frontendBaseUrl + "/reset-password?token=" + rawToken;
      SimpleMailMessage msg = new SimpleMailMessage();
      if (mailFrom != null && !mailFrom.isBlank()) msg.setFrom(mailFrom);
      msg.setTo(to);
      msg.setSubject("CVFacil.NG — Redefinição de senha");
      msg.setText(
          "Recebemos um pedido para redefinir sua senha.\n\n"
              + "Clique no link abaixo para escolher uma nova senha (válido por 30 minutos):\n"
              + link
              + "\n\nSe você não pediu isso, ignore este e-mail — sua senha continua a mesma.");
      mailSender.send(msg);
    } catch (Exception e) {
      // Falha de envio nunca deve vazar para o chamador (resposta do endpoint é
      // sempre 200, com ou sem e-mail cadastrado) — só loga para investigação.
      log.error("[password-reset] Falha ao enviar e-mail de redefinição: {}", e.getMessage());
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
