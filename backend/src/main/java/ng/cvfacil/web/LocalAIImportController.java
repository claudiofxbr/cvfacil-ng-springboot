package ng.cvfacil.web;

import ng.cvfacil.service.AIImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Endpoint de importação de currículo via IA — ativo somente no profile "local".
 *
 * <p>Idêntico ao AIImportController (mesma lógica em AIImportRequestSupport), mas sem verificação
 * de JWT (LocalSecurityConfig já abre todos os endpoints com .anyRequest().permitAll() em dev
 * local).
 *
 * <p>Para ativar a IA em dev local, preencha cvfacil.ai.api-key em application-local.yml (provider
 * padrao ja configurado como "gemini").
 */
@RestController
@RequestMapping("/api/ai-import")
@Profile("local")
public class LocalAIImportController {

  private static final Logger log = LoggerFactory.getLogger(LocalAIImportController.class);

  private final AIImportService aiImportService;

  public LocalAIImportController(AIImportService aiImportService) {
    this.aiImportService = aiImportService;
  }

  @PostMapping(
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> importResume(@RequestParam("file") MultipartFile file) {
    return AIImportRequestSupport.handle(aiImportService, file, log, "[AI Import local]");
  }
}
