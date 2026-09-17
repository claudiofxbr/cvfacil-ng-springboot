package ng.cvfacil.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Testes unitários para AIImportService — cobre a lógica determinística (normalizeResumeJson), a
 * extração de texto de PDF/DOCX, e os caminhos de erro das chamadas ao provedor de IA
 * (callOpenAI/callAnthropic/callGemini), usando um HttpServer local (setHttpClientForTesting) em
 * vez de rede real — foi exatamente a ausência dessa cobertura que deixou passar em produção um bug
 * de resposta do Gemini bloqueada por safety/truncada virando 500 opaco em vez de mensagem
 * acionável (ver AIProviderException).
 *
 * <p>O caminho de OCR (extractWithOcr) não é exercitado: exige Tesseract nativo instalado
 * (tessdata) e não há fixture/config de teste para isso no projeto; o teste de extractFromPdf usa
 * texto suficientemente longo (> 80 chars) para permanecer no caminho "sem OCR" (PDFBox puro).
 */
class AIImportServiceTest {

  private final AIImportService service = new AIImportService();
  private final ObjectMapper mapper = new ObjectMapper();

  // ── normalizeResumeJson ──────────────────────────────────────────────────

  @Test
  void normalizeResumeJson_jsonCompleto_permaneceEquivalente() throws Exception {
    String input =
        "{"
            + "\"fullName\":\"Maria Silva\","
            + "\"headline\":\"Engenheira de Software\","
            + "\"email\":\"maria@exemplo.com\","
            + "\"phone\":\"(11) 99999-9999\","
            + "\"location\":\"Sao Paulo, SP\","
            + "\"website\":\"https://linkedin.com/in/maria\","
            + "\"summary\":\"Resumo profissional.\","
            + "\"skills\":[{\"name\":\"Java\",\"pct\":90}],"
            + "\"experience\":[{\"role\":\"Dev\",\"company\":\"Acme\",\"period\":\"2020-2023\","
            + "\"bullets\":[\"Fez X\",\"Fez Y\"]}],"
            + "\"education\":[{\"degree\":\"Bacharelado\",\"school\":\"USP\",\"period\":\"2016-2020\"}],"
            + "\"languages\":[{\"name\":\"Ingles\",\"level\":\"Fluente\"}],"
            + "\"hobbies\":[\"Xadrez\"]"
            + "}";

    String result = service.normalizeResumeJson(input);
    JsonNode root = mapper.readTree(result);

    assertThat(root.get("fullName").asText()).isEqualTo("Maria Silva");
    assertThat(root.get("phone").asText()).isEqualTo("(11) 99999-9999");
    assertThat(root.get("photo").asText()).isEqualTo("");
    assertThat(root.get("skills").get(0).get("pct").asInt()).isEqualTo(90);
    assertThat(root.get("experience").get(0).get("bullets")).hasSize(10);
    assertThat(root.get("experience").get(0).get("bullets").get(0).asText()).isEqualTo("Fez X");
    assertThat(root.get("experience").get(0).get("bullets").get(1).asText()).isEqualTo("Fez Y");
    assertThat(root.get("experience").get(0).get("bullets").get(2).asText()).isEqualTo("");
    assertThat(root.get("hobbies").get(0).asText()).isEqualTo("Xadrez");
  }

  @Test
  void normalizeResumeJson_camposAusentesOuNull_viramStringOuArrayVazio() throws Exception {
    String input =
        "{\"fullName\":\"Joao\",\"summary\":null,\"phone\":null}"; // sem os demais campos

    String result = service.normalizeResumeJson(input);
    JsonNode root = mapper.readTree(result);

    assertThat(root.get("fullName").asText()).isEqualTo("Joao");
    assertThat(root.get("summary").asText()).isEqualTo("");
    assertThat(root.get("phone").asText()).isEqualTo("");
    assertThat(root.get("headline").asText()).isEqualTo("");
    assertThat(root.get("email").asText()).isEqualTo("");
    assertThat(root.get("location").asText()).isEqualTo("");
    assertThat(root.get("website").asText()).isEqualTo("");
    assertThat(root.get("photo").asText()).isEqualTo("");
    assertThat(root.get("skills")).isEmpty();
    assertThat(root.get("experience")).isEmpty();
    assertThat(root.get("education")).isEmpty();
    assertThat(root.get("languages")).isEmpty();
    assertThat(root.get("hobbies")).isEmpty();
    // nenhum campo de topo deve permanecer nulo
    root.fields().forEachRemaining(e -> assertThat(e.getValue().isNull()).isFalse());
  }

