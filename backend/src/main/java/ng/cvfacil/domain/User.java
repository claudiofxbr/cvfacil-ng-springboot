package ng.cvfacil.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import ng.cvfacil.domain.convert.MfaSecretConverter;

@Entity
@Table(name = "users")
public class User {

  /**
   * USER (Cliente): CRUD e impressão dos próprios currículos. ADMIN: todas as prerrogativas de
   * ROOT_MASTER, exceto excluir usuários e conceder créditos manualmente — ver
   * AdminService/AdminController. ROOT_MASTER (Root): controle total, incluindo excluir usuários e
   * créditos.
   */
  public enum Role {
    USER,
    ADMIN,
    ROOT_MASTER
  }

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true, columnDefinition = "citext")
  private String email;

  /** BCrypt hash — nullable para contas que usam apenas OAuth. */
  @Column(name = "password_hash", length = 100)
  private String passwordHash;

  @Column(name = "display_name", length = 120)
  private String displayName;

  @Column(nullable = false, length = 10)
  private String locale = "pt-BR";

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private Role role = Role.USER;

  @Column(name = "email_verified", nullable = false)
  private boolean emailVerified;

  // Cifrado em repouso via MfaSecretConverter (AES-256-GCM) — ver V4 migration
  // que amplia a coluna: base64(IV + tag + segredo) é maior que o texto puro.
  @Convert(converter = MfaSecretConverter.class)
  @Column(name = "mfa_secret", length = 255)
  private String mfaSecret;

  /** Saldo de créditos de criação de currículo (1 crédito = 1 currículo). */
  @Column(nullable = false)
  private int credits;

  @Column(name = "failed_logins", nullable = false)
  private int failedLogins;

  @Column(name = "locked_until")
  private Instant lockedUntil;

  /**
   * BCrypt hash do PIN de 8 dígitos — segundo fator obrigatório em TODO login/cadastro via Google,
   * mesmo com sessão já ativa no navegador (ver OAuth2LoginSuccessHandler). {@code null} significa
   * que a conta ainda não tem PIN — o próximo login com Google interrompe o fluxo para criar um,
   * antes de liberar qualquer sessão.
   */
  @Column(name = "pin_hash", length = 100)
  private String pinHash;

  @Column(name = "pin_failed_attempts", nullable = false)
  private int pinFailedAttempts;

  @Column(name = "pin_locked_until")
  private Instant pinLockedUntil;

  /** true somente após confirmação do código TOTP em /api/mfa/confirm — ver MfaService. */
  @Column(name = "mfa_enabled", nullable = false)
  private boolean mfaEnabled;

  /** Usado para o aviso de rotação de 90 dias do RootMaster (PRD §4.4). */
  @Column(name = "password_changed_at", nullable = false)
  private Instant passwordChangedAt = Instant.now();

  /** LGPD Art. 8 / GDPR Art. 7 — quando o titular aceitou os Termos/Política vigentes. */
  @Column(name = "terms_accepted_at")
  private Instant termsAcceptedAt;

  @Column(name = "terms_version", length = 20)
  private String termsVersion;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }

  // ---- Getters / Setters ----
  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String n) {
    this.displayName = n;
  }

  public String getLocale() {
    return locale;
  }

  public void setLocale(String l) {
    this.locale = l;
  }

  public Role getRole() {
    return role;
  }

  public void setRole(Role r) {
    this.role = r;
  }

  public boolean isEmailVerified() {
    return emailVerified;
  }

  public void setEmailVerified(boolean v) {
    this.emailVerified = v;
  }

  public String getMfaSecret() {
    return mfaSecret;
  }

  public void setMfaSecret(String s) {
    this.mfaSecret = s;
  }

  public int getCredits() {
    return credits;
  }

  public void setCredits(int credits) {
    this.credits = credits;
  }

  public int getFailedLogins() {
    return failedLogins;
  }

  public void setFailedLogins(int n) {
    this.failedLogins = n;
  }

  public Instant getLockedUntil() {
    return lockedUntil;
  }

  public void setLockedUntil(Instant t) {
    this.lockedUntil = t;
  }

  public String getPinHash() {
    return pinHash;
  }

  public void setPinHash(String pinHash) {
    this.pinHash = pinHash;
  }

  public int getPinFailedAttempts() {
    return pinFailedAttempts;
  }

  public void setPinFailedAttempts(int n) {
    this.pinFailedAttempts = n;
  }

  public Instant getPinLockedUntil() {
    return pinLockedUntil;
  }

  public void setPinLockedUntil(Instant t) {
    this.pinLockedUntil = t;
  }

  public boolean isMfaEnabled() {
    return mfaEnabled;
  }

  public void setMfaEnabled(boolean mfaEnabled) {
    this.mfaEnabled = mfaEnabled;
  }

  public Instant getPasswordChangedAt() {
    return passwordChangedAt;
  }

  public void setPasswordChangedAt(Instant t) {
    this.passwordChangedAt = t;
  }

  public Instant getTermsAcceptedAt() {
    return termsAcceptedAt;
  }

  public void setTermsAcceptedAt(Instant t) {
    this.termsAcceptedAt = t;
  }

  public String getTermsVersion() {
    return termsVersion;
  }

  public void setTermsVersion(String v) {
    this.termsVersion = v;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
