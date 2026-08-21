package ng.cvfacil.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import ng.cvfacil.domain.CreditPackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Cliente HTTP para a API de Pedidos (Orders) do PagBank/PagSeguro — pagamento via Pix (QR Code),
 * mesmo padrão de HttpClient cru já usado em AIImportService (sem dependência nova de WebClient).
 *
 * <p>Referência oficial: https://developer.pagbank.com.br/reference/criar-pedido e
 * https://developer.pagbank.com.br/reference/confirmar-autenticidade-da-notificacao — consultadas
 * em 2026-08-20. A resposta exata do webhook (payload de charges[].status) não pôde ser validada
 * contra o sandbox real neste ambiente (sem acesso a testes end-to-end); revisar o payload recebido
 * no primeiro teste real e ajustar {@link #extractOrderId} / {@link #extractChargeStatus} se os
 * nomes de campo divergirem.
 */
@Service
public class PagSeguroClient {

  private static final Logger log = LoggerFactory.getLogger(PagSeguroClient.class);

  @Value("${cvfacil.payment.pagseguro.access-token:}")
  private String accessToken;

  @Value("${cvfacil.payment.pagseguro.base-url:https://sandbox.api.pagseguro.com}")
  private String baseUrl;

  /**
   * URL pública do backend para onde o PagSeguro envia a notificação de mudança de status
   * (webhook). OBRIGATÓRIO em produção — sem isso o pagamento nunca credita automaticamente.
   */
  @Value("${cvfacil.payment.pagseguro.notification-url:}")
  private String notificationUrl;

  private final ObjectMapper mapper = new ObjectMapper();

  private final HttpClient httpClient =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(15))
          .version(HttpClient.Version.HTTP_2)
          .build();

  public boolean isConfigured() {
    return accessToken != null && !accessToken.isBlank();
  }

  /** Resultado da criação do pedido: id no PagSeguro + dados do Pix (QR Code) para exibir. */
  public record OrderResult(
      String pagSeguroOrderId, String qrCodeText, String qrCodeImageUrl, String payLinkUrl) {}

  /**
   * Cria um pedido de pagamento via Pix (QR Code) no PagSeguro.
   *
   * @param referenceId identificador interno do pedido (nosso CreditOrder.id), correlacionado no
   *     campo reference_id do PagSeguro para rastreio nos dois lados.
   * @param customerName nome do titular (obrigatório pelo PagSeguro)
   * @param customerEmail e-mail do titular
   * @param customerTaxId CPF do titular (obrigatório pelo PagSeguro — KYC)
   */
  public OrderResult createPixOrder(
      String referenceId,
      CreditPackage pack,
      String customerName,
      String customerEmail,
      String customerTaxId)
      throws Exception {
    if (!isConfigured()) {
      throw new IllegalStateException("PagSeguro não configurado (access-token ausente).");
    }
    if (notificationUrl == null || notificationUrl.isBlank()) {
      throw new IllegalStateException(
          "cvfacil.payment.pagseguro.notification-url não configurado — sem isso o webhook de"
              + " confirmação de pagamento nunca chega.");
    }

    ObjectNode customer = mapper.createObjectNode();
    customer.put("name", customerName);
    customer.put("email", customerEmail);
    customer.put("tax_id", customerTaxId);

    ObjectNode item = mapper.createObjectNode();
    item.put("name", pack.name() + " (" + pack.credits + " créditos)");
    item.put("quantity", 1);
    item.put("unit_amount", pack.priceCents);
    ArrayNode items = mapper.createArrayNode().add(item);

    ObjectNode qrAmount = mapper.createObjectNode().put("value", pack.priceCents);
    ObjectNode qrCode = mapper.createObjectNode();
    qrCode.set("amount", qrAmount);
    qrCode.set("arrangements", mapper.createArrayNode().add("PAGBANK"));
    ArrayNode qrCodes = mapper.createArrayNode().add(qrCode);

    ArrayNode notificationUrls = mapper.createArrayNode().add(notificationUrl);

    ObjectNode body = mapper.createObjectNode();
    body.put("reference_id", referenceId);
    body.set("customer", customer);
    body.set("items", items);
    body.set("qr_codes", qrCodes);
    body.set("notification_urls", notificationUrls);

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/orders"))
            .header("Authorization", "Bearer " + accessToken)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
            .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() != 200 && response.statusCode() != 201) {
      log.error(
          "[PagSeguro] Falha ao criar pedido (HTTP {}): {}",
          response.statusCode(),
          response.body());
      throw new RuntimeException(
          "Falha ao criar pedido no PagSeguro (HTTP " + response.statusCode() + ").");
    }

    JsonNode json = mapper.readTree(response.body());
    String orderId = json.path("id").asText(null);
    if (orderId == null) {
      throw new RuntimeException("Resposta do PagSeguro sem campo 'id' do pedido.");
    }

    JsonNode qr = json.withArray("qr_codes").isEmpty() ? null : json.withArray("qr_codes").get(0);
    String text = qr != null ? qr.path("text").asText(null) : null;
    String imageUrl = null;
    String payLink = null;
    if (qr != null) {
      for (JsonNode link : qr.withArray("links")) {
        if ("QRCODE.PNG".equalsIgnoreCase(link.path("rel").asText())
            || "image".equalsIgnoreCase(link.path("media").asText())) {
          imageUrl = link.path("href").asText(null);
        }
      }
    }
    for (JsonNode link : json.withArray("links")) {
      if ("PAY".equalsIgnoreCase(link.path("rel").asText())) {
        payLink = link.path("href").asText(null);
      }
    }

    return new OrderResult(orderId, text, imageUrl, payLink);
  }

  /**
   * Valida a assinatura do webhook conforme documentação oficial: SHA-256 de
   * "{access-token}-{payload_bruto_sem_reformatacao}", comparado ao header x-authenticity-token.
   * Qualquer divergência (inclusive de formatação do payload) deve ser tratada como notificação NÃO
   * confiável e rejeitada — nunca processar um evento sem essa validação passar.
   */
  public boolean isValidWebhookSignature(String rawPayload, String authenticityTokenHeader) {
    if (authenticityTokenHeader == null || authenticityTokenHeader.isBlank()) return false;
    if (accessToken == null || accessToken.isBlank()) return false;
    try {
      String toHash = accessToken + "-" + rawPayload;
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(toHash.getBytes(StandardCharsets.UTF_8));
      String hex = HexFormat.of().formatHex(hash);
      return constantTimeEquals(hex, authenticityTokenHeader.trim());
    } catch (Exception e) {
      log.error("[PagSeguro] Erro ao validar assinatura do webhook: {}", e.getMessage());
      return false;
    }
  }

  /** Extrai o id do pedido de um payload de webhook (espelha o Order — ver aviso na classe). */
  public String extractOrderId(String rawPayload) throws Exception {
    JsonNode json = mapper.readTree(rawPayload);
    String id = json.path("id").asText(null);
    return id != null ? id : json.path("order_id").asText(null);
  }

  /** Extrai o status da primeira charge do payload de webhook (PAID/DECLINED/CANCELED/...). */
  public String extractChargeStatus(String rawPayload) throws Exception {
    JsonNode json = mapper.readTree(rawPayload);
    JsonNode charges = json.withArray("charges");
    if (!charges.isEmpty()) {
      return charges.get(0).path("status").asText(null);
    }
    return json.path("status").asText(null);
  }

  private static boolean constantTimeEquals(String a, String b) {
    return MessageDigest.isEqual(
        a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }
}
