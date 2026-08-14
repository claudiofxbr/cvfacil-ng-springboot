package ng.cvfacil.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import ng.cvfacil.domain.User;
import ng.cvfacil.dto.AuthDtos.ChangePasswordRequest;
import ng.cvfacil.dto.AuthDtos.ForgotPasswordRequest;
import ng.cvfacil.dto.AuthDtos.LoginRequest;
import ng.cvfacil.dto.AuthDtos.LoginResponse;
import ng.cvfacil.dto.AuthDtos.MfaChallengeResponse;
import ng.cvfacil.dto.AuthDtos.MfaVerifyRequest;
import ng.cvfacil.dto.AuthDtos.RegisterRequest;
import ng.cvfacil.dto.AuthDtos.ResetPasswordRequest;
import ng.cvfacil.dto.AuthDtos.SecurityStatus;
import ng.cvfacil.dto.AuthDtos.UserView;
import ng.cvfacil.repository.UserRepository;
import ng.cvfacil.security.JwtService;
import ng.cvfacil.service.AuditService;
import ng.cvfacil.service.CreditService;
import ng.cvfacil.service.MfaService;
import ng.cvfacil.service.PasswordPolicyService;
import ng.cvfacil.service.PasswordPolicyService.PolicyViolationException;
import ng.cvfacil.service.PasswordResetService;
import ng.cvfacil.service.RateLimitService;
import ng.cvfacil.service.RootIpAllowlistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final JwtService jwt;
  private final AuditService audit;
  private final RateLimitService rateLimit;
  private final PasswordResetService passwordReset;
  private final CreditService credits;
  private final MfaService mfa;
  private final PasswordPolicyService passwordPolicy;
  private final RootIpAllowlistService ipAllowlist;

  /**
   * JwtDecoder apenas disponível no profile "!local" (SecurityConfig). Injetado de forma opcional
   * para que AuthController funcione também em dev local.
   */
  @Autowired(required = false)
  private JwtDecoder jwtDecoder;

  /** false em dev (HTTP), true em produção (HTTPS). */
  @Value("${cvfacil.security.cookie-secure:true}")
  private boolean cookieSecure;

  public AuthController(
      UserRepository users,
      PasswordEncoder encoder,
      JwtService jwt,
      AuditService audit,
      RateLimitService rateLimit,
      PasswordResetService passwordReset,
      CreditService credits,
      MfaService mfa,
      PasswordPolicyService passwordPolicy,
      RootIpAllowlistService ipAllowlist) {
    this.users = users;
    this.encoder = encoder;
    this.jwt = jwt;
    this.audit = audit;
    this.rateLimit = rateLimit;
    this.passwordReset = passwordReset;
    this.credits = credits;
    this.mfa = mfa;
    this.passwordPolicy = passwordPolicy;
    this.ipAllowlist = ipAllowlist;
  }

  @PostMapping("/register")
  public ResponseEntity<UserView> register(
      @Valid @RequestBody RegisterRequest req, HttpServletRequest http) {
    if (users.existsByEmailIgnoreCase(req.email())) {
      return ResponseEntity.status(409).build();
    }
    User u = new User();
    u.setEmail(req.email());
    u.setPasswordHash(encoder.encode(req.password()));
    u.setDisplayName(req.displayName());
    if (req.locale() != null) u.setLocale(req.locale());
    users.save(u);
    audit.record(
        u.getId(), "USER_REGISTER", http.getRemoteAddr(), http.getHeader("User-Agent"), null);
    // Cortesia: 1 crédito grátis de criação de currículo por conta nova.
    credits.grantCourtesyIfEligible(u.getId());
    return ResponseEntity.ok(view(u));
  }

  @PostMapping("/login")
  public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
    String ip = http.getRemoteAddr();
    if (!rateLimit.allow("login:" + ip, 5, Duration.ofMinutes(1))) {
      return ResponseEntity.status(429).build();
    }

    User u =
        users.findByEmailIgnoreCase(req.email()).orElseThrow(() -> new BadCredentialsException());

    if (u.getLockedUntil() != null && u.getLockedUntil().isAfter(java.time.Instant.now())) {
      return ResponseEntity.status(423).build(); // Locked
    }

    // Conta criada via OAuth (Google) — nunca teve senha definida.
    // NAO penaliza failedLogins: o usuario nao errou nada, apenas usou
    // o metodo de login errado. Retorna 422 com tipo especifico para que
    // o frontend exiba a mensagem correta ("Use o botao Google").
    if (u.getPasswordHash() == null) {
      audit.record(u.getId(), "LOGIN_OAUTH_ONLY_ATTEMPT", ip, http.getHeader("User-Agent"), null);
      return ResponseEntity.unprocessableEntity().body(Map.of("error", "oauth_only"));
    }

    if (!encoder.matches(req.password(), u.getPasswordHash())) {
      u.setFailedLogins(u.getFailedLogins() + 1);
      if (u.getFailedLogins() >= 5) {
        u.setLockedUntil(java.time.Instant.now().plus(Duration.ofMinutes(15)));
      }
      users.save(u);
      audit.record(u.getId(), "LOGIN_FAILURE", ip, http.getHeader("User-Agent"), null);
      throw new BadCredentialsException();
    }

    u.setFailedLogins(0);
    u.setLockedUntil(null);
    users.save(u);

    // PRD §4.4: RootMaster só autentica de IPs cadastrados na allowlist (quando ela
    // não estiver vazia — ver RootIpAllowlistService).
    if (u.getRole() == User.Role.ROOT_MASTER && !ipAllowlist.isAllowed(ip)) {
      audit.record(u.getId(), "LOGIN_ROOT_IP_BLOCKED", ip, http.getHeader("User-Agent"), null);
      return ResponseEntity.status(403).build();
    }

    if (u.isMfaEnabled()) {
      String challenge = jwt.issueMfaChallengeToken(u.getId());
      audit.record(u.getId(), "LOGIN_MFA_CHALLENGE", ip, http.getHeader("User-Agent"), null);
      return ResponseEntity.status(202).body(new MfaChallengeResponse(true, challenge));
    }

    return issueSession(u, ip, http.getHeader("User-Agent"));
  }

  /** Segundo fator: troca o challengeToken de 5 min por um código TOTP válido de 6 dígitos. */
  @PostMapping("/mfa-verify")
  public ResponseEntity<?> mfaVerify(
      @Valid @RequestBody MfaVerifyRequest req, HttpServletRequest http) {
    String ip = http.getRemoteAddr();
    UUID userId = decodeTypedToken(req.challengeToken(), "STUB_MFA.", "mfa_challenge");
    if (userId == null) return ResponseEntity.status(401).build();

    User u = users.findById(userId).orElse(null);
    if (u == null || !u.isMfaEnabled()) return ResponseEntity.status(401).build();

    if (!mfa.verifyLoginCode(u, req.code())) {
      audit.record(userId, "LOGIN_MFA_FAILURE", ip, http.getHeader("User-Agent"), null);
      return ResponseEntity.status(401).build();
    }

    return issueSession(u, ip, http.getHeader("User-Agent"));
  }

  /** Troca de senha self-service (usuário já autenticado) — aplica PasswordPolicyService. */
  @PostMapping("/change-password")
  public ResponseEntity<?> changePassword(
      @Valid @RequestBody ChangePasswordRequest req,
      @AuthenticationPrincipal Jwt principal,
      HttpServletRequest http) {
    UUID userId = resolveUserId(principal, http);
    if (userId == null) return ResponseEntity.status(401).build();
    User u = users.findById(userId).orElse(null);
    if (u == null || u.getPasswordHash() == null) return ResponseEntity.status(401).build();

    if (!encoder.matches(req.currentPassword(), u.getPasswordHash())) {
      return ResponseEntity.status(401).build();
    }
    try {
      passwordPolicy.validateNewPassword(u, req.newPassword());
    } catch (PolicyViolationException e) {
      return ResponseEntity.status(422).body(Map.of("error", e.getMessage()));
    }

    passwordPolicy.recordChange(userId, u.getPasswordHash());
    u.setPasswordHash(encoder.encode(req.newPassword()));
    u.setPasswordChangedAt(java.time.Instant.now());
    users.save(u);
    audit.record(
        userId, "PASSWORD_CHANGED", http.getRemoteAddr(), http.getHeader("User-Agent"), null);
    return ResponseEntity.ok().build();
  }

  /**
   * Reconfirma a senha do usuário já autenticado sem alterar nada — usado pelo timer de inatividade
   * de 5 min (PRD): ao expirar, o usuário digita a senha para continuar de onde parou, em vez de
   * ser deslogado e perder o trabalho não salvo.
   */
  @PostMapping("/verify-password")
  public ResponseEntity<Void> verifyPassword(
      @Valid @RequestBody ng.cvfacil.dto.AuthDtos.VerifyPasswordRequest req,
      @AuthenticationPrincipal Jwt principal,
      HttpServletRequest http) {
    UUID userId = resolveUserId(principal, http);
    if (userId == null) return ResponseEntity.status(401).build();
    User u = users.findById(userId).orElse(null);
    if (u == null || u.getPasswordHash() == null) return ResponseEntity.status(401).build();
    return encoder.matches(req.password(), u.getPasswordHash())
        ? ResponseEntity.ok().build()
        : ResponseEntity.status(401).build();
  }

  /** Estado de segurança da conta logada — alimenta a página /dashboard/security do frontend. */
  @GetMapping("/security-status")
  public ResponseEntity<SecurityStatus> securityStatus(
      @AuthenticationPrincipal Jwt principal, HttpServletRequest http) {
    UUID userId = resolveUserId(principal, http);
    if (userId == null) return ResponseEntity.status(401).build();
    User u = users.findById(userId).orElse(null);
    if (u == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(
        new SecurityStatus(
            u.isMfaEnabled(),
            passwordPolicy.passwordAgeDays(u),
            passwordPolicy.rotationOverdue(u),
            u.getRole().name()));
  }

  private ResponseEntity<LoginResponse> issueSession(User u, String ip, String userAgent) {
    String access = jwt.issueAccessToken(u);
    String refresh = jwt.issueRefreshToken(u.getId());
    audit.record(u.getId(), "LOGIN_SUCCESS", ip, userAgent, null);
    return ResponseEntity.ok()
        .header("Set-Cookie", buildRefreshCookie(refresh).toString())
        .body(new LoginResponse(view(u), access));
  }

  /**
   * Resolve o userId autenticado tanto em produção (Jwt principal real) quanto em dev local (STUB
   * token no header — não há JwtDecoder/resource server configurado em @Profile("local")). Mesmo
   * padrão usado em LocalAdminController.
   */
  private UUID resolveUserId(Jwt principal, HttpServletRequest request) {
    if (principal != null) {
      try {
        return UUID.fromString(principal.getSubject());
      } catch (Exception ignored) {
      }
    }
    // STUB só é aceito quando não há JwtDecoder real (profile "local") — em qualquer outro
    // profile, um STUB_ACCESS forjado pelo cliente nunca deve autenticar (ver decodeTypedToken).
    if (jwtDecoder == null) {
      String auth = request.getHeader("Authorization");
      if (auth != null && auth.startsWith("Bearer STUB_ACCESS.")) {
        String[] parts = auth.substring("Bearer ".length()).split("\\.", 3);
        if (parts.length >= 2) {
          try {
            return UUID.fromString(parts[1]);
          } catch (Exception ignored) {
          }
        }
      }
    }
    return null;
  }

  /**
   * Decodifica um token com claim "type". O formato STUB legado só é aceito quando {@code
   * jwtDecoder} é nulo (profile "local", sem resource server configurado) — checar o prefixo
   * antes do profile permitiria que qualquer cliente forjasse "STUB_MFA.<uuid>.x"/"STUB_REFRESH.
   * <uuid>.x" para autenticar como qualquer usuário em produção, pulando MFA e o allowlist de IP
   * do Root.
   */
  private UUID decodeTypedToken(String token, String stubPrefix, String expectedType) {
    if (token == null || token.isBlank()) return null;
    if (jwtDecoder == null) {
      if (token.startsWith(stubPrefix)) {
        String[] parts = token.split("\\.", 3);
        if (parts.length < 3) return null;
        try {
          return UUID.fromString(parts[1]);
        } catch (IllegalArgumentException e) {
          return null;
        }
      }
      return null;
    }
    try {
      Jwt decoded = jwtDecoder.decode(token);
      if (!expectedType.equals(decoded.getClaimAsString("type"))) return null;
      return UUID.fromString(decoded.getSubject());
    } catch (JwtException | IllegalArgumentException e) {
      return null;
    }
  }

  /**
   * Usa o cookie httpOnly de refresh para emitir um novo access token. Permite restaurar a sessão
   * após F5 ou abertura de nova aba sem pedir login.
   *
   * <p>Em produção ({@code JWT_PRIVATE_KEY} configurada): o refresh token é um JWT RS256. Valida
   * assinatura e claim {@code type=refresh} via {@link JwtDecoder} e extrai o {@code sub} (userId).
   *
   * <p>Em dev local ({@code JwtDecoder} não disponível): aceita o formato legado {@code
   * STUB_REFRESH.<userId>.<uuid>} sem validação criptográfica.
   */
  @PostMapping("/refresh")
  public ResponseEntity<LoginResponse> refresh(
      @CookieValue(name = "refresh_token", required = false) String refreshToken) {

    UUID userId = decodeTypedToken(refreshToken, "STUB_REFRESH.", "refresh");
    if (userId == null) return ResponseEntity.status(401).build();

    User u = users.findById(userId).orElse(null);
    if (u == null) return ResponseEntity.status(401).build();

    String newAccess = jwt.issueAccessToken(u);
    String newRefresh = jwt.issueRefreshToken(userId);

    return ResponseEntity.ok()
        .header("Set-Cookie", buildRefreshCookie(newRefresh).toString())
        .body(new LoginResponse(view(u), newAccess));
  }

  @PostMapping("/forgot-password")
  public ResponseEntity<Void> forgotPassword(
      @Valid @RequestBody ForgotPasswordRequest req, HttpServletRequest http) {
    String ip = http.getRemoteAddr();
    // Rate limit por IP para não permitir enviar e-mails em massa / martelar o SMTP.
    // Mesmo limite excedido: ainda retorna 200 (não revela nada sobre o e-mail).
    if (rateLimit.allow("forgot-password:" + ip, 5, Duration.ofMinutes(1))) {
      passwordReset.requestReset(req.email());
    }
    // Retorna 200 independente de o e-mail existir (previne enumeracao de contas).
    return ResponseEntity.ok().build();
  }

  @PostMapping("/reset-password")
  public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
    boolean ok = passwordReset.resetPassword(req.token(), req.password());
    return ok ? ResponseEntity.ok().build() : ResponseEntity.status(400).build();
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout() {
    ResponseCookie expire =
        ResponseCookie.from("refresh_token", "")
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite("Strict")
            .path("/")
            .maxAge(0)
            .build();
    return ResponseEntity.noContent().header("Set-Cookie", expire.toString()).build();
  }

  // ---- helpers ----

  private ResponseCookie buildRefreshCookie(String value) {
    return ResponseCookie.from("refresh_token", value)
        .httpOnly(true)
        .secure(cookieSecure)
        .sameSite("Strict")
        .path("/")
        .maxAge(jwt.refreshTtl())
        .build();
  }

  private UserView view(User u) {
    return new UserView(
        u.getId(),
        u.getEmail(),
        u.getDisplayName(),
        u.getRole().name(),
        u.getLocale(),
        u.isMfaEnabled());
  }

  @ResponseStatus(org.springframework.http.HttpStatus.UNAUTHORIZED)
  static class BadCredentialsException extends RuntimeException {}
}