  @Test
  void normalizeResumeJson_bulletsComMenosDe10_preenchidosComStringVazia() throws Exception {
    String input =
        "{\"experience\":[{\"role\":\"Dev\",\"company\":\"Acme\",\"period\":\"2020\","
            + "\"bullets\":[\"Uma tarefa\"]}]}";

    String result = service.normalizeResumeJson(input);
    JsonNode bullets = mapper.readTree(result).get("experience").get(0).get("bullets");

    assertThat(bullets).hasSize(10);
    assertThat(bullets.get(0).asText()).isEqualTo("Uma tarefa");
    for (int i = 1; i < 10; i++) {
      assertThat(bullets.get(i).asText()).isEqualTo("");
    }
  }

  @Test
  void normalizeResumeJson_bulletsVaziosOuEmBranco_saoDescartadosAntesDoPreenchimento()
      throws Exception {
    String input =
        "{\"experience\":[{\"bullets\":[\"\",\"   \",\"Valido\"]}]}"; // strings vazias/blank
    // filtradas

    String result = service.normalizeResumeJson(input);
    JsonNode bullets = mapper.readTree(result).get("experience").get(0).get("bullets");

    assertThat(bullets).hasSize(10);
    assertThat(bullets.get(0).asText()).isEqualTo("Valido");
    assertThat(bullets.get(1).asText()).isEqualTo("");
  }

  @Test
  void normalizeResumeJson_maisDe10Bullets_truncaEm10() throws Exception {
    StringBuilder bulletsJson = new StringBuilder("[");
    for (int i = 0; i < 15; i++) {
      if (i > 0) bulletsJson.append(",");
      bulletsJson.append("\"Item ").append(i).append("\"");
    }
    bulletsJson.append("]");
    String input = "{\"experience\":[{\"bullets\":" + bulletsJson + "}]}";

    String result = service.normalizeResumeJson(input);
    JsonNode bullets = mapper.readTree(result).get("experience").get(0).get("bullets");

    assertThat(bullets).hasSize(10);
    assertThat(bullets.get(9).asText()).isEqualTo("Item 9");
  }

  @Test
  void normalizeResumeJson_skillSemPct_aplicaDefault75() throws Exception {
    String input = "{\"skills\":[{\"name\":\"Python\"}]}";

    String result = service.normalizeResumeJson(input);
    JsonNode skill = mapper.readTree(result).get("skills").get(0);

    assertThat(skill.get("pct").asInt()).isEqualTo(75);
    assertThat(skill.get("name").asText()).isEqualTo("Python");
  }

  @Test
  void normalizeResumeJson_skillComPctNull_aplicaDefault75() throws Exception {
    String input = "{\"skills\":[{\"name\":\"Python\",\"pct\":null}]}";

    String result = service.normalizeResumeJson(input);
    JsonNode skill = mapper.readTree(result).get("skills").get(0);

    assertThat(skill.get("pct").asInt()).isEqualTo(75);
  }

  @Test
  void normalizeResumeJson_removeMarkdownFencesAntesDeParsear() throws Exception {
    String input = "```json\n{\"fullName\":\"Ana\"}\n```";

    String result = service.normalizeResumeJson(input);
    JsonNode root = mapper.readTree(result);

    assertThat(root.get("fullName").asText()).isEqualTo("Ana");
  }

  @Test
  void normalizeResumeJson_jsonMalformado_lancaExcecao() {
    String input = "isto nao e um json valido {{{";

    assertThatThrownBy(() -> service.normalizeResumeJson(input)).isInstanceOf(Exception.class);
  }

  // ── extractText / PDF ────────────────────────────────────────────────────

  @Test
  void extractText_pdfComCamadaDeTexto_extraiTextoSemNecessidadeDeOcr() throws Exception {
    String textoEsperado =
        "Maria Silva Engenheira de Software email maria arroba exemplo ponto com "
            + "telefone 11 99999 9999 experiencia profissional desenvolvedora backend "
            + "na empresa Acme de 2020 a 2023.";
    assertThat(textoEsperado.length()).isGreaterThan(80);

    byte[] pdfBytes = gerarPdfComTexto(textoEsperado);

    String extraido = service.extractText(pdfBytes, "curriculo.pdf");

    assertThat(extraido.replaceAll("\\s+", " ").trim())
        .isEqualTo(textoEsperado.replaceAll("\\s+", " ").trim());
  }

  // ── extractText / DOCX ───────────────────────────────────────────────────

  @Test
  void extractText_docx_extraiTextoDoParagrafo() throws Exception {
    String texto = "Curriculo em formato DOCX gerado para o teste.";
    byte[] docxBytes = gerarDocxComTexto(texto);

    String extraido = service.extractText(docxBytes, "curriculo.docx");

    assertThat(extraido).contains(texto);
  }

  // ── extractText / TXT ────────────────────────────────────────────────────

