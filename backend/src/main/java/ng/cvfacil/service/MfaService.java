package ng.cvfacil.service;

import java.util.UUID;
import ng.cvfacil.domain.User;
import ng.cvfacil.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Ativação/desativação de MFA (TOTP). Compartilhado entre MfaController (produção) e o fluxo de
 * verificação em AuthController.login/mfaVerify — mesmo padrão de dedup do resto do projeto.
 *
 * <p>Fluxo de ativação: setup() gera e persiste um novo secret com mfaEnabled=false (não vale ainda
 * para login) → o usuário confirma com um código válido em confirm() → só então mfaEnabled vira
 * true. Isso evita que um usuário fique trancado para fora da própria conta por ter copiado o
 * secret errado.
 */
@Service
public class MfaService {

  private final UserRepository users;
  private final TotpService totp;
  private final AuditService audit;
  private final PasswordEncoder encoder;

  public MfaService(
      UserRepository users, TotpService totp, AuditService audit, PasswordEncoder encoder) {
    this.users = users;
    this.totp = totp;
    this.audit = audit;
    this.encoder = encoder;
  }

  public record SetupResult(String secret, String otpAuthUri) {}

  public static class ReauthRequiredException extends RuntimeException {}

  /**
   * Gera um novo secret TOTP. Se a conta já tem MFA ativo, exige a senha atual — sem isso, um
   * access token vazado bastaria para trocar o secret e derrubar a proteção de MFA sem que o dono
   * da conta percebesse (o novo secret não é mostrado a ninguém além de quem já tem o token).
   */
  public SetupResult setup(UUID userId, String password) {
    User u = users.findById(userId).orElseThrow();
    if (u.isMfaEnabled()) {
      if (password == null
          || u.getPasswordHash() == null
          || !encoder.matches(password, u.getPasswordHash())) {
        throw new ReauthRequiredException();
      }
    }
    String secret = totp.generateSecret();
    u.setMfaSecret(secret);
    u.setMfaEnabled(false);
    users.save(u);
    return new SetupResult(secret, totp.buildOtpAuthUri(secret, u.getEmail()));
  }

  public boolean confirm(UUID userId, String code) {
    User u = users.findById(userId).orElseThrow();
    if (u.getMfaSecret() == null || !totp.verifyCode(u.getMfaSecret(), code)) return false;
    u.setMfaEnabled(true);
    users.save(u);
    audit.record(userId, "MFA_ENABLED", null, null, null);
    return true;
  }

  public void disable(UUID userId) {
    User u = users.findById(userId).orElseThrow();
    u.setMfaEnabled(false);
    u.setMfaSecret(null);
    users.save(u);
    audit.record(userId, "MFA_DISABLED", null, null, null);
  }

  public boolean verifyLoginCode(User u, String code) {
    return u.isMfaEnabled() && totp.verifyCode(u.getMfaSecret(), code);
  }
}
