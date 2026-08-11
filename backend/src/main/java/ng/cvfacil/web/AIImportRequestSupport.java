package ng.cvfacil.web;

import java.util.Set;
import ng.cvfacil.service.AIImportService;
import org.slf4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

/**
 * Validação e chamada ao AIImportService compartilhadas entre AIImportController
 * (produção) e LocalAIImportController (dev local).
 *
 * ANTES: os dois controllers duplicavam integralmente a validação de tipo/tamanho
 * de arquivo e o tratamento de erro — exatamente o tipo de lógica que, duplicada,
 * corre o risco de uma correção (ex.: um tipo MIME adicional bloqueado) ser
 * aplicada em um profile e esquecida no outro.
 */
final class AIImportRequestSupport {

  private static final long MAX_BYTES = 10 * 1024 * 1024; // 10 MB
  private static final Set<String> ALLOWED_TYPES = Set.of(
      "application/pdf",
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      "text/plain"
  );

  private AIImportRequestSupport() {}

  static ResponseEntity<String> handle(
      AIImportService aiImportService, MultipartFile file, Logger log, String logPrefix) {

    if (!aiImportService.isConfigured()) {
      log.info("{} AI_API_KEY não configurada — retornando 501 para fallback client-side", logPrefix);
      return ResponseEntity.status(501).body("{\"error\":\"IA não configurada neste servidor\"}");
    }

    if (file == null || file.isEmpty()) {
      return ResponseEntity.badRequest().body("{\"error\":\"Arquivo não enviado\"}");
    }

    if (file.getSize() > MAX_BYTES) {
      return ResponseEntity.badRequest().body("{\"error\":\"Arquivo excede 10 MB\"}");
    }

    String ct = file.getContentType();
    String fn = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
    boolean typeOk = (ct != null && ALLOWED_TYPES.contains(ct))
        || fn.endsWith(".pdf") || fn.endsWith(".docx") || fn.endsWith(".txt");
    if (!typeOk) {
      return ResponseEntity.badRequest()
          .body("{\"error\":\"Tipo de arquivo não suportado. Use PDF, DOCX ou TXT.\"}");
    }

    try {
      log.info("{} Importando '{}' ({} bytes)", logPrefix, fn, file.getSize());
      String resultJson = aiImportService.importResume(file.getBytes(), fn);
      return ResponseEntity.ok(resultJson);
    } catch (IllegalArgumentException e) {
      log.warn("{} Arquivo inválido: {}", logPrefix, e.getMessage());
      return ResponseEntity.badRequest()
          .body("{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
    } catch (Exception e) {
      log.error("{} Erro ao processar '{}': {}", logPrefix, fn, e.getMessage(), e);
      return ResponseEntity.internalServerError()
          .body("{\"error\":\"Erro ao processar o arquivo. Tente novamente ou cole o texto manualmente.\"}");
    }
  }
}
