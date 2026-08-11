package ng.cvfacil.service;

import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import ng.cvfacil.domain.AuditLog;
import ng.cvfacil.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AuditLog imutável com encadeamento SHA-256: self_hash = H(prev_hash || payload).
 * Em produção, assinar com HMAC-SHA256 e chave dedicada do KMS (SEC-10).
 *
 * IMPORTANTE: falhas de auditoria nunca devem bloquear a operação principal.
 * O método record() absorve toda exceção e retorna null em caso de erro.
 *
 * CORREÇÃO (race condition multi-instância): a versão anterior usava apenas
 * `synchronized`, um lock em memória da própria JVM. Em qualquer deploy com
 * mais de uma instância do backend atrás de um load balancer, duas instâncias
 * podiam ler o mesmo `findTopByOrderByIdDesc()` simultaneamente e gravar dois
 * registros com o mesmo prevHash, quebrando a cadeia de auditoria sem erro
 * visível. Agora o serializador é um advisory lock transacional do próprio
 * Postgres (pg_advisory_xact_lock) — funciona entre instâncias e é liberado
 * automaticamente no fim da transação, sem risco de lock órfão.
 */
@Service
public class AuditService {

  private static final Logger log = LoggerFactory.getLogger(AuditService.class);

  /** Chave arbitrária e estável para o advisory lock da cadeia de auditoria. */
  private static final long AUDIT_CHAIN_LOCK_KEY = 7_326_192_84L;

  private final AuditLogRepository repo;
  private final EntityManager entityManager;

  public AuditService(AuditLogRepository repo, EntityManager entityManager) {
    this.repo = repo;
    this.entityManager = entityManager;
  }

  @Transactional
  public AuditLog record(UUID userId, String action, String ip, String userAgent, String detailsJson) {
    try {
      // Serializa leitura+escrita do último hash entre todas as instâncias do
      // backend. Liberado automaticamente ao fim desta transação.
      entityManager
          .createNativeQuery("SELECT pg_advisory_xact_lock(:key)")
          .setParameter("key", AUDIT_CHAIN_LOCK_KEY)
          .getSingleResult();

      AuditLog last = repo.findTopByOrderByIdDesc();
      String prev = last == null ? "" : last.getSelfHash();
      AuditLog entry = new AuditLog();
      entry.setUserId(userId);
      entry.setAction(action);
      entry.setIp(ip);
      entry.setUserAgent(userAgent);
      entry.setDetailsJson(detailsJson);
      entry.setPrevHash(prev);
      entry.setSelfHash(sha256(prev + "|" + action + "|" + userId + "|" + detailsJson));
      return repo.save(entry);
    } catch (Exception e) {
      // Auditoria é não-crítica: loga o erro mas não propaga para não derrubar
      // o fluxo de login/registro nem incrementar contadores de falha indevidamente.
      log.error("[audit] Falha ao registrar entrada de auditoria (não crítico): {}", e.getMessage());
      return null;
    }
  }

  private String sha256(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : digest) sb.append(String.format("%02x", b));
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
