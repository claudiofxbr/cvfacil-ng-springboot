package ng.cvfacil.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ng.cvfacil.dto.ResumeDtos.ResumeView;

/**
 * DTOs do autoatendimento LGPD Art. 18 / GDPR Art. 15-20 (acesso, portabilidade e eliminação) — ver
 * PrivacyController/PrivacyService.
 */
public class PrivacyDtos {

  /**
   * Exportação completa dos dados pessoais do titular autenticado (direito de
   * acesso/portabilidade).
   */
  public record UserExport(
      UUID id,
      String email,
      String displayName,
      String locale,
      String role,
      boolean emailVerified,
      boolean mfaEnabled,
      int credits,
      Instant termsAcceptedAt,
      String termsVersion,
      Instant createdAt,
      List<ResumeView> resumes,
      List<CreditTransactionView> creditTransactions) {}

  public record CreditTransactionView(
      UUID id, String type, int amount, int balanceAfter, String reference, Instant createdAt) {}

  /**
   * Confirmação de senha exigida para excluir a conta — evita que um JWT vazado/roubado apague a
   * conta sem o titular confirmar a senha (contas OAuth-only, sem senha, dispensam o campo).
   */
  public record DeleteAccountRequest(String password) {}
}
