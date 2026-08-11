package ng.cvfacil.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import ng.cvfacil.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Emissão de tokens JWT RS256.
 *
 * <p>Em produção: lê a chave privada RSA-2048 de {@code cvfacil.jwt.private-key}
 * (variável de ambiente {@code JWT_PRIVATE_KEY}) e assina tokens com Nimbus JOSE.
 * Os tokens de acesso incluem claims {@code sub}, {@code email}, {@code role},
 * {@code locale}, {@code iat}, {@code exp} e {@code iss}. O token de refresh
 * inclui adicionalmente {@code type=refresh}.
 *
 * <p>Em dev local (quando {@code JWT_PRIVATE_KEY} não está definida): emite tokens
 * stub não assinados aceitos apenas pelo {@code LocalResumeController}.  Nenhum
 * endpoint de produção aceita esses stubs.
 *
 * <p>Rotação de chaves: gere um novo par RSA, atualize {@code JWT_PRIVATE_KEY} e
 * {@code JWT_PUBLIC_KEY} no ambiente de produção; tokens antigos expiram em no
 * máximo {@code accessTtlMinutes} (padrão 15 min) ou {@code refreshTtlDays}
 * (padrão 7 dias).
 */
@Service
public class JwtService {

  private static final Logger log = LoggerFactory.getLogger(JwtService.class);

  @Value("${cvfacil.jwt.issuer}")
  private String issuer;

  @Value("${cvfacil.jwt.access-ttl-minutes}")
  private int accessTtlMinutes;

  @Value("${cvfacil.jwt.refresh-ttl-days}")
  private int refreshTtlDays;

  /** PEM da chave privada RSA (sem cabeçalhos), fornecida via JWT_PRIVATE_KEY. */
  @Value("${cvfacil.jwt.private-key:}")
  private String privateKeyPem;

  /**
   * RSA signer inicializado no startup; {@code null} quando {@code JWT_PRIVATE_KEY}
   * não está configurada (modo dev/stub).
   */
  private RSASSASigner signer;

  @PostConstruct
  void init() {
    if (privateKeyPem == null || privateKeyPem.isBlank()) {
      log.warn("[JwtService] JWT_PRIVATE_KEY nao configurada — emitindo STUB tokens. "
          + "Aceitaveis apenas em dev local (LocalResumeController). "
          + "Em producao, defina JWT_PRIVATE_KEY com a chave RSA-2048 PEM.");
      return;
    }
    try {
      String stripped = privateKeyPem
          .replace("-----BEGIN PRIVATE KEY-----", "")
          .replace("-----END PRIVATE KEY-----", "")
          .replace("-----BEGIN RSA PRIVATE KEY-----", "")
          .replace("-----END RSA PRIVATE KEY-----", "")
          .replaceAll("\\s+", "");
      byte[] decoded = Base64.getDecoder().decode(stripped);
      RSAPrivateKey privateKey = (RSAPrivateKey)
          KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
      signer = new RSASSASigner(privateKey);
      log.info("[JwtService] Chave RSA carregada — tokens RS256 ativos.");
    } catch (Exception e) {
      throw new IllegalStateException(
          "[JwtService] Falha ao carregar JWT_PRIVATE_KEY: " + e.getMessage(), e);
    }
  }

  /**
   * Emite um access token JWT (validade {@code accessTtlMinutes}).
   * Retorna um STUB não assinado quando a chave privada não está configurada.
   */
  public String issueAccessToken(User user) {
    if (signer == null) {
      return "STUB_ACCESS." + user.getId() + "." + System.currentTimeMillis();
    }
    try {
      Instant now = Instant.now();
      JWTClaimsSet claims = new JWTClaimsSet.Builder()
          .issuer(issuer)
          .subject(user.getId().toString())
          .claim("email", user.getEmail())
          .claim("role", user.getRole().name())
          .claim("locale", user.getLocale())
          .issueTime(Date.from(now))
          .expirationTime(Date.from(now.plus(accessTtl())))
          .jwtID(UUID.randomUUID().toString())
          .build();
      SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
      jwt.sign(signer);
      return jwt.serialize();
    } catch (Exception e) {
      throw new IllegalStateException("[JwtService] Erro ao assinar access token: " + e.getMessage(), e);
    }
  }

  /**
   * Emite um refresh token (validade {@code refreshTtlDays}).
   * Em produção é também um JWT RS256 com {@code type=refresh} para validação
   * criptográfica no endpoint {@code /api/auth/refresh}.
   * Em dev retorna o formato STUB legado.
   */
  public String issueRefreshToken(UUID userId) {
    if (signer == null) {
      return "STUB_REFRESH." + userId + "." + UUID.randomUUID();
    }
    try {
      Instant now = Instant.now();
      JWTClaimsSet claims = new JWTClaimsSet.Builder()
          .issuer(issuer)
          .subject(userId.toString())
          .claim("type", "refresh")
          .issueTime(Date.from(now))
          .expirationTime(Date.from(now.plus(refreshTtl())))
          .jwtID(UUID.randomUUID().toString())
          .build();
      SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
      jwt.sign(signer);
      return jwt.serialize();
    } catch (Exception e) {
      throw new IllegalStateException("[JwtService] Erro ao assinar refresh token: " + e.getMessage(), e);
    }
  }

  /** {@code true} quando a chave privada está carregada e tokens RS256 reais são emitidos. */
  public boolean isRealSigningActive() { return signer != null; }

  public Duration accessTtl()  { return Duration.ofMinutes(accessTtlMinutes); }
  public Duration refreshTtl() { return Duration.ofDays(refreshTtlDays); }

  public Map<String, Object> buildClaims(User user) {
    return Map.of(
        "iss",    issuer,
        "sub",    user.getId().toString(),
        "email",  user.getEmail(),
        "role",   user.getRole().name(),
        "locale", user.getLocale());
  }
}
