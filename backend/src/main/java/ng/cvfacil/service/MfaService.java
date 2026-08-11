package ng.cvfacil.service;

import java.util.UUID;
import ng.cvfacil.domain.User;
import ng.cvfacil.repository.UserRepository;
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

  public MfaService(UserRepository users, TotpService totp, AuditService audit) {
    this.users = users;
    this.totp = totp;
    this.audit = audit;
  }

  public record SetupResult(String secret, String otpAuthUri) {}

  public SetupResult setup(UUID userId) {
    User u = users.findById(userId).orElseThrow();
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
