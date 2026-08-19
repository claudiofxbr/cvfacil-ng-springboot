package ng.cvfacil.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Servico de importacao de curriculos via Inteligencia Artificial.
 *
 * <p>PIPELINE: 1. Extracao de texto -> PDFBox (PDF) | Apache POI (DOCX) | UTF-8 plain (TXT) 2.
 * Fallback OCR -> Tesseract 5 via tess4j (PDFs escaneados sem camada de texto) 3. Chamada ao LLM ->
 * OpenAI Chat Completions API, Anthropic Messages API ou Google Gemini generateContent API 4.
 * Normalizacao JSON -> garante schema exato esperado pelo editor do CVFacil.NG
 *
 * <p>SEGURANCA: - A API key NUNCA e exposta ao frontend. - O texto do curriculo e enviado
 * diretamente ao LLM e nao e armazenado. - Timeout de 90 s; erros de rede retornam excecao
 * controlada.
 *
 * <p>FALLBACK: - Quando isConfigured() == false, o controller retorna HTTP 501 e o frontend cai no
 * parser client-side (parseResumeText.js).
 *
 * <p>OCR (PDFs escaneados): - Quando PDFBox retorna texto vazio ou muito curto (< 80 chars), o
 * servico detecta que o PDF nao tem camada de texto e aciona o Tesseract OCR. - O Tesseract
 * renderiza cada pagina do PDF como imagem (300 DPI) e extrai o texto. - Requer:
 * cvfacil.ocr.enabled=true + Tesseract 5 instalado no SO. - Instalacao: Ubuntu -> sudo apt install
 * tesseract-ocr tesseract-ocr-por Windows -> https://github.com/UB-Mannheim/tesseract/wiki macOS ->
 * brew install tesseract tesseract-lang
 *
 * <p>PROVEDORES SUPORTADOS: - OpenAI (padrao): AI_PROVIDER=openai
 * AI_API_BASE_URL=https://api.openai.com/v1 - Anthropic Claude: AI_PROVIDER=anthropic
 * AI_API_BASE_URL=https://api.anthropic.com/v1 - Google Gemini: AI_PROVIDER=gemini
 * AI_API_BASE_URL=https://generativelanguage.googleapis.com/v1beta - Qualquer provider
 * OpenAI-compatible (Groq, Together, etc.): defina AI_API_BASE_URL.
 */
@Service
public class AIImportService {

  private static final Logger log = LoggerFactory.getLogger(AIImportService.class);

  /**
   * Limiar minimo de caracteres extraidos pelo PDFBox. Se o texto extraido tiver menos que este
   * valor (apos trim), o PDF e considerado escaneado (sem camada de texto) e o OCR e acionado como
   * fallback.
   */
  private static final int MIN_TEXT_LENGTH_FOR_SKIP_OCR = 80;

