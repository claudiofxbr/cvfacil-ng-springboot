package ng.cvfacil.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import ng.cvfacil.service.AIImportService;
import ng.cvfacil.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Endpoint de importação de currículo via IA — ativo somente em produção (@Profile("!local")).
 *
 * <p>POST /api/ai-import Content-Type: multipart/form-data Param: file (PDF, DOCX ou TXT — max 10
 * MB)
 *
 * <p>Fluxo: 1. Valida JWT (userId extraído do token — nunca do cliente) 2. Delega a
 * AIImportRequestSupport: validação + AIImportService (extração via PDFBox/POI + chamada ao LLM) —
 * lógica compartilhada com LocalAIImportController 3. Retorna JSON estruturado do currículo pronto
 * para o editor
 *
 * <p>SEGURANÇA: - JWT validado pelo Spring Security antes de chegar neste método - API key do LLM
 * NUNCA é exposta ao frontend - Arquivo não é salvo em disco (processado em memória)
 */
@RestController
@RequestMapping("/api/ai-import")
@Profile("!local")
public class AIImportController {

  private static final Logger log = LoggerFactory.getLogger(AIImportController.class);

  private final AIImportService aiImportService;
  private final AuditService audit;

  public AIImportController(AIImportService aiImportService, AuditService audit) {
    this.aiImportService = aiImportService;
    this.audit = audit;
  }

  @PostMapping(
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> importResume(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "aiConsent", defaultValue = "false") boolean aiConsent,
      @AuthenticationPrincipal Jwt principal,
      HttpServletRequest http) {

    if (principal == null) {
      return ResponseEntity.status(401).body("{\"error\":\"Autenticação necessária\"}");
    }

    UUID userId = resolveUserId(principal);
    return AIImportRequestSupport.handle(
        aiImportService,
        file,
        aiConsent,
        log,
        "[AI Import]",
        audit,
        userId,
        http.getRemoteAddr(),
        http.getHeader("User-Agent"));
  }

  /** Extrai userId do JWT RS256 validado pelo Spring Security (única fonte confiável). */
  private UUID resolveUserId(Jwt principal) {
    try {
      return UUID.fromString(principal.getSubject());
    } catch (Exception ignored) {
      return null;
    }
  }
}
