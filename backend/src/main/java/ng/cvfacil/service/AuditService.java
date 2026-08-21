package ng.cvfacil.service;

import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ng.cvfacil.domain.AuditLog;
import ng.cvfacil.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AuditLog imutável com encadeamento SHA-256: self_hash = H(prev_hash || payload). Em produção,
 * assinar com HMAC-SHA256 e chave dedicada do KMS (SEC-10).
 *
 * <p>IMPORTANTE: falhas de auditoria nunca devem bloquear a operação principal. O método record()
 * absorve toda exceção e retorna null em caso de erro.
 *
 * <p>CORREÇÃO (race condition multi-instância): a versão anterior usava apenas `synchronized`, um
 * lock em memória da própria JVM. Em qualquer deploy com mais de uma instância do backend atrás de
 * um load balancer, duas instâncias podiam ler o mesmo `findTopByOrderByIdDesc()` simultaneamente e
 * gravar dois registros com o mesmo prevHash, quebrando a cadeia de auditoria sem erro visível.
 * Agora o serializador é um advisory lock transacional do próprio Postgres (pg_advisory_xact_lock)
 * — funciona entre instâncias e é liberado automaticamente no fim da transação, sem risco de lock
 * órfão.
 *
 * <p>MITIGAÇÃO (contenção sob carga): pg_advisory_xact_lock por padrão bloqueia indefinidamente.
 * Como record() roda inline no caminho de login/logout/créditos (não em background), um pico de
 * concorrência faria requisições — e as conexões do pool Hikari que elas seguram — empilharem à
 * espera do mesmo lock, podendo esgotar o pool para toda a aplicação, não só para auditoria. Um
 * lock_timeout de sessão limita a espera; como record() já trata qualquer exceção como não crítica
 * (loga e retorna null sem propagar), o pior caso vira "essa entrada de auditoria não foi gravada
 * desta vez" em vez de travar requisições não relacionadas.
 */
@Service
public class AuditService {

  private static final Logger log = LoggerFactory.getLogger(AuditService.class);

  /** Chave arbitrária e estável para o advisory lock da cadeia de auditoria. */
  private static final long AUDIT_CHAIN_LOCK_KEY = 7_326_192_84L;

  /**
   * E-mail dentro de detailsJson: mascara a parte local, mantendo o domínio (ex.:
   * ana.silva@gmail.com vira a***@gmail.com) — LGPD Art. 6/III (minimização): o registro continua
   * útil para investigação sem expor o dado pessoal em texto puro.
   */
  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("([a-zA-Z0-9._%+-])[a-zA-Z0-9._%+-]*(@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})");

  private final AuditLogRepository repo;
  private final EntityManager entityManager;

  public AuditService(AuditLogRepository repo, EntityManager entityManager) {
    this.repo = repo;
    this.entityManager = entityManager;
  }

  @Transactional
  public AuditLog record(
      UUID userId, String action, String ip, String userAgent, String detailsJson) {
    try {
      // Serializa leitura+escrita do último hash entre todas as instâncias do
      // backend. Liberado automaticamente ao fim desta transação.
      // lock_timeout bounded evita empilhar conexões do pool indefinidamente sob
      // contenção (ver javadoc da classe) — SET LOCAL vale só para esta transação.
      entityManager.createNativeQuery("SET LOCAL lock_timeout = '3s'").executeUpdate();
      entityManager
          .createNativeQuery("SELECT pg_advisory_xact_lock(:key)")
          .setParameter("key", AUDIT_CHAIN_LOCK_KEY)
          .getSingleResult();

      AuditLog last = repo.findTopByOrderByIdDesc();
      String prev = last == null ? "" : last.getSelfHash();
      String masked = maskPii(detailsJson);
      AuditLog entry = new AuditLog();
      entry.setUserId(userId);
      entry.setAction(action);
      entry.setIp(ip);
      entry.setUserAgent(userAgent);
      entry.setDetailsJson(masked);
      entry.setPrevHash(prev);
      entry.setSelfHash(sha256(prev + "|" + action + "|" + userId + "|" + masked));
      return repo.save(entry);
    } catch (Exception e) {
      // Auditoria é não-crítica: loga o erro mas não propaga para não derrubar
      // o fluxo de login/registro nem incrementar contadores de falha indevidamente.
      log.error(
          "[audit] Falha ao registrar entrada de auditoria (não crítico): {}", e.getMessage());
      return null;
    }
  }

  /**
   * Aplica em detailsJson antes de gravar/hashear — ver EMAIL_PATTERN. Ponto único de defesa: um
   * chamador futuro de record() não precisa lembrar de mascarar e-mail manualmente.
   */
  private String maskPii(String detailsJson) {
    if (detailsJson == null || detailsJson.isBlank()) return detailsJson;
    Matcher m = EMAIL_PATTERN.matcher(detailsJson);
    return m.replaceAll("$1***$2");
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
