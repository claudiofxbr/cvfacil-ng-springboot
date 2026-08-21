package ng.cvfacil.web;

import ng.cvfacil.service.CreditService;
import ng.cvfacil.service.PagSeguroClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recebe a notificação assíncrona do PagSeguro quando o status de um pedido muda (ex.: Pix pago).
 * Rota pública (sem JWT — o PagSeguro não tem um token nosso) protegida pela validação de
 * assinatura x-authenticity-token (ver PagSeguroClient.isValidWebhookSignature e SecurityConfig,
 * onde /api/credits/webhook/** é liberado do filtro JWT).
 *
 * <p>Idempotente: CreditService.confirmOrderPaid só concede crédito na primeira confirmação de cada
 * pedido (CreditOrderRepository.markPaidIfPending); reenvios do PagSeguro (comportamento normal de
 * gateways) são no-op.
 */
@RestController
@RequestMapping("/api/credits/webhook")
public class PagSeguroWebhookController {

  private static final Logger log = LoggerFactory.getLogger(PagSeguroWebhookController.class);

  private final PagSeguroClient pagSeguro;
  private final CreditService credits;

  public PagSeguroWebhookController(PagSeguroClient pagSeguro, CreditService credits) {
    this.pagSeguro = pagSeguro;
    this.credits = credits;
  }

  @PostMapping("/pagseguro")
  public ResponseEntity<Void> receive(
      @RequestBody String rawPayload,
      @RequestHeader(value = "x-authenticity-token", required = false) String authenticityToken) {
    if (!pagSeguro.isValidWebhookSignature(rawPayload, authenticityToken)) {
      log.warn("[PagSeguro Webhook] Assinatura inválida ou ausente — notificação rejeitada.");
      return ResponseEntity.status(401).build();
    }

    try {
      String orderId = pagSeguro.extractOrderId(rawPayload);
      String status = pagSeguro.extractChargeStatus(rawPayload);
      if (orderId == null || status == null) {
        log.warn("[PagSeguro Webhook] Payload sem id/status reconhecível: {}", rawPayload);
        return ResponseEntity.ok().build();
      }

      switch (status.toUpperCase()) {
        case "PAID" -> {
          boolean granted = credits.confirmOrderPaid(orderId);
          log.info(
              "[PagSeguro Webhook] Pedido {} PAID — crédito {}.",
              orderId,
              granted ? "concedido agora" : "já havia sido processado (ignorado)");
        }
        case "DECLINED", "CANCELED" -> {
          credits.confirmOrderFailed(orderId);
          log.info("[PagSeguro Webhook] Pedido {} {} — nenhum crédito concedido.", orderId, status);
        }
        default ->
            log.info(
                "[PagSeguro Webhook] Pedido {} status {} — sem ação (aguardando).",
                orderId,
                status);
      }
      return ResponseEntity.ok().build();
    } catch (Exception e) {
      log.error("[PagSeguro Webhook] Erro ao processar payload: {}", e.getMessage());
      // 200 para o PagSeguro não ficar reenviando indefinidamente um payload que nunca
      // vamos conseguir parsear; o erro fica registrado no log para investigação manual.
      return ResponseEntity.ok().build();
    }
  }
}
