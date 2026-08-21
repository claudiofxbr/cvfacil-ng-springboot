package ng.cvfacil.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "revoked_tokens")
public class RevokedToken {

  @Id private UUID jti;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  protected RevokedToken() {}

  public RevokedToken(UUID jti, Instant expiresAt) {
    this.jti = jti;
    this.expiresAt = expiresAt;
  }

  public UUID getJti() {
    return jti;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }
}
