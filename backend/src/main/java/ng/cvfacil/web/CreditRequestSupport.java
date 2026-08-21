package ng.cvfacil.web;

import java.util.UUID;
import ng.cvfacil.domain.CreditOrder;
import ng.cvfacil.domain.CreditTransaction;
import ng.cvfacil.domain.User;
import ng.cvfacil.dto.CreditDtos.PurchaseRequest;
import ng.cvfacil.dto.CreditDtos.PurchaseResponse;
import ng.cvfacil.dto.CreditDtos.TransactionView;
import ng.cvfacil.dto.CreditDtos.WalletView;
import ng.cvfacil.repository.UserRepository;
import ng.cvfacil.service.CreditService;
import ng.cvfacil.service.PagSeguroClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

/**
 * Lógica compartilhada entre CreditController (produção) e LocalCreditController (dev local) —
 * mesmo padrão de dedup já usado para Resume/AIImport/Admin.
 */
final class CreditRequestSupport {

  private static final Logger log = LoggerFactory.getLogger(CreditRequestSupport.class);

  private CreditRequestSupport() {}

  static ResponseEntity<WalletView> wallet(CreditService credits, UUID userId) {
    int balance = credits.balanceOf(userId);
    boolean unlimited = credits.hasUnlimitedCredits(userId);
    var history = credits.historyOf(userId).stream().map(CreditRequestSupport::toView).toList();
    return ResponseEntity.ok(new WalletView(balance, unlimited, history));
  }

  /**
   * Cria o pedido de pagamento via Pix no PagSeguro e registra um CreditOrder PENDING local. O
   * crédito só é concedido quando o webhook confirmar o pagamento (ver PagSeguroWebhookController),
   * nunca nesta requisição síncrona.
   */
  static ResponseEntity<PurchaseResponse> purchase(
      CreditService credits,
      PagSeguroClient pagSeguro,
      UserRepository users,
      UUID userId,
      PurchaseRequest req) {
    if (!credits.isPurchaseGatewayConfigured() || !pagSeguro.isConfigured()) {
      return ResponseEntity.status(501).build();
    }
    User user = users.findById(userId).orElse(null);
    if (user == null) return ResponseEntity.status(401).build();

    try {
      // reference_id provisório (UUID aleatório) só para correlacionar com o PagSeguro;
      // o CreditOrder local é gravado logo depois, com o id real do pedido no gateway.
      String referenceId = UUID.randomUUID().toString();
      PagSeguroClient.OrderResult result =
          pagSeguro.createPixOrder(
              referenceId, req.packageId(), user.getDisplayName(), user.getEmail(), req.taxId());

      CreditOrder order =
          credits.createPendingOrder(userId, req.packageId(), result.pagSeguroOrderId());

      return ResponseEntity.ok(
          new PurchaseResponse(
              order.getId(), result.qrCodeText(), result.qrCodeImageUrl(), result.payLinkUrl()));
    } catch (Exception e) {
      log.error(
          "[Credits] Falha ao criar pedido PagSeguro para user={}: {}", userId, e.getMessage());
      return ResponseEntity.status(502).build();
    }
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
