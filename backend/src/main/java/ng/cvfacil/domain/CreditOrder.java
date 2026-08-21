package ng.cvfacil.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Pedido de compra de créditos junto ao PagSeguro — vida útil PENDING até o webhook confirmar
 * (PAID) ou recusar (FAILED). credit_transactions só ganha uma linha quando o status vira PAID (ver
 * CreditService.grantPurchase), nunca antes.
 */
@Entity
@Table(name = "credit_orders")
public class CreditOrder {

  public enum Status {
    PENDING,
    PAID,
    FAILED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  /**
   * Nulo quando o titular exerceu o direito de exclusão (LGPD Art. 18) — a linha do ledger é
   * preservada.
   */
  @Column(name = "user_id")
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "package", nullable = false, length = 20)
  private CreditPackage pack;

  @Column(name = "pagseguro_order_id", nullable = false, length = 64)
  private String pagSeguroOrderId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Status status = Status.PENDING;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public CreditPackage getPack() {
    return pack;
  }

  public void setPack(CreditPackage pack) {
    this.pack = pack;
  }

  public String getPagSeguroOrderId() {
    return pagSeguroOrderId;
  }

  public void setPagSeguroOrderId(String pagSeguroOrderId) {
    this.pagSeguroOrderId = pagSeguroOrderId;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
