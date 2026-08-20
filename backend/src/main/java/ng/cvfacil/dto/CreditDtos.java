package ng.cvfacil.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ng.cvfacil.domain.CreditPackage;

public class CreditDtos {

  public record WalletView(int balance, boolean unlimited, List<TransactionView> history) {}

  public record TransactionView(
      UUID id, String type, int amount, int balanceAfter, String reference, Instant createdAt) {}

  /**
   * CPF é exigido pelo PagSeguro na criação do pedido (KYC) — o CVFacil.NG não armazena esse dado
   * no perfil do usuário, então é solicitado a cada compra e usado só para essa chamada.
   */
  public record PurchaseRequest(
      @NotNull CreditPackage packageId,
      @NotBlank @Pattern(regexp = "\\d{11}", message = "CPF deve ter 11 dígitos") String taxId) {}

  public record PurchaseResponse(
      UUID orderId, String qrCodeText, String qrCodeImageUrl, String payLinkUrl) {}
}
