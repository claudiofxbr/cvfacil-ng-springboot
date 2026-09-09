package ng.cvfacil.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import ng.cvfacil.domain.User;
import ng.cvfacil.dto.AuthDtos.ChangePinRequest;
import ng.cvfacil.dto.AuthDtos.LoginResponse;
import ng.cvfacil.dto.AuthDtos.PinFailureResponse;
import ng.cvfacil.dto.AuthDtos.PinSetupRequest;
import ng.cvfacil.dto.AuthDtos.PinStatusResponse;
import ng.cvfacil.dto.AuthDtos.PinVerifyRequest;
import ng.cvfacil.dto.AuthDtos.ResetPinRequest;
import ng.cvfacil.dto.AuthDtos.UserView;
import ng.cvfacil.repository.UserRepository;
import ng.cvfacil.security.JwtService;
import ng.cvfacil.service.PinService;
import ng.cvfacil.service.RateLimitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.*;

/**
 * PIN de 8 dígitos — segundo fator obrigatório em todo login/cadastro via Google, mesmo com sessão
 * já ativa no navegador. Ver {@link ng.cvfacil.security.OAuth2LoginSuccessHandler} para o ponto que
 * redireciona para {@code /oauth2/pin} em vez de emitir sessão direto, e {@link PinService} para a
 * lógica de hash/bloqueio/reset.
 *
 * <p>Os endpoints de setup/verify NUNCA confiam em identidade vinda do corpo da requisição — o
 * usuário é sempre resolvido a partir do cookie httpOnly ({@code pin_setup_state}/{@code
 * pin_verify_state}) gravado pelo success handler, o mesmo padrão já usado por {@code relink_state}
 * em AuthController.
 */
@RestController
@RequestMapping("/api/auth/pin")
public class PinController {

  private final UserRepository users;
  private final JwtService jwt;
  private final PinService pinService;
  private final RateLimitService rateLimit;

  @Autowired(required = false)
  @Lazy
  private JwtDecoder jwtDecoder;

  @Value("${cvfacil.security.cookie-secure:true}")
  private boolean cookieSecure;

  public PinController(
      UserRepository users, JwtService jwt, PinService pinService, RateLimitService rateLimit) {
    this.users = users;
    this.jwt = jwt;
    this.pinService = pinService;
    this.rateLimit = rateLimit;
  }

  @GetMapping("/status")
  public ResponseEntity<PinStatusResponse> status(HttpServletRequest http) {
    if (decodeChallenge(readCookie(http, "pin_setup_state"), "STUB_PINSETUP.", "pin_setup")
        != null) {
      return ResponseEntity.ok(new PinStatusResponse("setup"));
    }
    if (decodeChallenge(readCookie(http, "pin_verify_state"), "STUB_PINVERIFY.", "pin_verify")
        != null) {
      return ResponseEntity.ok(new PinStatusResponse("verify"));
    }
    return ResponseEntity.ok(new PinStatusResponse("none"));
  }

  @PostMapping("/setup")
  public ResponseEntity<?> setup(
      @Valid @RequestBody PinSetupRequest req, HttpServletRequest http, HttpServletResponse resp) {
    String ip = http.getRemoteAddr();
    if (!rateLimit.allow("pin-setup:" + ip, 10, Duration.ofMinutes(15))) {
      return ResponseEntity.status(429).build();
    }
    if (!req.pin().equals(req.pinConfirm())) {
      return ResponseEntity.status(422).body(Map.of("error", "pin_mismatch"));
    }
    if (!pinService.isValidPin(req.pin())) {
      return ResponseEntity.status(422).body(Map.of("error", "invalid_pin"));
    }

    UUID userId = consumeCookie(http, resp, "pin_setup_state", "STUB_PINSETUP.", "pin_setup");
    if (userId == null) return ResponseEntity.status(401).build();

    User u = users.findById(userId).orElse(null);
    if (u == null) return ResponseEntity.status(401).build();
    if (u.getPinHash() != null) {
      // Já tinha PIN — nada a fazer aqui (o cookie era pra setup, mas o estado mudou nesse meio
      // tempo); a chamada de verify é a correta agora.
      return ResponseEntity.status(409).build();
    }

    pinService.setupPin(u, req.pin(), ip, http.getHeader("User-Agent"));
    return issueSession(u, resp);
  }

  @PostMapping("/verify")
  public ResponseEntity<?> verify(
      @Valid @RequestBody PinVerifyRequest req, HttpServletRequest http, HttpServletResponse resp) {
    String ip = http.getRemoteAddr();
    if (!rateLimit.allow("pin-verify:" + ip, 10, Duration.ofMinutes(15))) {
      return ResponseEntity.status(429).build();
    }

    // Lê sem consumir ainda — se o PIN estiver errado, o desafio precisa continuar valendo para
    // a próxima tentativa (só é apagado em caso de acerto ou de bloqueio definitivo).
    String rawCookie = readCookie(http, "pin_verify_state");
    UUID userId = decodeChallenge(rawCookie, "STUB_PINVERIFY.", "pin_verify");
    if (userId == null) return ResponseEntity.status(401).build();

    User u = users.findById(userId).orElse(null);
    if (u == null || u.getPinHash() == null) return ResponseEntity.status(401).build();

    if (pinService.isLocked(u)) {
      expireCookie(resp, "pin_verify_state");
      return ResponseEntity.status(423).body(new PinFailureResponse("pin_locked", 0));
    }

    if (!pinService.verifyPin(u, req.pin(), ip, http.getHeader("User-Agent"))) {
      int remaining = Math.max(0, 5 - u.getPinFailedAttempts());
      if (remaining == 0) expireCookie(resp, "pin_verify_state");
      return ResponseEntity.status(401).body(new PinFailureResponse("wrong_pin", remaining));
    }

    expireCookie(resp, "pin_verify_state");
    return issueSession(u, resp);
  }

