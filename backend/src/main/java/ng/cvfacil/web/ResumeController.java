package ng.cvfacil.web;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import ng.cvfacil.dto.ResumeDtos.ResumeRequest;
import ng.cvfacil.dto.ResumeDtos.ResumeView;
import ng.cvfacil.service.CreditService;
import ng.cvfacil.service.ResumeService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints de currículos — ativo apenas nos perfis de produção (@Profile("!local")).
 *
 * <p>Segurança: userId é extraído exclusivamente do JWT RS256 validado pelo Spring Security. A
 * lógica de cifragem/descoberta de propriedade vive em {@link ResumeService}, compartilhada com
 * {@link LocalResumeController} — este controller só resolve o userId e delega.
 */
@RestController
@RequestMapping("/api/resumes")
@Profile("!local")
public class ResumeController {

  private final ResumeService service;

  public ResumeController(ResumeService service) {
    this.service = service;
  }

  @GetMapping
  public ResponseEntity<List<ResumeView>> list(@AuthenticationPrincipal Jwt principal) {
    UUID userId = resolveUserId(principal);
    if (userId == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(service.list(userId));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ResumeView> get(
      @PathVariable UUID id, @AuthenticationPrincipal Jwt principal) {
    UUID userId = resolveUserId(principal);
    if (userId == null) return ResponseEntity.status(401).build();
    return service
        .get(id, userId)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @PostMapping
  public ResponseEntity<ResumeView> create(
      @Valid @RequestBody ResumeRequest req, @AuthenticationPrincipal Jwt principal) {
    UUID userId = resolveUserId(principal);
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
      @AuthenticationPrincipal Jwt principal) {
    UUID userId = resolveUserId(principal);
    if (userId == null) return ResponseEntity.status(401).build();
    return service
        .update(id, userId, req)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @PathVariable UUID id, @AuthenticationPrincipal Jwt principal) {
    UUID userId = resolveUserId(principal);
    if (userId == null) return ResponseEntity.status(401).build();
    return service.delete(id, userId)
        ? ResponseEntity.noContent().build()
        : ResponseEntity.notFound().build();
  }

  /** Extrai userId do JWT RS256 validado pelo Spring Security (única fonte confiável). */
  UUID resolveUserId(Jwt principal) {
    if (principal == null) return null;
    try {
      return UUID.fromString(principal.getSubject());
    } catch (Exception ignored) {
      return null;
    }
  }
}
