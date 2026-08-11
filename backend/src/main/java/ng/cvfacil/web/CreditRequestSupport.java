package ng.cvfacil.web;

import java.util.UUID;
import ng.cvfacil.domain.CreditTransaction;
import ng.cvfacil.dto.CreditDtos.TransactionView;
import ng.cvfacil.dto.CreditDtos.WalletView;
import ng.cvfacil.service.CreditService;
import org.springframework.http.ResponseEntity;

/**
 * Lógica compartilhada entre CreditController (produção) e LocalCreditController (dev local) —
 * mesmo padrão de dedup já usado para Resume/AIImport/Admin.
 */
final class CreditRequestSupport {

  private CreditRequestSupport() {}

  static ResponseEntity<WalletView> wallet(CreditService credits, UUID userId) {
    int balance = credits.balanceOf(userId);
    var history = credits.historyOf(userId).stream().map(CreditRequestSupport::toView).toList();
    return ResponseEntity.ok(new WalletView(balance, history));
  }

  /**
   * Sem gateway de pagamento real configurado (ver CreditService.isPurchaseGatewayConfigured) —
   * responde 501, mesmo padrão já usado para IA quando AI_API_KEY não está definida.
   */
  static ResponseEntity<Void> purchase(CreditService credits) {
    if (!credits.isPurchaseGatewayConfigured()) {
      return ResponseEntity.status(501).build();
    }
    // Quando um gateway real for integrado, o fluxo correto é: criar uma
    // transação PENDING, redirecionar para o checkout do provedor, e só
    // chamar credits.grantPurchase(...) a partir do webhook de confirmação
    // de pagamento — nunca a partir desta requisição síncrona do cliente.
    return ResponseEntity.status(501).build();
  }

  private static TransactionView toView(CreditTransaction tx) {
    return new TransactionView(
        tx.getId(),
        tx.getType().name(),
        tx.getAmount(),
        tx.getBalanceAfter(),
        tx.getReference(),
        tx.getCreatedAt());
  }
}
