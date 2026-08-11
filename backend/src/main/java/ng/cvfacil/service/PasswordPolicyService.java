package ng.cvfacil.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import ng.cvfacil.domain.PasswordHistory;
import ng.cvfacil.domain.User;
import ng.cvfacil.repository.PasswordHistoryRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Política de senha do RootMaster (PRD §4.4): mínimo 16 caracteres, rotação recomendada a cada 90
 * dias, não reutilizar nenhuma das últimas 12 senhas. Demais papéis seguem a regra geral (mínimo 10
 * caracteres, já validada em AuthDtos) sem histórico/rotação.
 */
@Service
public class PasswordPolicyService {

  private static final int ROOT_MIN_LENGTH = 16;
  private static final int HISTORY_SIZE = 12;
  private static final Duration ROTATION_PERIOD = Duration.ofDays(90);

  private final PasswordHistoryRepository history;
  private final PasswordEncoder encoder;

  public PasswordPolicyService(PasswordHistoryRepository history, PasswordEncoder encoder) {
    this.history = history;
    this.encoder = encoder;
  }

  public static class PolicyViolationException extends RuntimeException {
    public PolicyViolationException(String message) {
      super(message);
    }
  }

  /** Lança PolicyViolationException se a nova senha viola a política do papel do usuário. */
  public void validateNewPassword(User user, String rawNewPassword) {
    if (user.getRole() == User.Role.ROOT_MASTER && rawNewPassword.length() < ROOT_MIN_LENGTH) {
      throw new PolicyViolationException(
          "Senha do RootMaster deve ter no mínimo " + ROOT_MIN_LENGTH + " caracteres");
    }
    for (PasswordHistory h : history.findTop12ByUserIdOrderByCreatedAtDesc(user.getId())) {
      if (encoder.matches(rawNewPassword, h.getPasswordHash())) {
        throw new PolicyViolationException(
            "Senha já foi usada recentemente — escolha uma diferente das últimas "
                + HISTORY_SIZE
                + " senhas");
      }
    }
  }

  /** Registra o hash atual no histórico (chamar ANTES de sobrescrever User.passwordHash). */
  public void recordChange(UUID userId, String currentPasswordHash) {
    if (currentPasswordHash == null) return; // conta OAuth sem senha anterior
    history.save(new PasswordHistory(userId, currentPasswordHash));
  }

  public boolean rotationOverdue(User user) {
    return user.getRole() == User.Role.ROOT_MASTER
        && Duration.between(user.getPasswordChangedAt(), Instant.now()).compareTo(ROTATION_PERIOD)
            > 0;
  }

  public long passwordAgeDays(User user) {
    return Duration.between(user.getPasswordChangedAt(), Instant.now()).toDays();
  }
}
