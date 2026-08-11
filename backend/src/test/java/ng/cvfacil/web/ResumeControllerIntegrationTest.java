package ng.cvfacil.web;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import ng.cvfacil.domain.User;
import ng.cvfacil.repository.ResumeRepository;
import ng.cvfacil.repository.UserRepository;
import ng.cvfacil.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Testes de integração para LocalResumeController (perfil "local").
 *
 * <p>Cria um usuário real no EmbeddedPostgres, autentica via /api/auth/login
 * para obter um STUB token e testa o CRUD completo de currículos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ResumeControllerIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired UserRepository users;
  @Autowired ResumeRepository resumes;
  @Autowired PasswordEncoder encoder;
  @Autowired JwtService jwtService;

  @MockBean
  StringRedisTemplate redis;

  private User testUser;
  private String stubToken;

  @BeforeEach
  void setup() {
    // Cria usuário de teste diretamente no repositório
    testUser = new User();
    testUser.setEmail("resume-test@cvfacil.ng");
    testUser.setPasswordHash(encoder.encode("Senha@Forte123"));
    testUser.setDisplayName("Resume Tester");
    // Criação de currículo agora consome 1 crédito (CreditService) — o fluxo real
    // de registro concede a cortesia, mas este teste cria o usuário direto no
    // repositório, então precisa de saldo explícito para os cenários de criação.
    testUser.setCredits(10);
    users.save(testUser);

    // Gera STUB token para autenticação nos testes
    stubToken = jwtService.issueAccessToken(testUser);
    assertThat(stubToken).startsWith("STUB_ACCESS.");
  }

  @AfterEach
  void cleanup() {
    resumes.deleteAll(resumes.findByUserIdOrderByUpdatedAtDesc(testUser.getId()));
    users.delete(testUser);
  }

  // ── POST /api/resumes ─────────────────────────────────────────────────────

  @Test
  void createResume_withValidData_returns201() throws Exception {
    var req = resumeRequest("classic", "{\"fullName\":\"João Silva\",\"email\":\"joao@example.com\"}");

    mvc.perform(post("/api/resumes")
            .header("Authorization", "Bearer " + stubToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(req)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.layoutId").value("classic"))
        .andExpect(jsonPath("$.content").value(req.get("content")));
  }

  @Test
  void createResume_withoutAuth_returns401() throws Exception {
    var req = resumeRequest("classic", "{\"fullName\":\"João\"}");
    mvc.perform(post("/api/resumes")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(req)))
        .andExpect(status().isUnauthorized());
  }

  // ── GET /api/resumes ──────────────────────────────────────────────────────

  @Test
  void listResumes_returnsUserResumes() throws Exception {
    createResumeViaApi(stubToken, "classic", "{\"fullName\":\"Teste 1\"}");
    createResumeViaApi(stubToken, "modern", "{\"fullName\":\"Teste 2\"}");

    mvc.perform(get("/api/resumes")
            .header("Authorization", "Bearer " + stubToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void listResumes_doesNotReturnOtherUsersData() throws Exception {
    // Cria segundo usuário com seus próprios currículos
    User other = new User();
    other.setEmail("other@cvfacil.ng");
    other.setPasswordHash(encoder.encode("Senha@Forte123"));
    other.setDisplayName("Other");
    other.setCredits(10);
    users.save(other);
    String otherToken = jwtService.issueAccessToken(other);
    try {
      createResumeViaApi(otherToken, "classic", "{\"fullName\":\"Outro\"}");

      // O usuário principal não deve ver currículo do outro
      mvc.perform(get("/api/resumes")
              .header("Authorization", "Bearer " + stubToken))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(0));
    } finally {
      resumes.deleteAll(resumes.findByUserIdOrderByUpdatedAtDesc(other.getId()));
      users.delete(other);
    }
  }

  // ── GET /api/resumes/{id} ─────────────────────────────────────────────────

  @Test
  void getResume_existingId_returnsDecryptedContent() throws Exception {
    String content = "{\"fullName\":\"Decifrado\"}";
    UUID id = createResumeViaApi(stubToken, "modern", content);

    mvc.perform(get("/api/resumes/" + id)
            .header("Authorization", "Bearer " + stubToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id.toString()))
        .andExpect(jsonPath("$.content").value(content));
  }

  @Test
  void getResume_nonExistentId_returns404() throws Exception {
    mvc.perform(get("/api/resumes/" + UUID.randomUUID())
            .header("Authorization", "Bearer " + stubToken))
        .andExpect(status().isNotFound());
  }

  // ── PUT /api/resumes/{id} ─────────────────────────────────────────────────

  @Test
  void updateResume_incrementsVersion() throws Exception {
    UUID id = createResumeViaApi(stubToken, "classic", "{\"fullName\":\"Original\"}");

    var update = resumeRequest("modern", "{\"fullName\":\"Atualizado\"}");
    MvcResult result = mvc.perform(put("/api/resumes/" + id)
            .header("Authorization", "Bearer " + stubToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(update)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(jsonPath("$.content").value(update.get("content")))
        .andReturn();
  }

  @Test
  void updateResume_otherUsersResume_returns404() throws Exception {
    User other = new User();
    other.setEmail("other2@cvfacil.ng");
    other.setPasswordHash(encoder.encode("Senha@Forte123"));
    other.setDisplayName("Other2");
    other.setCredits(10);
    users.save(other);
    String otherToken = jwtService.issueAccessToken(other);
    try {
      UUID otherId = createResumeViaApi(otherToken, "classic", "{\"fullName\":\"Outro\"}");

      // Usuário principal não deve conseguir editar currículo de outro
      var update = resumeRequest("modern", "{\"fullName\":\"Hack\"}");
      mvc.perform(put("/api/resumes/" + otherId)
              .header("Authorization", "Bearer " + stubToken)
              .contentType(MediaType.APPLICATION_JSON)
              .content(mapper.writeValueAsString(update)))
          .andExpect(status().isNotFound());
    } finally {
      resumes.deleteAll(resumes.findByUserIdOrderByUpdatedAtDesc(other.getId()));
      users.delete(other);
    }
  }

  // ── DELETE /api/resumes/{id} ──────────────────────────────────────────────

  @Test
  void deleteResume_existingId_returns204() throws Exception {
    UUID id = createResumeViaApi(stubToken, "classic", "{\"fullName\":\"Deletar\"}");

    mvc.perform(delete("/api/resumes/" + id)
            .header("Authorization", "Bearer " + stubToken))
        .andExpect(status().isNoContent());

    // Confirma que foi removido
    assertThat(resumes.findById(id)).isEmpty();
  }

  @Test
  void deleteResume_nonExistentId_returns404() throws Exception {
    mvc.perform(delete("/api/resumes/" + UUID.randomUUID())
            .header("Authorization", "Bearer " + stubToken))
        .andExpect(status().isNotFound());
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private Map<String, Object> resumeRequest(String layoutId, String content) {
    return Map.of(
        "layoutId", layoutId,
        "locale", "pt-BR",
        "content", content);
  }

  private UUID createResumeViaApi(String token, String layoutId, String content) throws Exception {
    var req = resumeRequest(layoutId, content);
    MvcResult result = mvc.perform(post("/api/resumes")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(req)))
        .andExpect(status().isCreated())
        .andReturn();

    var body = mapper.readTree(result.getResponse().getContentAsString());
    return UUID.fromString(body.get("id").asText());
  }
}
