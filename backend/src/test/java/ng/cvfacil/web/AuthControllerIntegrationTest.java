package ng.cvfacil.web;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import ng.cvfacil.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Testes de integração para AuthController com perfil "local" (EmbeddedPostgres).
 *
 * <p>Redis é mockado via @MockBean — sem necessidade de Docker ou servidor Redis externo. O
 * EmbeddedPostgres do perfil local sobe automaticamente pelo {@code LocalDatabaseConfig}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AuthControllerIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired UserRepository users;

  /** Mock do Redis — evita dependência de servidor externo nos testes. */
  @MockBean StringRedisTemplate redis;

  @AfterEach
  void cleanup() {
    // Remove usuários criados durante cada teste para isolamento
    users.findByEmailIgnoreCase("inttest@cvfacil.ng").ifPresent(users::delete);
    users.findByEmailIgnoreCase("inttest2@cvfacil.ng").ifPresent(users::delete);
  }

  // ── /api/auth/register ────────────────────────────────────────────────────

  @Test
  void register_withValidData_returns200AndUserView() throws Exception {
    allowRateLimit();

    var req =
        Map.of(
            "email", "inttest@cvfacil.ng",
            "password", "Senha@Forte123",
            "displayName", "Teste Int",
            "locale", "pt-BR",
            "termsAccepted", true);

    mvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("inttest@cvfacil.ng"))
        .andExpect(jsonPath("$.displayName").value("Teste Int"))
        .andExpect(jsonPath("$.id").isNotEmpty());
  }

  @Test
  void register_duplicateEmail_returns409() throws Exception {
    allowRateLimit();

    var req =
        Map.of(
            "email", "inttest@cvfacil.ng",
            "password", "Senha@Forte123",
            "displayName", "Dup",
            "termsAccepted", true);

    mvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
        .andExpect(status().isOk());

    mvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
        .andExpect(status().isConflict());
  }

  @Test
  void register_weakPassword_returns400() throws Exception {
    var req =
        Map.of(
            "email", "inttest@cvfacil.ng",
            "password", "curta", // < 10 chars
            "displayName", "Teste",
            "termsAccepted", true);

    mvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
        .andExpect(status().isBadRequest());
  }

  // ── /api/auth/login ───────────────────────────────────────────────────────

  @Test
  void login_withCorrectCredentials_returnsAccessToken() throws Exception {
    allowRateLimit();
    registerUser("inttest@cvfacil.ng", "Senha@Forte123");

    var req = Map.of("email", "inttest@cvfacil.ng", "password", "Senha@Forte123");
    MvcResult result =
        mvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.user.email").value("inttest@cvfacil.ng"))
            .andReturn();

    // Verifica que o cookie de refresh foi emitido — a resposta tem DOIS
    // Set-Cookie (XSRF-TOKEN do filtro CSRF + refresh_token do controller);
    // getHeader() (singular) só retorna o primeiro, por isso getHeaders() (plural).
    assertThat(result.getResponse().getHeaders("Set-Cookie"))
        .anyMatch(c -> c.contains("refresh_token="));
  }

  @Test
  void login_withWrongPassword_returns401() throws Exception {
    allowRateLimit();
    registerUser("inttest@cvfacil.ng", "Senha@Forte123");

    var req = Map.of("email", "inttest@cvfacil.ng", "password", "SenhaErrada999!");
    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void login_withNonExistentEmail_returns401() throws Exception {
    allowRateLimit();

    var req = Map.of("email", "naoexiste@cvfacil.ng", "password", "Senha@Forte123");
    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void login_whenRateLimitExceeded_returns429() throws Exception {
    // Simula Redis retornando contador acima do limite (6 > 5)
    when(redis.execute(any(RedisScript.class), anyList(), any(String.class))).thenReturn(6L);

    var req = Map.of("email", "inttest@cvfacil.ng", "password", "Senha@Forte123");
    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
        .andExpect(status().isTooManyRequests());
  }

  // ── /api/auth/logout ──────────────────────────────────────────────────────

  @Test
  void logout_returns204WithExpiredCookie() throws Exception {
    mvc.perform(post("/api/auth/logout"))
        .andExpect(status().isNoContent())
        .andDo(
            result -> {
              // Mesmo motivo do teste de login: resposta tem 2 Set-Cookie
              // (XSRF-TOKEN do filtro CSRF + refresh_token expirado do controller).
              java.util.List<String> cookies = result.getResponse().getHeaders("Set-Cookie");
              assertThat(cookies)
                  .anyMatch(c -> c.contains("refresh_token=") && c.contains("Max-Age=0"));
            });
  }

  // ── /api/auth/forgot-password ─────────────────────────────────────────────

  @Test
  void forgotPassword_alwaysReturns200_toPreventEnumeration() throws Exception {
    // E-mail inexistente deve retornar 200 (previne enumeração de contas)
    var req = Map.of("email", "naoexiste@cvfacil.ng");
    mvc.perform(
            post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
        .andExpect(status().isOk());

    // E-mail existente também deve retornar 200
    registerUser("inttest@cvfacil.ng", "Senha@Forte123");
    var req2 = Map.of("email", "inttest@cvfacil.ng");
    mvc.perform(
            post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req2)))
        .andExpect(status().isOk());
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  /** Configura o mock do Redis para permitir todas as requisições (contador = 1). */
  private void allowRateLimit() {
    when(redis.execute(any(RedisScript.class), anyList(), any(String.class))).thenReturn(1L);
  }

  private void registerUser(String email, String password) throws Exception {
    allowRateLimit();
    var req =
        Map.of("email", email, "password", password, "displayName", "Teste", "termsAccepted", true);
    mvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
        .andExpect(status().isOk());
  }
}
