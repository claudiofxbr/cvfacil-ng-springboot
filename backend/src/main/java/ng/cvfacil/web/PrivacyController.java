package ng.cvfacil.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import ng.cvfacil.dto.PrivacyDtos.DeleteAccountRequest;
import ng.cvfacil.service.PrivacyService;
import ng.cvfacil.service.PrivacyService.DeleteResult;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Autoatendimento LGPD Art. 18 / GDPR Art. 15-20 — o próprio titular exportando ou apagando os seus
 * dados. userId vem exclusivamente do JWT validado, nunca de parâmetro de rota — não é possível
 * exportar/apagar a conta de outra pessoa por aqui (isso é AdminController).
 */
@RestController
@RequestMapping("/api/users/me")
@Profile("!local")
public class PrivacyController {

  private final PrivacyService service;

  public PrivacyController(PrivacyService service) {
    this.service = service;
  }

  @GetMapping("/export")
  public ResponseEntity<?> export(@AuthenticationPrincipal Jwt principal) {
    UUID userId = resolveUserId(principal);
    if (userId == null) return ResponseEntity.status(401).build();
    return service.export(userId).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
  }

  @DeleteMapping
  public ResponseEntity<?> deleteOwnAccount(
      @RequestBody(required = false) DeleteAccountRequest req,
      @AuthenticationPrincipal Jwt principal,
      HttpServletRequest http) {
    UUID userId = resolveUserId(principal);
    if (userId == null) return ResponseEntity.status(401).build();

    String password = req == null ? null : req.password();
    DeleteResult result =
        service.deleteOwnAccount(
            userId, password, http.getRemoteAddr(), http.getHeader("User-Agent"));

    return switch (result) {
      case OK -> ResponseEntity.noContent().build();
      case NOT_FOUND -> ResponseEntity.notFound().build();
      case WRONG_PASSWORD -> ResponseEntity.status(401).body(Map.of("error", "Senha incorreta"));
      case ROOT_MASTER_BLOCKED ->
          ResponseEntity.status(409)
              .body(
                  Map.of(
                      "error", "Contas Root não podem se autoexcluir. Transfira o papel antes."));
    };
  }

  /** Extrai userId do JWT RS256 validado pelo Spring Security (única fonte confiável). */
  private UUID resolveUserId(Jwt principal) {
    if (principal == null) return null;
    try {
      return UUID.fromString(principal.getSubject());
    } catch (Exception ignored) {
      return null;
    }
  }
}
