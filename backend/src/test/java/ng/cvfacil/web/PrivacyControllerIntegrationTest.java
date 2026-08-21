package ng.cvfacil.web;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import ng.cvfacil.domain.Resume;
import ng.cvfacil.domain.User;
import ng.cvfacil.repository.ResumeRepository;
import ng.cvfacil.repository.UserRepository;
import ng.cvfacil.service.AesGcmCipherService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Testes de integração para PrivacyController (LGPD Art. 18 — export/eliminação da própria conta),
 * com JWT simulado via {@code SecurityMockMvcRequestPostProcessors.jwt()}.
 *
 * <p>Diferente de AuthControllerIntegrationTest/ResumeControllerIntegrationTest (perfil "local"),
 * PrivacyController é {@code @Profile("!local")}: só existe quando o profile "local" NÃO está
 * ativo, junto com SecurityConfig (que exige um {@code JwtDecoder} real). Este teste usa o profile
 * dedicado "privacytest" (ver {@code application-privacytest.yml}) para carregar exatamente essa
 * combinação de beans, e fornece seu próprio PostgreSQL embarcado via {@link TestDataSourceConfig}
 * já que {@code LocalDatabaseConfig} só se ativa no profile "local".
 *
 * <p>{@code jwt()} injeta a autenticação diretamente no {@code SecurityContext} do MockMvc sem
 * passar pelo {@code JwtDecoder} — por isso não é necessário assinar tokens com a chave privada RSA
 * real; o "sub" do JWT simulado é o UUID do usuário criado no teste, exatamente como o {@code
 * SecurityConfig} de produção espera (ver {@code PrivacyController.resolveUserId}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("privacytest")
class PrivacyControllerIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired UserRepository users;
  @Autowired ResumeRepository resumes;
  @Autowired PasswordEncoder encoder;
  @Autowired AesGcmCipherService cipher;

  @MockBean StringRedisTemplate redis;

  private User testUser;

  @BeforeEach
  void setup() {
    testUser = new User();
    testUser.setEmail("privacy-test@cvfacil.ng");
    testUser.setPasswordHash(encoder.encode("Senha@Forte123"));
    testUser.setDisplayName("Privacy Tester");
    testUser.setCredits(5);
    users.save(testUser);
  }

  @AfterEach
  void cleanup() {
    resumes.deleteAll(resumes.findByUserIdOrderByUpdatedAtDesc(testUser.getId()));
    users.findById(testUser.getId()).ifPresent(users::delete);
  }

  // ── GET /api/users/me/export ────────────────────────────────────────────

  @Test
  void export_withValidJwt_returnsUserDataAndResumes() throws Exception {
    Resume r = new Resume();
    r.setUserId(testUser.getId());
    r.setLayoutId("classic");
    r.setContentEnc(cipher.encrypt("{\"fullName\":\"Privacy Export\"}".getBytes()));
    resumes.save(r);

    mvc.perform(
            get("/api/users/me/export")
                .with(jwt().jwt(j -> j.subject(testUser.getId().toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(testUser.getId().toString()))
        .andExpect(jsonPath("$.email").value("privacy-test@cvfacil.ng"))
        .andExpect(jsonPath("$.role").value("USER"))
        .andExpect(jsonPath("$.credits").value(5))
        .andExpect(jsonPath("$.resumes.length()").value(1))
        .andExpect(jsonPath("$.resumes[0].content").value("{\"fullName\":\"Privacy Export\"}"));
  }

  @Test
  void export_withoutJwt_returns401() throws Exception {
    mvc.perform(get("/api/users/me/export")).andExpect(status().isUnauthorized());
  }

  // ── DELETE /api/users/me ────────────────────────────────────────────────

  @Test
  void deleteAccount_withWrongPassword_returns401() throws Exception {
    var req = Map.of("password", "SenhaErrada999!");

    mvc.perform(
            delete("/api/users/me")
                .with(jwt().jwt(j -> j.subject(testUser.getId().toString())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Senha incorreta"));

    assertThat(users.findById(testUser.getId())).isPresent();
  }

  @Test
  void deleteAccount_withCorrectPassword_deletesAccountAndCascadesResumes() throws Exception {
    Resume r = new Resume();
    r.setUserId(testUser.getId());
    r.setLayoutId("classic");
    r.setContentEnc(cipher.encrypt("{\"fullName\":\"Vai Sumir\"}".getBytes()));
    resumes.save(r);
    UUID resumeId = r.getId();

    var req = Map.of("password", "Senha@Forte123");

    mvc.perform(
            delete("/api/users/me")
                .with(jwt().jwt(j -> j.subject(testUser.getId().toString())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
        .andExpect(status().isNoContent());

    assertThat(users.findById(testUser.getId())).isEmpty();
    assertThat(resumes.findById(resumeId)).isEmpty();
  }

  @Test
  void deleteAccount_asRootMaster_returns409AndDoesNotDelete() throws Exception {
    testUser.setRole(User.Role.ROOT_MASTER);
    users.save(testUser);

    var req = Map.of("password", "Senha@Forte123");

    mvc.perform(
            delete("/api/users/me")
                .with(jwt().jwt(j -> j.subject(testUser.getId().toString())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.error")
                .value("Contas Root não podem se autoexcluir. Transfira o papel antes."));

    assertThat(users.findById(testUser.getId())).isPresent();
  }

  // ── PostgreSQL embarcado para o profile "privacytest" ───────────────────

  /**
   * {@code LocalDatabaseConfig} só se ativa no profile "local" (que este teste evita
   * propositalmente — ver Javadoc da classe). Fornece o mesmo PostgreSQL embarcado (zonky)
   * diretamente para o contexto deste teste.
   */
  @TestConfiguration
  static class TestDataSourceConfig {

    @Bean
    @Primary
    DataSource dataSource() throws IOException {
      EmbeddedPostgres embedded = EmbeddedPostgres.builder().start();
      HikariConfig cfg = new HikariConfig();
      cfg.setJdbcUrl("jdbc:postgresql://localhost:" + embedded.getPort() + "/postgres");
      cfg.setUsername("postgres");
      cfg.setPassword("postgres");
      cfg.setMaximumPoolSize(5);
      cfg.setPoolName("privacytest-embedded-pg");
      return new HikariDataSource(cfg);
    }
  }
}
