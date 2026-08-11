package ng.cvfacil.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id")
  private UUID userId;

  @Column(nullable = false, length = 64)
  private String action;

  // Armazenado como VARCHAR(64) — pgjdbc 42.6+ rejeita String→inet via setString().
  @Column(name = "ip", length = 64)
  private String ip;

  @Column(name = "user_agent", length = 500)
  private String userAgent;

  // Armazenado como TEXT — pgjdbc 42.6+ rejeita String→jsonb via setNull/setString.
  @Column(name = "details_json", columnDefinition = "text")
  private String detailsJson;

  @Column(name = "prev_hash", length = 128)
  private String prevHash;

  @Column(name = "self_hash", nullable = false, length = 128)
  private String selfHash;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  public Long getId() { return id; }
  public UUID getUserId() { return userId; }
  public void setUserId(UUID u) { this.userId = u; }
  public String getAction() { return action; }
  public void setAction(String a) { this.action = a; }
  public String getIp() { return ip; }
  public void setIp(String i) { this.ip = i; }
  public String getUserAgent() { return userAgent; }
  public void setUserAgent(String ua) { this.userAgent = ua; }
  public String getDetailsJson() { return detailsJson; }
  public void setDetailsJson(String d) { this.detailsJson = d; }
  public String getPrevHash() { return prevHash; }
  public void setPrevHash(String p) { this.prevHash = p; }
  public String getSelfHash() { return selfHash; }
  public void setSelfHash(String s) { this.selfHash = s; }
  public Instant getCreatedAt() { return createdAt; }
}