  @Test
  void extractText_txt_retornaConteudoDecodificadoUtf8() throws Exception {
    String texto = "Curriculo em texto simples com acentuacao: ção, ã, é.";

    String extraido =
        service.extractText(
            texto.getBytes(java.nio.charset.StandardCharsets.UTF_8), "curriculo.txt");

    assertThat(extraido).isEqualTo(texto);
  }

  // ── parseWithAI / callGemini (provedor padrao em producao) ──────────────

  @Test
  void callGemini_httpErro_lancaAIProviderExceptionComMensagemAcionavel() throws Exception {
    withStubServer(
        (status, body) -> {
          configurarServicoParaTeste("gemini", status, body);
          assertThatThrownBy(() -> service.parseWithAI("texto do curriculo"))
              .isInstanceOf(AIProviderException.class)
              .hasMessageContaining("Verifique a API key")
              .satisfies(e -> assertThat(((AIProviderException) e).getHttpStatus()).isEqualTo(502));
        },
        401,
        "{\"error\":\"invalid api key\"}");
  }

  @Test
  void callGemini_semCandidatesComBlockReason_indicaBloqueioDeSeguranca() throws Exception {
    withStubServer(
        (status, body) -> {
          configurarServicoParaTeste("gemini", status, body);
          assertThatThrownBy(() -> service.parseWithAI("curriculo com CPF e telefone"))
              .isInstanceOf(AIProviderException.class)
              .hasMessageContaining("bloqueado pelo filtro de seguranca")
              .hasMessageContaining("SAFETY");
        },
        200,
        "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}");
  }

  @Test
  void callGemini_finishReasonSafetySemTexto_indicaBloqueioDeSeguranca() throws Exception {
    withStubServer(
        (status, body) -> {
          configurarServicoParaTeste("gemini", status, body);
          assertThatThrownBy(() -> service.parseWithAI("curriculo"))
              .isInstanceOf(AIProviderException.class)
              .hasMessageContaining("bloqueado pelo filtro de seguranca");
        },
        200,
        "{\"candidates\":[{\"finishReason\":\"SAFETY\",\"content\":{\"parts\":[]}}]}");
  }

  @Test
  void callGemini_finishReasonMaxTokens_indicaResumoTruncado() throws Exception {
    withStubServer(
        (status, body) -> {
          configurarServicoParaTeste("gemini", status, body);
          assertThatThrownBy(() -> service.parseWithAI("curriculo muito extenso"))
              .isInstanceOf(AIProviderException.class)
              .hasMessageContaining("truncada");
        },
        200,
        "{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\",\"content\":{\"parts\":[]}}]}");
  }

  @Test
  void callGemini_respostaValida_retornaTextoDoCandidato() throws Exception {
    withStubServer(
        (status, body) -> {
          configurarServicoParaTeste("gemini", status, body);
          String result = service.parseWithAI("curriculo");
          assertThat(result).isEqualTo("{\"fullName\":\"Maria\"}");
        },
        200,
        "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":"
            + "[{\"text\":\"{\\\"fullName\\\":\\\"Maria\\\"}\"}]}}]}");
  }

  @Test
  void callGemini_503PersistenteComRetryAfterZero_esgota5TentativasELancaMensagemDeSobrecarga()
      throws Exception {
    java.util.concurrent.atomic.AtomicInteger requestCount =
        new java.util.concurrent.atomic.AtomicInteger(0);
    withStubServerComHeaders(
        (status, body) -> {
          configurarServicoParaTeste("gemini", status, body);
          assertThatThrownBy(() -> service.parseWithAI("curriculo"))
              .isInstanceOf(AIProviderException.class)
              .hasMessageContaining("temporariamente sobrecarregado")
              .satisfies(e -> assertThat(((AIProviderException) e).getHttpStatus()).isEqualTo(503));
          // 5 tentativas (era 3) — confirma que o limite foi elevado, nao apenas mantido
          assertThat(requestCount.get()).isEqualTo(5);
        },
        503,
        "{\"error\":\"UNAVAILABLE\"}",
        java.util.Map.of("Retry-After", "0"),
        requestCount);
  }

  // ── parseWithAI / callOpenAI e callAnthropic ─────────────────────────────

  @Test
  void callOpenAI_httpErro_lancaAIProviderException() throws Exception {
    withStubServer(
        (status, body) -> {
          configurarServicoParaTeste("openai", status, body);
          assertThatThrownBy(() -> service.parseWithAI("texto"))
              .isInstanceOf(AIProviderException.class)
              .hasMessageContaining("API OpenAI")
              .satisfies(e -> assertThat(((AIProviderException) e).getHttpStatus()).isEqualTo(502));
        },
        429,
        "{\"error\":\"rate limited\"}");
  }

