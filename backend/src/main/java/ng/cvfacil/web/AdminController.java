package ng.cvfacil.web;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import ng.cvfacil.domain.User;
import ng.cvfacil.service.AdminService;
import ng.cvfacil.service.CreditService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints administrativos — ativo apenas em produção (@Profile("!local")).
 * O filtro de segurança (SecurityConfig) já exige ROLE_ADMIN ou ROLE_ROOT_MASTER
 * para qualquer rota sob /api/admin/**; aqui só distinguimos as ações
 * exclusivas do Root (excluir usuário) das demais (Admin + Root).
 *
 * Em produção, este controller é servido em subdomínio admin.cvfacil.ng (PRD §4.4).
 */
@RestController
@RequestMapping("/api/admin")
@Profile("!local")
public class AdminController {

  private final AdminService service;
  private final CreditService credits;

  public AdminController(AdminService service, CreditService credits) {
    this.service = service;
    this.credits = credits;
  }

  @GetMapping("/stats")
  public ResponseEntity<Stats> stats() {
    return ResponseEntity.ok(new Stats(service.countUsers()));
  }

  @GetMapping("/users")
  public ResponseEntity<List<AdminService.UserAdminView>> users() {
    return ResponseEntity.ok(service.listUsers());
  }

  @DeleteMapping("/users/{id}")
  public ResponseEntity<Void> deleteUser(
      @PathVariable UUID id, @AuthenticationPrincipal Jwt principal) {
    UUID actingUserId = resolveUserId(principal);
    User.Role actingRole = roleOf(principal);
    if (actingUserId == null || actingRole != User.Role.ROOT_MASTER) {
      return ResponseEntity.status(403).build();
    }
    return service.deleteUser(actingUserId, actingRole, id)
        ? ResponseEntity.noContent().build()
        : ResponseEntity.notFound().build();
  }

  /** Concessão manual de créditos — SOMENTE Root (nunca Admin, ver PRD/RBAC). */
  @PostMapping("/users/{id}/credits")
  public ResponseEntity<Void> grantCredits(
      @PathVariable UUID id,
      @Valid @RequestBody GrantCreditsRequest req,
      @AuthenticationPrincipal Jwt principal) {
    UUID actingUserId = resolveUserId(principal);
    User.Role actingRole = roleOf(principal);
    if (actingUserId == null || actingRole != User.Role.ROOT_MASTER) {
      return ResponseEntity.status(403).build();
    }
    boolean ok = credits.grantByRoot(actingUserId, actingRole, id, req.amount());
    return ok ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
  }

  public record GrantCreditsRequest(@Min(1) int amount) {}

  static UUID resolveUserId(Jwt principal) {
    if (principal == null) return null;
    try {
      return UUID.fromString(principal.getSubject());
    } catch (Exception ignored) {
      return null;
    }
  }

  static User.Role roleOf(Jwt principal) {
    if (principal == null) return null;
    try {
      return User.Role.valueOf(principal.getClaimAsString("role"));
    } catch (Exception ignored) {
      return null;
    }
  }

  public record Stats(long totalUsers) {}
}
