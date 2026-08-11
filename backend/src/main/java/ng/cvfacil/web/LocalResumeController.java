package ng.cvfacil.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import ng.cvfacil.dto.ResumeDtos.ResumeRequest;
import ng.cvfacil.dto.ResumeDtos.ResumeView;
import ng.cvfacil.service.CreditService;
import ng.cvfacil.service.ResumeService;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Versão local de ResumeController — ativa apenas em @Profile("local").
 *
 * <p>Diferença vs. produção: aceita STUB tokens gerados por JwtService em dev,
 * extraindo o userId diretamente do header {@code Authorization: Bearer STUB_ACCESS.<userId>.*}.
 * Toda a lógica de CRUD/cifragem vive em {@link ResumeService}, compartilhada com
 * {@link ResumeController} — só a resolução de userId difere entre profiles.
 *
 * <p>IMPORTANTE: STUB tokens NUNCA chegam a produção. Este controller deve ser
 * eliminado quando o ambiente de dev migrar para RS256 completo.
 */
@RestController
@RequestMapping("/api/resumes")
@Profile("local")
@Primary
public class LocalResumeController {

  private final ResumeService service;

  public LocalResumeController(ResumeService service) {
    this.service = service;
  }

  @GetMapping
  public ResponseEntity<List<ResumeView>> list(
      @AuthenticationPrincipal Jwt principal, HttpServletRequest request) {
    UUID userId = resolveUserId(principal, request);
    if (userId == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(service.list(userId));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ResumeView> get(
      @PathVariable UUID id, @AuthenticationPrincipal Jwt principal, HttpServletRequest request) {
    UUID userId = resolveUserId(principal, request);
    if (userId == null) return ResponseEntity.status(401).build();
    return service.get(id, userId).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
  }

  @PostMapping
  public ResponseEntity<ResumeView> create(
      @Valid @RequestBody ResumeRequest req,
      @AuthenticationPrincipal Jwt principal,
      HttpServletRequest request) {
    UUID userId = resolveUserId(principal, request);
    if (userId == null) return ResponseEntity.status(401).build();
    try {
      return ResponseEntity.status(201).body(service.create(userId, req));
    } catch (CreditService.InsufficientCreditsException e) {
      return ResponseEntity.status(402).build(); // Payment Required — sem créditos
    }
  }

  @PutMapping("/{id}")
  public ResponseEntity<ResumeView> update(
      @PathVariable UUID id,
      @Valid @RequestBody ResumeRequest req,
      @AuthenticationPrincipal Jwt principal,
      HttpServletRequest request) {
    UUID userId = resolveUserId(principal, request);
    if (userId == null) return ResponseEntity.status(401).build();
    return service.update(id, userId, req).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @PathVariable UUID id, @AuthenticationPrincipal Jwt principal, HttpServletRequest request) {
    UUID userId = resolveUserId(principal, request);
    if (userId == null) return ResponseEntity.status(401).build();
    return service.delete(id, userId) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
  }

  private UUID resolveUserId(Jwt principal, HttpServletRequest request) {
    if (principal != null) {
      try {
        return UUID.fromString(principal.getSubject());
      } catch (Exception ignored) {
      }
    }
    // Aceita STUB token somente em dev local
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
