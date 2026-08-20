package ng.cvfacil.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import ng.cvfacil.repository.UserRepository;
import ng.cvfacil.service.MfaService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Ativação/desativação de MFA (TOTP) para o usuário já autenticado. Independente de profile —
 * funciona tanto com Jwt principal real (produção) quanto com STUB token (dev local), mesmo padrão
 * de {@code resolveUserId} usado em AdminController/LocalAdminController.
 */
@RestController
@RequestMapping("/api/mfa")
public class MfaController {

  private final MfaService mfa;
  private final UserRepository users;
  private final PasswordEncoder encoder;

  public MfaController(MfaService mfa, UserRepository users, PasswordEncoder encoder) {
    this.mfa = mfa;
    this.users = users;
    this.encoder = encoder;
  }

  public record ConfirmRequest(@NotBlank @Pattern(regexp = "\\d{6}") String code) {}

  public record DisableRequest(@NotBlank String password) {}

  public record SetupRequest(String password) {}

  @PostMapping("/setup")
  public ResponseEntity<MfaService.SetupResult> setup(
      @RequestBody(required = false) SetupRequest req,
      @AuthenticationPrincipal Jwt principal,
      HttpServletRequest http) {
    UUID userId = resolveUserId(principal, http);
    if (userId == null) return ResponseEntity.status(401).build();
    try {
      return ResponseEntity.ok(mfa.setup(userId, req != null ? req.password() : null));
    } catch (MfaService.ReauthRequiredException e) {
      return ResponseEntity.status(401).build();
    }
  }

  @PostMapping("/confirm")
  public ResponseEntity<Void> confirm(
      @Valid @RequestBody ConfirmRequest req,
      @AuthenticationPrincipal Jwt principal,
      HttpServletRequest http) {
    UUID userId = resolveUserId(principal, http);
    if (userId == null) return ResponseEntity.status(401).build();
    return mfa.confirm(userId, req.code())
        ? ResponseEntity.ok().build()
        : ResponseEntity.status(400).build();
  }

  @PostMapping("/disable")
  public ResponseEntity<Void> disable(
      @Valid @RequestBody DisableRequest req,
      @AuthenticationPrincipal Jwt principal,
      HttpServletRequest http) {
    UUID userId = resolveUserId(principal, http);
    if (userId == null) return ResponseEntity.status(401).build();
    var u = users.findById(userId).orElse(null);
    if (u == null
        || u.getPasswordHash() == null
        || !encoder.matches(req.password(), u.getPasswordHash())) {
      return ResponseEntity.status(401).build();
    }
    mfa.disable(userId);
    return ResponseEntity.ok().build();
  }

  private UUID resolveUserId(Jwt principal, HttpServletRequest request) {
    if (principal != null) {
      try {
        return UUID.fromString(principal.getSubject());
      } catch (Exception ignored) {
      }
    }
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
    return null;
  }
}
