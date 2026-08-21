package ng.cvfacil.service;

import java.time.Instant;
import java.util.UUID;
import ng.cvfacil.domain.RevokedToken;
import ng.cvfacil.repository.RevokedTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lista de revogação de refresh tokens: por padrão um refresh token JWT continua criptograficamente
 * válido até expirar (até {@code refreshTtlDays}, padrão 7 dias) mesmo depois de logout ou de já
 * ter sido trocado por um novo em {@code /api/auth/refresh}. Esta tabela guarda o {@code jti} de
 * todo token consumido/revogado para que o backend rejeite reuso.
 *
 * <p>Rotação: cada chamada bem-sucedida a {@code /api/auth/refresh} revoga o refresh token
 * apresentado e emite um novo — um refresh token só pode ser trocado uma vez. Isso também detecta
 * roubo de token: se um token já revogado for reapresentado, o refresh falha e o usuário precisa
 * logar novamente.
 */
@Service
public class TokenRevocationService {

  private static final Logger log = LoggerFactory.getLogger(TokenRevocationService.class);

  private final RevokedTokenRepository repo;

  public TokenRevocationService(RevokedTokenRepository repo) {
    this.repo = repo;
  }

  @Transactional
  public void revoke(String jti, Instant expiresAt) {
    if (jti == null) return;
    try {
      UUID id = UUID.fromString(jti);
      if (!repo.existsById(id)) {
        repo.save(new RevokedToken(id, expiresAt != null ? expiresAt : Instant.now()));
      }
    } catch (IllegalArgumentException e) {
      // jti nao e um UUID (ex.: formato STUB antigo sem jti valido) — nada a revogar.
    }
  }

  @Transactional(readOnly = true)
  public boolean isRevoked(String jti) {
    if (jti == null) return false;
    try {
      return repo.existsById(UUID.fromString(jti));
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  /** Remove entradas ja expiradas — evita crescimento indefinido da tabela. */
  @Scheduled(fixedRate = 3_600_000, initialDelay = 300_000)
  @Transactional
  public void cleanupExpired() {
    int removed = repo.deleteByExpiresAtBefore(Instant.now());
    if (removed > 0) {
      log.info("[token-revocation] {} entradas expiradas removidas.", removed);
    }
  }
}
