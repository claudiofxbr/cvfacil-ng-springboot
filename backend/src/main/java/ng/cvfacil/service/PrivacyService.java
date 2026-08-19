package ng.cvfacil.service;

import java.util.Optional;
import java.util.UUID;
import ng.cvfacil.domain.CreditTransaction;
import ng.cvfacil.domain.User;
import ng.cvfacil.dto.PrivacyDtos.CreditTransactionView;
import ng.cvfacil.dto.PrivacyDtos.UserExport;
import ng.cvfacil.repository.CreditTransactionRepository;
import ng.cvfacil.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Autoatendimento LGPD Art. 18 / GDPR Art. 15-20 — acesso, portabilidade e eliminação dos dados do
 * próprio titular (diferente de AdminService.deleteUser, que é um Root/Admin apagando OUTRA
 * conta).
 */
@Service
public class PrivacyService {

  private final UserRepository users;
  private final CreditTransactionRepository creditTransactions;
  private final ResumeService resumes;
  private final PasswordEncoder encoder;
  private final AuditService audit;

  public PrivacyService(
      UserRepository users,
      CreditTransactionRepository creditTransactions,
      ResumeService resumes,
      PasswordEncoder encoder,
      AuditService audit) {
    this.users = users;
    this.creditTransactions = creditTransactions;
    this.resumes = resumes;
    this.encoder = encoder;
    this.audit = audit;
  }

  public Optional<UserExport> export(UUID userId) {
    return users.findById(userId).map(this::toExport);
  }

  public enum DeleteResult {
    OK,
    NOT_FOUND,
    WRONG_PASSWORD,
    ROOT_MASTER_BLOCKED
  }

  /**
   * Apaga a conta e, em cascata (ver FKs ON DELETE CASCADE nas migrations), currículos, fotos,
   * histórico de senha, tokens de reset e ledger de créditos. audit_logs é preservado
   * propositalmente (LGPD Art. 16 — obrigação legal de manter trilha de auditoria de segurança
   * mesmo após a eliminação da conta).
   */
  @Transactional
  public DeleteResult deleteOwnAccount(UUID userId, String password, String ip, String userAgent) {
    User u = users.findById(userId).orElse(null);
    if (u == null) return DeleteResult.NOT_FOUND;

    // ROOT_MASTER nao pode se autoexcluir por aqui: deixaria o sistema sem administrador raiz.
    // Transferencia de papel deve ser feita antes, por outro Root, via AdminController.
    if (u.getRole() == User.Role.ROOT_MASTER) return DeleteResult.ROOT_MASTER_BLOCKED;

    if (u.getPasswordHash() != null
        && !encoder.matches(password == null ? "" : password, u.getPasswordHash())) {
      return DeleteResult.WRONG_PASSWORD;
    }

    audit.record(userId, "USER_SELF_DELETE", ip, userAgent, "email=" + u.getEmail());
    users.delete(u);
    return DeleteResult.OK;
  }

  private UserExport toExport(User u) {
    var txns =
        creditTransactions.findByUserIdOrderByCreatedAtDesc(u.getId()).stream()
            .map(this::toTxnView)
            .toList();
    return new UserExport(
        u.getId(),
        u.getEmail(),
        u.getDisplayName(),
        u.getLocale(),
        u.getRole().name(),
        u.isEmailVerified(),
        u.isMfaEnabled(),
        u.getCredits(),
        u.getTermsAcceptedAt(),
        u.getTermsVersion(),
        u.getCreatedAt(),
        resumes.list(u.getId()),
        txns);
  }

  private CreditTransactionView toTxnView(CreditTransaction t) {
    return new CreditTransactionView(
        t.getId(),
        t.getType().name(),
        t.getAmount(),
        t.getBalanceAfter(),
        t.getReference(),
        t.getCreatedAt());
  }
}
