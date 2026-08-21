package ng.cvfacil.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Ledger append-only de créditos — nunca editar/apagar uma linha existente. */
@Entity
@Table(name = "credit_transactions")
public class CreditTransaction {

  public enum Type {
    COURTESY,
    PURCHASE,
    ADMIN_GRANT,
    CONSUMPTION
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
  @Column(nullable = false, length = 20)
  private Type type;

  /** Positivo para crédito concedido, negativo para consumo. */
  @Column(nullable = false)
  private int amount;

  @Column(name = "balance_after", nullable = false)
  private int balanceAfter;

  @Column(length = 255)
  private String reference;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public Type getType() {
    return type;
  }

  public void setType(Type type) {
    this.type = type;
  }

  public int getAmount() {
    return amount;
  }

  public void setAmount(int amount) {
    this.amount = amount;
  }

  public int getBalanceAfter() {
    return balanceAfter;
  }

  public void setBalanceAfter(int balanceAfter) {
    this.balanceAfter = balanceAfter;
  }

  public String getReference() {
    return reference;
  }

  public void setReference(String reference) {
    this.reference = reference;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