  // ── System prompt: instrui o LLM a extrair dados no schema do CVFacil.NG ──
  private static final String SYSTEM_PROMPT =
      "Voce e um parser especializado em curriculos. Analise o texto fornecido e extraia "
          + "todas as informacoes estruturadas em portugues.\n\n"
          + "Retorne APENAS um objeto JSON valido, sem markdown, sem explicacoes, sem blocos de codigo.\n\n"
          + "O JSON deve seguir EXATAMENTE este schema:\n"
          + "{\n"
          + "  \"fullName\": \"Nome completo do candidato\",\n"
          + "  \"headline\": \"Titulo profissional ou cargo atual\",\n"
          + "  \"email\": \"email@exemplo.com\",\n"
          + "  \"phone\": \"(XX) XXXXX-XXXX\",\n"
          + "  \"location\": \"Cidade, Estado\",\n"
          + "  \"website\": \"URL do LinkedIn ou site pessoal\",\n"
          + "  \"summary\": \"Paragrafo de resumo profissional (max. 3 linhas)\",\n"
          + "  \"skills\": [{\"name\": \"Habilidade\", \"pct\": 80}],\n"
          + "  \"experience\": [{\n"
          + "    \"role\": \"Cargo\",\n"
          + "    \"company\": \"Empresa\",\n"
          + "    \"period\": \"2020 - 2023\",\n"
          + "    \"bullets\": [\"Responsabilidade 1\", \"Conquista 2\"]\n"
          + "  }],\n"
          + "  \"education\": [{\"degree\": \"Bacharelado em...\", \"school\": \"Universidade\", \"period\": \"2016 - 2020\"}],\n"
          + "  \"languages\": [{\"name\": \"Portugues\", \"level\": \"Nativo\"}],\n"
          + "  \"hobbies\": [\"Hobby 1\"]\n"
          + "}\n\n"
          + "REGRAS OBRIGATORIAS:\n"
          + "1. Extraia SOMENTE informacoes presentes no texto. NUNCA invente ou assuma dados.\n"
          + "2. skills[].pct - estime a proficiencia (60-95) pelo contexto:\n"
          + "   - especialista / avancado / expert -> 88-95\n"
          + "   - proficiente / solido / bom dominio -> 78-85\n"
          + "   - intermediario / conhecimento em -> 68-75\n"
          + "   - basico / familiaridade -> 60-65\n"
          + "   - nivel nao especificado -> 75\n"
          + "3. experience[].bullets - extraia responsabilidades/conquistas como array de strings "
          + "NAO-VAZIAS. Maximo 10 itens por experiencia.\n"
          + "4. experience[] - ordene do emprego mais RECENTE para o mais antigo.\n"
          + "5. Campos sem dados: use \"\" (string vazia) ou [] (array vazio). NUNCA use null.\n"
          + "6. phone - formato brasileiro preferencialmente: (11) 99999-9999.\n"
          + "7. languages[].level - use exatamente um destes: Nativo, Fluente, Avancado, Intermediario, Basico.\n"
          + "8. Retorne APENAS o JSON. Nenhum texto antes ou depois.";

  // ── Configuracao LLM ────────────────────────────────────────────────────────
  @Value("${cvfacil.ai.provider:openai}")
  private String provider;

  @Value("${cvfacil.ai.base-url:https://api.openai.com/v1}")
  private String baseUrl;

  @Value("${cvfacil.ai.api-key:}")
  private String apiKey;

  @Value("${cvfacil.ai.model:gpt-4o-mini}")
  private String model;

  // ── Configuracao OCR (Tesseract) ────────────────────────────────────────────
  /**
   * Habilita o fallback OCR para PDFs escaneados. Requer Tesseract 5 instalado no sistema
   * operacional. Padrao: false (OCR desabilitado por seguranca, evitar crash se Tesseract nao
   * estiver instalado).
   */
  @Value("${cvfacil.ocr.enabled:false}")
  private boolean ocrEnabled;

  /**
   * Caminho para o diretorio tessdata do Tesseract. Vazio = usa o padrao do sistema (funciona
   * quando Tesseract esta no PATH). Exemplos: Linux : /usr/share/tesseract-ocr/5/tessdata Windows:
   * C:/Program Files/Tesseract-OCR/tessdata macOS : /usr/local/share/tessdata
   */
  @Value("${cvfacil.ocr.tessdata-path:}")
  private String tessdataPath;

  /** Idiomas do OCR. "por+eng" = portugues + ingles (recomendado para curriculos brasileiros). */
  @Value("${cvfacil.ocr.language:por+eng}")
  private String ocrLanguage;

  /**
   * Resolucao (DPI) das imagens geradas pelo PDFBox para o OCR. 300 DPI e o minimo recomendado. Use
   * 400 para PDFs com fontes muito pequenas.
   */
  @Value("${cvfacil.ocr.dpi:300}")
  private float ocrDpi;

  private final ObjectMapper mapper = new ObjectMapper();

