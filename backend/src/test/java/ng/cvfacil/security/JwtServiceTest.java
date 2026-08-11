package ng.cvfacil.security;

import static org.assertj.core.api.Assertions.*;

import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.util.Base64;
import java.util.UUID;
import ng.cvfacil.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Testes unitários para JwtService.
 *
 * <p>Dois cenários: (1) sem chave privada → tokens STUB; (2) com chave RSA-2048 → JWT RS256.
 */
class JwtServiceTest {

  private JwtService service;
  private User user;

  @BeforeEach
  void setup() {
    user = new User();
    ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
    user.setEmail("test@cvfacil.ng");
    user.setDisplayName("Teste");
  }

  // ─── modo STUB (sem chave configurada) ───────────────────────────────────

  @Nested
  class StubMode {

    @BeforeEach
    void buildService() {
      service = new JwtService();
      ReflectionTestUtils.setField(service, "issuer", "https://cvfacil.ng");
      ReflectionTestUtils.setField(service, "accessTtlMinutes", 15);
      ReflectionTestUtils.setField(service, "refreshTtlDays", 7);
      ReflectionTestUtils.setField(service, "privateKeyPem", ""); // sem chave
      service.init();
    }

    @Test
    void accessToken_isStub() {
      String token = service.issueAccessToken(user);
      assertThat(token).startsWith("STUB_ACCESS.");
      assertThat(token).contains(user.getId().toString());
    }

    @Test
    void refreshToken_isStub() {
      String token = service.issueRefreshToken(user.getId());
      assertThat(token).startsWith("STUB_REFRESH.");
      assertThat(token).contains(user.getId().toString());
    }

    @Test
    void isRealSigningActive_returnsFalse() {
      assertThat(service.isRealSigningActive()).isFalse();
    }

    @Test
    void buildClaims_containsExpectedKeys() {
      var claims = service.buildClaims(user);
      assertThat(claims).containsKeys("iss", "sub", "email", "role", "locale");
      assertThat(claims.get("sub")).isEqualTo(user.getId().toString());
    }
  }

  // ─── modo RS256 (com chave RSA real) ─────────────────────────────────────

  @Nested
  class Rs256Mode {

    @BeforeEach
    void buildService() throws Exception {
      // Gera par RSA-2048 para o teste (não vai para produção)
      KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
      gen.initialize(2048);
      KeyPair pair = gen.generateKeyPair();
      RSAPrivateKey privateKey = (RSAPrivateKey) pair.getPrivate();
      String pem = Base64.getEncoder().encodeToString(privateKey.getEncoded());

      service = new JwtService();
      ReflectionTestUtils.setField(service, "issuer", "https://cvfacil.ng");
      ReflectionTestUtils.setField(service, "accessTtlMinutes", 15);
      ReflectionTestUtils.setField(service, "refreshTtlDays", 7);
      ReflectionTestUtils.setField(service, "privateKeyPem", pem);
      service.init();
    }

    @Test
    void isRealSigningActive_returnsTrue() {
      assertThat(service.isRealSigningActive()).isTrue();
    }

    @Test
    void accessToken_isValidSignedJwt() throws Exception {
      String token = service.issueAccessToken(user);
      assertThat(token).doesNotStartWith("STUB_ACCESS.");

      SignedJWT jwt = (SignedJWT) JWTParser.parse(token);
      assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo(user.getId().toString());
      assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo("https://cvfacil.ng");
      assertThat(jwt.getJWTClaimsSet().getStringClaim("email")).isEqualTo(user.getEmail());
      assertThat(jwt.getJWTClaimsSet().getExpirationTime()).isNotNull();
    }

    @Test
    void refreshToken_hasTypeRefreshClaim() throws Exception {
      String token = service.issueRefreshToken(user.getId());
      assertThat(token).doesNotStartWith("STUB_REFRESH.");

      SignedJWT jwt = (SignedJWT) JWTParser.parse(token);
      assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo(user.getId().toString());
      assertThat(jwt.getJWTClaimsSet().getStringClaim("type")).isEqualTo("refresh");
    }

    @Test
    void accessToken_expiresInAccessTtl() throws Exception {
      long before = System.currentTimeMillis();
      String token = service.issueAccessToken(user);
      SignedJWT jwt = (SignedJWT) JWTParser.parse(token);
      long expMs = jwt.getJWTClaimsSet().getExpirationTime().getTime();
      long iatMs = jwt.getJWTClaimsSet().getIssueTime().getTime();
      long diffMinutes = (expMs - iatMs) / 60_000;
      // Deve estar próximo de accessTtlMinutes (15 min)
      assertThat(diffMinutes).isEqualTo(15);
    }
  }

  // ─── edge case: chave PEM inválida ───────────────────────────────────────

  @Test
  void init_withInvalidPem_throwsIllegalState() {
    JwtService svc = new JwtService();
    ReflectionTestUtils.setField(svc, "issuer", "https://cvfacil.ng");
    ReflectionTestUtils.setField(svc, "accessTtlMinutes", 15);
    ReflectionTestUtils.setField(svc, "refreshTtlDays", 7);
    ReflectionTestUtils.setField(svc, "privateKeyPem", "INVALIDO_NAO_E_PEM");
    assertThatThrownBy(svc::init)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_PRIVATE_KEY");
  }
}