  @Test
  void callAnthropic_httpErro_lancaAIProviderException() throws Exception {
    withStubServer(
        (status, body) -> {
          configurarServicoParaTeste("anthropic", status, body);
          assertThatThrownBy(() -> service.parseWithAI("texto"))
              .isInstanceOf(AIProviderException.class)
              .hasMessageContaining("API Anthropic");
        },
        500,
        "{\"error\":\"internal\"}");
  }

  // ── helpers de infraestrutura de teste (stub HTTP local) ─────────────────

  @FunctionalInterface
  private interface StubServerTest {
    void run(int status, String body) throws Exception;
  }

  /**
   * Sobe um HttpServer local respondendo sempre com {@code status}/{@code body}, para exercitar
   * callOpenAI/callAnthropic/callGemini sem rede real. Usa setHttpClientForTesting apenas para
   * garantir um HttpClient fresco por teste; a URL do servidor local e propagada via o campo
   * baseUrl (reflection), nao via o HttpClient em si.
   */
  private void withStubServer(StubServerTest test, int status, String body) throws Exception {
    HttpServer httpServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    httpServer.createContext(
        "/",
        exchange -> {
          byte[] resp = body.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(status, resp.length);
          try (var os = exchange.getResponseBody()) {
            os.write(resp);
          }
        });
    httpServer.start();
    try {
      ReflectionTestUtils.setField(
          service, "httpClient", HttpClient.newBuilder().build(), HttpClient.class);
      String base = "http://localhost:" + httpServer.getAddress().getPort();
      ReflectionTestUtils.setField(service, "baseUrl", base);
      test.run(status, body);
    } finally {
      httpServer.stop(0);
    }
  }

  /**
   * Variante de withStubServer que permite definir headers de resposta (ex.: Retry-After) e contar
   * quantas requisicoes o servico realmente fez — usada para validar o backoff/retry de callGemini
   * sem depender de tempo real de espera (Retry-After: 0 mantem o teste rapido).
   */
  private void withStubServerComHeaders(
      StubServerTest test,
      int status,
      String body,
      java.util.Map<String, String> headers,
      java.util.concurrent.atomic.AtomicInteger requestCount)
      throws Exception {
    HttpServer httpServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    httpServer.createContext(
        "/",
        exchange -> {
          requestCount.incrementAndGet();
          headers.forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
          byte[] resp = body.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(status, resp.length);
          try (var os = exchange.getResponseBody()) {
            os.write(resp);
          }
        });
    httpServer.start();
    try {
      ReflectionTestUtils.setField(
          service, "httpClient", HttpClient.newBuilder().build(), HttpClient.class);
      String base = "http://localhost:" + httpServer.getAddress().getPort();
      ReflectionTestUtils.setField(service, "baseUrl", base);
      test.run(status, body);
    } finally {
      httpServer.stop(0);
    }
  }

  private void configurarServicoParaTeste(String provider, int status, String body) {
    ReflectionTestUtils.setField(service, "provider", provider);
    ReflectionTestUtils.setField(service, "apiKey", "test-key");
    ReflectionTestUtils.setField(service, "model", "");
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private byte[] gerarPdfComTexto(String texto) throws Exception {
    try (PDDocument doc = new PDDocument()) {
      PDPage page = new PDPage();
      doc.addPage(page);
      try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
        cs.newLineAtOffset(50, 700);
        // PDFBox nao quebra linha automaticamente; escrevemos em varias linhas
        // curtas (quebrando só em espaços, nunca no meio de uma palavra) para
        // garantir que o texto caiba na pagina sem corromper o conteudo extraido.
        for (String linha : quebrarEmLinhas(texto, 60)) {
          cs.showText(linha);
          cs.newLineAtOffset(0, -14);
        }
        cs.endText();
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      doc.save(out);
      return out.toByteArray();
    }
  }

  /** Quebra o texto em linhas de no máximo {@code maxLen} chars, só nos espaços. */
  private java.util.List<String> quebrarEmLinhas(String texto, int maxLen) {
    java.util.List<String> linhas = new java.util.ArrayList<>();
    StringBuilder atual = new StringBuilder();
    for (String palavra : texto.split(" ")) {
      if (atual.length() > 0 && atual.length() + 1 + palavra.length() > maxLen) {
        linhas.add(atual.toString());
        atual.setLength(0);
      }
      if (atual.length() > 0) atual.append(' ');
      atual.append(palavra);
    }
    if (atual.length() > 0) linhas.add(atual.toString());
    return linhas;
  }

  private byte[] gerarDocxComTexto(String texto) throws Exception {
    try (XWPFDocument doc = new XWPFDocument()) {
      XWPFParagraph paragraph = doc.createParagraph();
      XWPFRun run = paragraph.createRun();
      run.setText(texto);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      doc.write(out);
      return out.toByteArray();
    }
  }
}
