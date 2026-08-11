package ng.cvfacil.service;

import java.util.List;
import java.util.UUID;
import ng.cvfacil.domain.CreditPackage;
import ng.cvfacil.domain.CreditTransaction;
import ng.cvfacil.domain.User;
import ng.cvfacil.repository.CreditTransactionRepository;
import ng.cvfacil.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Créditos de criação de currículo: 1 crédito = 1 currículo criado.
 *
 * Saldo (users.credits) é a leitura rápida; credit_transactions é o ledger
 * append-only para auditoria (mesmo padrão do audit_logs). Toda alteração de
 * saldo passa por um UPDATE atômico (UserRepository) + uma linha no ledger —
 * nunca lê-modifica-escreve o saldo em memória (evita a mesma classe de race
 * condition corrigida no AuditService).
 */
@Service
public class CreditService {

  public static final class InsufficientCreditsException extends RuntimeException {}

  private final UserRepository users;
  private final CreditTransactionRepository transactions;
  private final AuditService audit;

  /**
   * NENHUM gateway de pagamento real está integrado ainda (Mercado Pago é o
   * recomendado no PRD para Pix/boleto/cartão em BRL, mas exige credenciais
   * reais que este ambiente não tem). Enquanto {@code cvfacil.payment.mercadopago
   * .access-token} não estiver configurado, /api/credits/purchase responde 501 —
   * mesmo padrão já usado em AIImportService.isConfigured() para a IA.
   */
  @Value("${cvfacil.payment.mercadopago.access-token:}")
  private String mercadoPagoAccessToken;

  public CreditService(
      UserRepository users, CreditTransactionRepository transactions, AuditService audit) {
    this.users = users;
    this.transactions = transactions;
    this.audit = audit;
  }

  public boolean isPurchaseGatewayConfigured() {
    return mercadoPagoAccessToken != null && !mercadoPagoAccessToken.isBlank();
  }

  /** Concede 1 crédito de cortesia — idempotente, só concede uma vez por conta. */
  @Transactional
  public void grantCourtesyIfEligible(UUID userId) {
    if (transactions.existsByUserIdAndType(userId, CreditTransaction.Type.COURTESY)) return;
    grant(userId, 1, CreditTransaction.Type.COURTESY, "signup");
  }

  /** Concessão manual pelo Root — o controller deve garantir {@code actingRole == ROOT_MASTER}. */
  @Transactional
  public boolean grantByRoot(UUID actingUserId, User.Role actingRole, UUID targetUserId, int amount) {
    if (actingRole != User.Role.ROOT_MASTER || amount <= 0) return false;
    if (!users.existsById(targetUserId)) return false;
    grant(targetUserId, amount, CreditTransaction.Type.ADMIN_GRANT, "root:" + actingUserId);
    audit.record(actingUserId, "ADMIN_GRANT_CREDITS", null, null,
        "target=" + targetUserId + " amount=" + amount);
    return true;
  }

  /** Credita um pacote comprado (chamar somente após confirmação do gateway de pagamento). */
  @Transactional
  public void grantPurchase(UUID userId, CreditPackage pack) {
    grant(userId, pack.credits, CreditTransaction.Type.PURCHASE, pack.name());
  }

  /**
   * Consome 1 crédito para criar um currículo.
   * @throws InsufficientCreditsException se o saldo for zero.
   */
  @Transactional
  public void consumeOneForResumeCreation(UUID userId) {
    int updated = users.decrementOneCreditIfAvailable(userId);
    if (updated == 0) throw new InsufficientCreditsException();
    int balance = users.findById(userId).map(User::getCredits).orElse(0);
    CreditTransaction tx = new CreditTransaction();
    tx.setUserId(userId);
    tx.setType(CreditTransaction.Type.CONSUMPTION);
    tx.setAmount(-1);
    tx.setBalanceAfter(balance);
    transactions.save(tx);
  }

  public int balanceOf(UUID userId) {
    return users.findById(userId).map(User::getCredits).orElse(0);
  }

  public List<CreditTransaction> historyOf(UUID userId) {
    return transactions.findByUserIdOrderByCreatedAtDesc(userId);
  }

  private void grant(UUID userId, int amount, CreditTransaction.Type type, String reference) {
    users.incrementCredits(userId, amount);
    int balance = users.findById(userId).map(User::getCredits).orElse(0);
    CreditTransaction tx = new CreditTransaction();
    tx.setUserId(userId);
    tx.setType(type);
    tx.setAmount(amount);
    tx.setBalanceAfter(balance);
    tx.setReference(reference);
    transactions.save(tx);
  }
}