  private final HttpClient httpClient =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(30))
          .version(HttpClient.Version.HTTP_2)
          .build();

  // ── API publica ─────────────────────────────────────────────────────────────

  /**
   * Provedor de IA externo ativo (openai/anthropic/gemini) — usado para informar o titular dos
   * dados, ao pedir consentimento, para onde o conteudo do curriculo sera enviado (LGPD Art. 9 /
   * GDPR Art. 44-49, transferencia internacional).
   */
  public String getProvider() {
    return provider;
  }

  /** Retorna true quando a chave de API esta configurada. */
  public boolean isConfigured() {
    return apiKey != null && !apiKey.isBlank();
  }

  /** Retorna true quando o OCR esta habilitado e configurado. */
  public boolean isOcrEnabled() {
    return ocrEnabled;
  }

  /**
   * Pipeline completo: extrai texto do arquivo e envia ao LLM. Para PDFs escaneados (sem camada de
   * texto), aciona OCR automaticamente se habilitado.
   *
   * @param content conteudo binario do arquivo (PDF, DOCX ou TXT)
   * @param filename nome do arquivo (usado para detectar o tipo)
   * @return JSON estruturado do curriculo, pronto para o editor
   */
  public String importResume(byte[] content, String filename) throws Exception {
    String rawText = extractText(content, filename.toLowerCase());
    if (rawText.isBlank()) {
      throw new IllegalArgumentException(
          "Nao foi possivel extrair texto do arquivo. "
              + (filename.toLowerCase().endsWith(".pdf") && !ocrEnabled
                  ? "O PDF pode estar escaneado. Habilite o OCR no servidor (cvfacil.ocr.enabled=true) "
                      + "e instale o Tesseract para processar PDFs escaneados."
                  : "Certifique-se de que o arquivo nao esta protegido por senha ou corrompido."));
    }
    log.debug("[AI Import] Texto extraido: {} caracteres de '{}'", rawText.length(), filename);
    String aiJson = parseWithAI(rawText);
    return normalizeResumeJson(aiJson);
  }

  // ── Extracao de texto ───────────────────────────────────────────────────────

  /**
   * Extrai texto puro do arquivo binario. PDF -> Apache PDFBox (+ OCR Tesseract como fallback para
   * PDFs escaneados) DOCX -> Apache POI TXT -> UTF-8 decode
   */
  String extractText(byte[] content, String filename) throws Exception {
    if (filename.endsWith(".pdf")) return extractFromPdf(content);
    if (filename.endsWith(".docx")) return extractFromDocx(content);
    return new String(content, StandardCharsets.UTF_8);
  }

  /**
   * Extrai texto de um PDF.
   *
   * <p>Fluxo: 1. PDFBox extrai texto da camada de texto digital (rapido, preciso) 2. Se o texto for
   * muito curto (PDF escaneado sem camada de texto): a. Se OCR habilitado: Tesseract renderiza cada
   * pagina como imagem e extrai o texto b. Se OCR desabilitado: loga aviso e retorna string vazia
   */
  private String extractFromPdf(byte[] content) throws Exception {
    // PDFBox 3.x: Loader.loadPDF(byte[]) substitui PDDocument.load(InputStream)
    try (PDDocument doc = Loader.loadPDF(content)) {
      if (doc.isEncrypted()) {
        throw new IllegalArgumentException(
            "PDF protegido por senha - remova a protecao e tente novamente.");
      }

      // Tentativa 1: extrair texto da camada digital (PDFBox)
      PDFTextStripper stripper = new PDFTextStripper();
      stripper.setSortByPosition(true); // preserva ordem visual (coluna esquerda -> direita)
      String text = stripper.getText(doc).trim();

      if (text.length() >= MIN_TEXT_LENGTH_FOR_SKIP_OCR) {
        // PDF digital com camada de texto — nenhum OCR necessario
        log.debug("[AI Import] PDFBox extraiu {} chars (PDF digital)", text.length());
        return text;
      }

      // Tentativa 2: OCR via Tesseract (PDF escaneado ou sem camada de texto)
      if (ocrEnabled) {
        log.info(
            "[AI Import] PDFBox retornou {} chars — PDF parece escaneado. "
                + "Acionando OCR (Tesseract, idioma={}, dpi={})",
            text.length(),
            ocrLanguage,
            (int) ocrDpi);
        return extractWithOcr(doc);
      }

      // OCR desabilitado e PDF sem texto — logar orientacao clara
      log.warn(
          "[AI Import] PDF sem camada de texto e OCR desabilitado. "
              + "Para processar PDFs escaneados, habilite cvfacil.ocr.enabled=true "
              + "e instale Tesseract: sudo apt install tesseract-ocr tesseract-ocr-por");
      return "";
    }
  }

  /**
   * Extrai texto de um PDF escaneado via OCR (Tesseract 5).
   *
   * <p>Cada pagina do PDF e renderizada como imagem em escala de cinza a 300 DPI e processada pelo
   * Tesseract. O texto de todas as paginas e concatenado.
   *
   * <p>Requer: Tesseract 5 instalado no SO com os pacotes de idioma configurados.
   */
  private String extractWithOcr(PDDocument doc) throws Exception {
    // Modo headless: essencial para servidores sem display (Linux server)
    System.setProperty("java.awt.headless", "true");

    // Configurar Tesseract
    Tesseract tesseract;
    try {
      tesseract = new Tesseract();
    } catch (UnsatisfiedLinkError | ExceptionInInitializerError e) {
      log.error(
          "[AI Import] tess4j nao conseguiu carregar as bibliotecas nativas. "
              + "Causa: {}. "
              + "No Windows: as DLLs (libtesseract-5.dll / liblept-5.dll) sao extraidas "
              + "automaticamente pelo tess4j para um diretorio temporario — isso nao requer "
              + "instalacao do Tesseract. Verifique se o antivirus nao esta bloqueando a extracao "
              + "de DLLs de JARs. Caminho tessdata configurado: '{}'",
          e.getMessage(),
          tessdataPath.isBlank() ? "(padrao do sistema)" : tessdataPath);
      throw new IllegalStateException(
          "OCR indisponivel: falha ao carregar bibliotecas nativas do Tesseract. "
              + "Verifique os logs do servidor para detalhes.",
          e);
    }

    // Definir caminho do tessdata (usa padrao do sistema se nao configurado)
    if (tessdataPath != null && !tessdataPath.isBlank()) {
      tesseract.setDatapath(tessdataPath);
    }
    tesseract.setLanguage(ocrLanguage);
    tesseract.setPageSegMode(3); // PSM_AUTO — segmentacao automatica de pagina
    tesseract.setOcrEngineMode(1); // OEM_LSTM_ONLY — rede neural (mais preciso)

    PDFRenderer renderer = new PDFRenderer(doc);
    StringBuilder sb = new StringBuilder();
    int pages = doc.getNumberOfPages();

    for (int i = 0; i < pages; i++) {
      // Renderizar pagina como imagem em escala de cinza (menor memoria, mesma precisao)
      BufferedImage img;
      try {
        img = renderer.renderImageWithDPI(i, ocrDpi, ImageType.GRAY);
      } catch (Exception renderEx) {
        log.warn(
            "[AI Import] Falha ao renderizar pagina {}/{}: {}",
            i + 1,
            pages,
            renderEx.getMessage());
        continue;
      }

      try {
        String pageText = tesseract.doOCR(img);
        if (pageText != null && !pageText.isBlank()) {
          sb.append(pageText).append("\n");
        }
        log.debug(
            "[AI Import] OCR pagina {}/{}: {} chars extraidos",
            i + 1,
            pages,
            pageText != null ? pageText.trim().length() : 0);
      } catch (TesseractException e) {
        log.warn("[AI Import] OCR falhou na pagina {}/{}: {}", i + 1, pages, e.getMessage());
        // Continua para as proximas paginas mesmo se uma falhar
      } catch (UnsatisfiedLinkError | ExceptionInInitializerError e) {
        // Erro de DLL durante OCR (raro — geralmente ocorre na inicializacao do Tesseract acima)
        log.error(
            "[AI Import] Erro de biblioteca nativa durante OCR na pagina {}: {}",
            i + 1,
            e.getMessage());
        throw new IllegalStateException("OCR interrompido: falha de biblioteca nativa.", e);
      }
    }

    String result = sb.toString().trim();
    log.info(
        "[AI Import] OCR concluido: {} chars extraidos de {} pagina(s)", result.length(), pages);
    return result;
  }

  private String extractFromDocx(byte[] content) throws Exception {
    try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(content));
        XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
      return extractor.getText();
    }
  }

  // ── Chamada ao LLM ──────────────────────────────────────────────────────────

  /**
   * Envia o texto ao LLM configurado e retorna o JSON bruto da resposta. Suporta OpenAI (padrao) e
   * Anthropic.
   */
  String parseWithAI(String rawText) throws Exception {
    // Limita o texto a ~16 000 caracteres para nao exceder o contexto do modelo
    String truncated = rawText.length() > 16_000 ? rawText.substring(0, 16_000) : rawText;
    if ("anthropic".equalsIgnoreCase(provider)) return callAnthropic(truncated);
    if ("gemini".equalsIgnoreCase(provider)) return callGemini(truncated);
    return callOpenAI(truncated);
  }

  private String callOpenAI(String text) throws Exception {
    ArrayNode messages = mapper.createArrayNode();
    messages.add(mapper.createObjectNode().put("role", "system").put("content", SYSTEM_PROMPT));
    messages.add(mapper.createObjectNode().put("role", "user").put("content", text));

    ObjectNode body = mapper.createObjectNode();
    body.put("model", model.isBlank() ? "gpt-4o-mini" : model);
    body.put("temperature", 0.1);
    body.put("max_tokens", 4000);
    body.set("messages", messages);
    // response_format json_object garante que o modelo retorne JSON valido
    body.set("response_format", mapper.createObjectNode().put("type", "json_object"));

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/chat/completions"))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(90))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
            .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() != 200) {
      log.error("[AI Import] OpenAI retornou HTTP {}: {}", response.statusCode(), response.body());
      throw new RuntimeException(
          "Erro na API OpenAI (HTTP "
              + response.statusCode()
              + "). Verifique a API key e o modelo configurado.");
    }

    return mapper.readTree(response.body()).at("/choices/0/message/content").asText();
  }

  private String callAnthropic(String text) throws Exception {
    ArrayNode messages = mapper.createArrayNode();
    messages.add(mapper.createObjectNode().put("role", "user").put("content", text));

    ObjectNode body = mapper.createObjectNode();
    body.put("model", model.isBlank() ? "claude-3-5-haiku-20241022" : model);
    body.put("max_tokens", 4000);
    body.put("system", SYSTEM_PROMPT);
    body.set("messages", messages);

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/messages"))
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(90))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
            .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() != 200) {
      log.error(
          "[AI Import] Anthropic retornou HTTP {}: {}", response.statusCode(), response.body());
      throw new RuntimeException(
          "Erro na API Anthropic (HTTP "
              + response.statusCode()
              + "). Verifique a API key e o modelo configurado.");
    }

    return mapper.readTree(response.body()).at("/content/0/text").asText();
  }

  private String callGemini(String text) throws Exception {
    // "gemini-2.5-flash" fixo retorna 404 para chaves de contas novas (modelo com
    // acesso restrito); o alias "gemini-flash-latest" resolve para o Flash vigente
    // e funciona para qualquer chave — usado como fallback mais seguro.
    String effectiveModel = model.isBlank() ? "gemini-flash-latest" : model;
    // base-url deve apontar para a raiz da API (ex.:
    // https://generativelanguage.googleapis.com/v1beta),
    // sem o segmento /models/{model}:generateContent — este metodo o monta.
    String url = baseUrl.replaceAll("/+$", "") + "/models/" + effectiveModel + ":generateContent";

    ObjectNode part = mapper.createObjectNode().put("text", text);
    ObjectNode content = mapper.createObjectNode();
    content.set("parts", mapper.createArrayNode().add(part));
    ArrayNode contents = mapper.createArrayNode().add(content);

    ObjectNode systemInstruction = mapper.createObjectNode();
    systemInstruction.set(
        "parts",
        mapper.createArrayNode().add(mapper.createObjectNode().put("text", SYSTEM_PROMPT)));

    ObjectNode generationConfig = mapper.createObjectNode();
    generationConfig.put("temperature", 0.1);
    generationConfig.put("maxOutputTokens", 4000);
    generationConfig.put("responseMimeType", "application/json");

    ObjectNode body = mapper.createObjectNode();
    body.set("contents", contents);
    body.set("systemInstruction", systemInstruction);
    body.set("generationConfig", generationConfig);

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("x-goog-api-key", apiKey)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(90))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
            .build();

    // Gemini free-tier retorna 503 "UNAVAILABLE" (modelo sobrecarregado) e 429 "RESOURCE_EXHAUSTED"
    // com frequencia — a propria Google recomenda retry. Sem isso, qualquer pico de demanda no lado
    // deles derruba a importacao mesmo com chave/modelo corretos.
    int maxAttempts = 3;
    HttpResponse<String> response = null;
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      int status = response.statusCode();
      if (status == 200) break;
      boolean transient_ = status == 503 || status == 429;
      if (!transient_ || attempt == maxAttempts) {
        log.error("[AI Import] Gemini retornou HTTP {}: {}", status, response.body());
        throw new RuntimeException(
            transient_
                ? "O modelo Gemini esta temporariamente sobrecarregado. Tente novamente em instantes."
                : "Erro na API Gemini (HTTP "
                    + status
                    + "). Verifique a API key e o modelo configurado.");
      }
      log.warn(
          "[AI Import] Gemini retornou HTTP {} (tentativa {}/{}) — retentando em {}ms",
          status,
          attempt,
          maxAttempts,
          1000L << (attempt - 1));
      try {
        Thread.sleep(1000L << (attempt - 1)); // 1s, 2s
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("Importacao interrompida.", e);
      }
    }

    return mapper.readTree(response.body()).at("/candidates/0/content/parts/0/text").asText();
  }

  // ── Normalizacao do JSON ────────────────────────────────────────────────────

  /**
   * Normaliza a resposta do LLM para corresponder EXATAMENTE ao schema do editor.
   *
   * <p>Garante: - Campo "photo" presente (string vazia - foto nao e extraida de texto) -
   * experience[].bullets com exatamente 10 elementos (preenchidos com "" quando necessario) -
   * Nenhum campo null (substituido por "" ou [])
   */
  String normalizeResumeJson(String aiJson) throws Exception {
    // Extrai o JSON mesmo que o LLM tenha incluido markdown fences
    String cleaned = aiJson.strip();
    if (cleaned.startsWith("```")) {
      int start = cleaned.indexOf('\n') + 1;
      int end = cleaned.lastIndexOf("```");
      if (end > start) cleaned = cleaned.substring(start, end).strip();
    }

    ObjectNode root = (ObjectNode) mapper.readTree(cleaned);

    // Campos de topo: garantir que nao sejam null
    for (String field :
        new String[] {"fullName", "headline", "email", "phone", "location", "website", "summary"}) {
      if (!root.has(field) || root.get(field).isNull()) root.put(field, "");
    }
    if (!root.has("photo") || root.get("photo").isNull()) root.put("photo", "");

    // skills: garantir array; pct padrao 75 quando ausente
    if (!root.has("skills") || root.get("skills").isNull()) {
      root.set("skills", mapper.createArrayNode());
    }
    for (var skill : root.withArray("skills")) {
      ObjectNode s = (ObjectNode) skill;
      if (!s.has("pct") || s.get("pct").isNull()) s.put("pct", 75);
      if (!s.has("name") || s.get("name").isNull()) s.put("name", "");
    }

    // experience: bullets com exatamente 10 posicoes (editor espera array de 10)
    if (!root.has("experience") || root.get("experience").isNull()) {
      root.set("experience", mapper.createArrayNode());
    }
    for (var entry : root.withArray("experience")) {
      ObjectNode exp = (ObjectNode) entry;
      if (!exp.has("role") || exp.get("role").isNull()) exp.put("role", "");
      if (!exp.has("company") || exp.get("company").isNull()) exp.put("company", "");
      if (!exp.has("period") || exp.get("period").isNull()) exp.put("period", "");

      ArrayNode bullets = mapper.createArrayNode();
      if (exp.has("bullets") && !exp.get("bullets").isNull()) {
        for (var b : exp.withArray("bullets")) {
          String t = b.asText("").strip();
          if (!t.isEmpty()) bullets.add(t);
          if (bullets.size() == 10) break;
        }
      }
      while (bullets.size() < 10) bullets.add(""); // preenche ate 10
      exp.set("bullets", bullets);
    }

    // education
    if (!root.has("education") || root.get("education").isNull()) {
      root.set("education", mapper.createArrayNode());
    }
    for (var edu : root.withArray("education")) {
      ObjectNode e = (ObjectNode) edu;
      if (!e.has("degree") || e.get("degree").isNull()) e.put("degree", "");
      if (!e.has("school") || e.get("school").isNull()) e.put("school", "");
      if (!e.has("period") || e.get("period").isNull()) e.put("period", "");
    }

    // languages
    if (!root.has("languages") || root.get("languages").isNull()) {
      root.set("languages", mapper.createArrayNode());
    }
    for (var lang : root.withArray("languages")) {
      ObjectNode l = (ObjectNode) lang;
      if (!l.has("name") || l.get("name").isNull()) l.put("name", "");
      if (!l.has("level") || l.get("level").isNull()) l.put("level", "");
    }

    // hobbies
    if (!root.has("hobbies") || root.get("hobbies").isNull()) {
      root.set("hobbies", mapper.createArrayNode());
    }

    return mapper.writeValueAsString(root);
  }
}
