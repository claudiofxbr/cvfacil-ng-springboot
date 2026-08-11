package ng.cvfacil.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import ng.cvfacil.domain.User;
import ng.cvfacil.repository.UserRepository;
import ng.cvfacil.service.AdminService;
import ng.cvfacil.service.CreditService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Versão local de AdminController — ativa apenas em @Profile("local").
 *
 * <p>Diferença vs. produção: STUB tokens não carregam claim "role" (só userId), então o papel é
 * resolvido consultando o usuário no banco — permite testar RBAC em dev sem precisar de
 * JWT_PRIVATE_KEY/JWT_PUBLIC_KEY configurados. LocalSecurityConfig já abre /api/admin/**
 * (permitAll), então a checagem de papel abaixo é a única barreira em dev.
 */
@RestController
@RequestMapping("/api/admin")
@Profile("local")
public class LocalAdminController {

  private final AdminService service;
  private final UserRepository users;
  private final CreditService credits;

  public LocalAdminController(AdminService service, UserRepository users, CreditService credits) {
    this.service = service;
    this.users = users;
    this.credits = credits;
  }

  @GetMapping("/stats")
  public ResponseEntity<AdminController.Stats> stats(
      @AuthenticationPrincipal Jwt principal, HttpServletRequest request) {
    if (roleOf(principal, request) == null) return ResponseEntity.status(403).build();
    return ResponseEntity.ok(new AdminController.Stats(service.countUsers()));
  }

  @GetMapping("/users")
  public ResponseEntity<List<AdminService.UserAdminView>> users(
      @AuthenticationPrincipal Jwt principal, HttpServletRequest request) {
    if (roleOf(principal, request) == null) return ResponseEntity.status(403).build();
    return ResponseEntity.ok(service.listUsers());
  }

  @DeleteMapping("/users/{id}")
  public ResponseEntity<Void> deleteUser(
      @PathVariable UUID id, @AuthenticationPrincipal Jwt principal, HttpServletRequest request) {
    UUID actingUserId = resolveUserId(principal, request);
    User.Role role = roleOf(principal, request);
    if (actingUserId == null || role != User.Role.ROOT_MASTER) {
      return ResponseEntity.status(403).build();
    }
    return service.deleteUser(actingUserId, role, id)
        ? ResponseEntity.noContent().build()
        : ResponseEntity.notFound().build();
  }

  @PostMapping("/users/{id}/credits")
  public ResponseEntity<Void> grantCredits(
      @PathVariable UUID id,
      @Valid @RequestBody AdminController.GrantCreditsRequest req,
      @AuthenticationPrincipal Jwt principal,
      HttpServletRequest request) {
    UUID actingUserId = resolveUserId(principal, request);
    User.Role role = roleOf(principal, request);
    if (actingUserId == null || role != User.Role.ROOT_MASTER) {
      return ResponseEntity.status(403).build();
    }
    boolean ok = credits.grantByRoot(actingUserId, role, id, req.amount());
    return ok ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
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

  /** Papel resolvido do banco (STUB tokens não carregam a claim "role"). */
  private User.Role roleOf(Jwt principal, HttpServletRequest request) {
    UUID userId = resolveUserId(principal, request);
    if (userId == null) return null;
    return users.findById(userId).map(User::getRole).filter(r -> r != User.Role.USER).orElse(null);
  }
}