  /** Troca de PIN self-service (usuário já com sessão válida) — aba Segurança. */
  @PutMapping
  public ResponseEntity<Void> changePin(
      @Valid @RequestBody ChangePinRequest req,
      @AuthenticationPrincipal Jwt principal,
      HttpServletRequest http) {
    UUID userId = resolveUserId(principal, http);
    if (userId == null) return ResponseEntity.status(401).build();
    User u = users.findById(userId).orElse(null);
    if (u == null) return ResponseEntity.status(401).build();

    boolean ok =
        pinService.changePin(
            u, req.currentPin(), req.newPin(), http.getRemoteAddr(), http.getHeader("User-Agent"));
    return ok ? ResponseEntity.ok().build() : ResponseEntity.status(401).build();
  }

  /** "Esqueci meu PIN" a partir da aba Segurança (usuário já autenticado por outro meio). */
  @PostMapping("/forgot")
  public ResponseEntity<Void> forgot(
      @AuthenticationPrincipal Jwt principal, HttpServletRequest http) {
    UUID userId = resolveUserId(principal, http);
    if (userId != null
        && rateLimit.allow("pin-forgot:" + http.getRemoteAddr(), 5, Duration.ofMinutes(15))) {
      users.findById(userId).ifPresent(pinService::requestReset);
    }
    return ResponseEntity.ok().build();
  }

  /**
   * "Esqueci meu PIN" a partir da própria tela de verificação (usuário ainda SEM sessão, mas com o
   * cookie {@code pin_verify_state} pendente) — evita o beco sem saída de quem esqueceu o PIN nunca
   * conseguir chegar na aba Segurança para pedir o reset, já que ela exige sessão válida.
   */
  @PostMapping("/forgot-pending")
  public ResponseEntity<Void> forgotPending(HttpServletRequest http) {
    UUID userId =
        decodeChallenge(readCookie(http, "pin_verify_state"), "STUB_PINVERIFY.", "pin_verify");
    if (userId != null
        && rateLimit.allow("pin-forgot:" + http.getRemoteAddr(), 5, Duration.ofMinutes(15))) {
      users.findById(userId).ifPresent(pinService::requestReset);
    }
    return ResponseEntity.ok().build();
  }

  @PostMapping("/reset")
  public ResponseEntity<Void> reset(@Valid @RequestBody ResetPinRequest req) {
    boolean ok = pinService.resetPin(req.token());
    return ok ? ResponseEntity.ok().build() : ResponseEntity.status(400).build();
  }

  // ---- helpers ----

  private ResponseEntity<LoginResponse> issueSession(User u, HttpServletResponse resp) {
    String access = jwt.issueAccessToken(u);
    String refresh = jwt.issueRefreshToken(u.getId());
    ResponseCookie cookie =
        ResponseCookie.from("refresh_token", refresh)
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite("Strict")
            .path("/")
            .maxAge(jwt.refreshTtl())
            .build();
    resp.addHeader("Set-Cookie", cookie.toString());
    return ResponseEntity.ok(new LoginResponse(view(u), access));
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

  private String readCookie(HttpServletRequest request, String name) {
    if (request.getCookies() == null) return null;
    for (Cookie c : request.getCookies()) {
      if (name.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
        return c.getValue();
      }
    }
    return null;
  }

  private void expireCookie(HttpServletResponse response, String name) {
    Cookie expire = new Cookie(name, "");
    expire.setPath("/");
    expire.setHttpOnly(true);
    expire.setSecure(cookieSecure);
    expire.setMaxAge(0);
    response.addCookie(expire);
  }

  /** Lê e decodifica o cookie de desafio SEM apagá-lo — usado para checar estado sem consumir. */
  private UUID decodeChallenge(String token, String stubPrefix, String expectedType) {
    if (token == null || token.isBlank()) return null;
    if (jwtDecoder == null) {
      if (token.startsWith(stubPrefix)) {
        String[] parts = token.split("\\.", 3);
        if (parts.length < 2) return null;
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
   * Decodifica e apaga o cookie — uso único, para os endpoints que efetivamente consomem o desafio.
   */
  private UUID consumeCookie(
      HttpServletRequest request,
      HttpServletResponse response,
      String cookieName,
      String stubPrefix,
      String expectedType) {
    String token = readCookie(request, cookieName);
    UUID userId = decodeChallenge(token, stubPrefix, expectedType);
    expireCookie(response, cookieName);
    return userId;
  }

  /** Mesmo padrão usado em AuthController/LocalAdminController. */
  private UUID resolveUserId(Jwt principal, HttpServletRequest request) {
    if (principal != null) {
      try {
        return UUID.fromString(principal.getSubject());
      } catch (Exception ignored) {
      }
    }
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
}
