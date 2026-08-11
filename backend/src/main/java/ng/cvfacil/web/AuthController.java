package ng.cvfacil.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import ng.cvfacil.domain.User;
import ng.cvfacil.dto.AuthDtos.ForgotPasswordRequest;
import ng.cvfacil.dto.AuthDtos.LoginRequest;
import ng.cvfacil.dto.AuthDtos.LoginResponse;
import ng.cvfacil.dto.AuthDtos.RegisterRequest;
import ng.cvfacil.dto.AuthDtos.ResetPasswordRequest;
import ng.cvfacil.dto.AuthDtos.UserView;
import ng.cvfacil.repository.UserRepository;
import ng.cvfacil.security.JwtService;
import ng.cvfacil.service.AuditService;
import ng.cvfacil.service.PasswordResetService;
import ng.cvfacil.service.RateLimitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseCookie;
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

  /**
   * JwtDecoder apenas disponível no profile "!local" (SecurityConfig).
   * Injetado de forma opcional para que AuthController funcione também em dev local.
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
      PasswordResetService passwordReset) {
    this.users = users;
    this.encoder = encoder;
    this.jwt = jwt;
    this.audit = audit;
    this.rateLimit = rateLimit;
    this.passwordReset = passwordReset;
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
    audit.record(u.getId(), "USER_REGISTER", http.getRemoteAddr(), http.getHeader("User-Agent"), null);
    return ResponseEntity.ok(view(u));
  }

  @PostMapping("/login")
  public ResponseEntity<?> login(
      @Valid @RequestBody LoginRequest req, HttpServletRequest http) {
    String ip = http.getRemoteAddr();
    if (!rateLimit.allow("login:" + ip, 5, Duration.ofMinutes(1))) {
      return ResponseEntity.status(429).build();
    }

    User u =
        users
            .findByEmailIgnoreCase(req.email())
            .orElseThrow(() -> new BadCredentialsException());

    if (u.getLockedUntil() != null && u.getLockedUntil().isAfter(java.time.Instant.now())) {
      return ResponseEntity.status(423).build(); // Locked
    }

    // Conta criada via OAuth (Google) — nunca teve senha definida.
    // NAO penaliza failedLogins: o usuario nao errou nada, apenas usou
    // o metodo de login errado. Retorna 422 com tipo especifico para que
    // o frontend exiba a mensagem correta ("Use o botao Google").
    if (u.getPasswordHash() == null) {
      audit.record(u.getId(), "LOGIN_OAUTH_ONLY_ATTEMPT", ip, http.getHeader("User-Agent"), null);
      return ResponseEntity.unprocessableEntity()
          .body(Map.of("error", "oauth_only"));
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

    String access  = jwt.issueAccessToken(u);
    String refresh = jwt.issueRefreshToken(u.getId());

    audit.record(u.getId(), "LOGIN_SUCCESS", ip, http.getHeader("User-Agent"), null);
    return ResponseEntity.ok()
        .header("Set-Cookie", buildRefreshCookie(refresh).toString())
        .body(new LoginResponse(view(u), access));
  }

  /**
   * Usa o cookie httpOnly de refresh para emitir um novo access token.
   * Permite restaurar a sessão após F5 ou abertura de nova aba sem pedir login.
   *
   * <p>Em produção ({@code JWT_PRIVATE_KEY} configurada): o refresh token é um JWT RS256.
   * Valida assinatura e claim {@code type=refresh} via {@link JwtDecoder} e extrai
   * o {@code sub} (userId).
   *
   * <p>Em dev local ({@code JwtDecoder} não disponível): aceita o formato legado
   * {@code STUB_REFRESH.<userId>.<uuid>} sem validação criptográfica.
   */
  @PostMapping("/refresh")
  public ResponseEntity<LoginResponse> refresh(
      @CookieValue(name = "refresh_token", required = false) String refreshToken) {

    if (refreshToken == null || refreshToken.isBlank()) {
      return ResponseEntity.status(401).build();
    }

    UUID userId;

    if (refreshToken.startsWith("STUB_REFRESH.")) {
      // Formato legado de dev local — sem validação criptográfica
      String[] parts = refreshToken.split("\\.", 3);
      if (parts.length < 3) return ResponseEntity.status(401).build();
      try {
        userId = UUID.fromString(parts[1]);
      } catch (IllegalArgumentException e) {
        return ResponseEntity.status(401).build();
      }
    } else {
      // Token RS256 real — valida assinatura e claims via JwtDecoder (produção)
      if (jwtDecoder == null) {
        // Em dev sem JwtDecoder configurado e token não é STUB → rejeita
        return ResponseEntity.status(401).build();
      }
      try {
        Jwt decoded = jwtDecoder.decode(refreshToken);
        // Verifica que é um refresh token (não um access token reutilizado)
        String type = decoded.getClaimAsString("type");
        if (!"refresh".equals(type)) {
          return ResponseEntity.status(401).build();
        }
        userId = UUID.fromString(decoded.getSubject());
      } catch (JwtException | IllegalArgumentException e) {
        return ResponseEntity.status(401).build();
      }
    }

    User u = users.findById(userId).orElse(null);
    if (u == null) return ResponseEntity.status(401).build();

    String newAccess  = jwt.issueAccessToken(u);
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
    return new UserView(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole().name(), u.getLocale());
  }

  @ResponseStatus(org.springframework.http.HttpStatus.UNAUTHORIZED)
  static class BadCredentialsException extends RuntimeException {}
}
