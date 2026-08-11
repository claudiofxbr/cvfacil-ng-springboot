package ng.cvfacil.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "resumes")
public class Resume {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "layout_id", nullable = false, length = 32)
  private String layoutId;

  @Column(nullable = false, length = 10)
  private String locale = "pt-BR";

  @Column(nullable = false)
  private int version = 1;

  /** Payload JSON cifrado em AES-256-GCM (column-level). */
  @Column(name = "content_enc", nullable = false, columnDefinition = "bytea")
  private byte[] contentEnc;

  @Column(name = "photo_url", length = 500)
  private String photoUrl;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public String getLayoutId() {
    return layoutId;
  }

  public void setLayoutId(String layoutId) {
    this.layoutId = layoutId;
  }

  public String getLocale() {
    return locale;
  }

  public void setLocale(String locale) {
    this.locale = locale;
  }

  public int getVersion() {
    return version;
  }

  public void setVersion(int v) {
    this.version = v;
  }

  public byte[] getContentEnc() {
    return contentEnc;
  }

  public void setContentEnc(byte[] c) {
    this.contentEnc = c;
  }

  public String getPhotoUrl() {
    return photoUrl;
  }

  public void setPhotoUrl(String p) {
    this.photoUrl = p;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
