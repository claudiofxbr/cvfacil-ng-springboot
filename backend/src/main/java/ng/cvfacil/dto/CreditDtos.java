package ng.cvfacil.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ng.cvfacil.domain.CreditPackage;

public class CreditDtos {

  public record WalletView(int balance, List<TransactionView> history) {}

  public record TransactionView(
      UUID id, String type, int amount, int balanceAfter, String reference, Instant createdAt) {}

  public record PurchaseRequest(@NotNull CreditPackage packageId) {}
}
