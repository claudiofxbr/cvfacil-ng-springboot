package ng.cvfacil.service;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;

/**
 * Allowlist de IP para login do RootMaster (PRD §4.4). A tabela {@code root_ip_allowlist} já existe
 * desde V1__init.sql (coluna {@code cidr}, tipo nativo CIDR do Postgres) mas nunca tinha sido lida
 * por nenhum código até agora.
 *
 * <p>Consultada via query nativa (não como {@code @Entity} JPA): mapear uma coluna CIDR como String
 * quebraria a validação de schema do Hibernate ({@code ddl-auto=validate}), que espera o tipo Java
 * bater exatamente com o tipo da coluna.
 *
 * <p>Tabela vazia = allowlist desativada (nenhuma restrição) — evita lockout antes do primeiro IP
 * ser cadastrado; assim que houver ao menos uma linha, o IP de origem do login precisa estar
 * contido em um dos CIDRs cadastrados.
 */
@Service
public class RootIpAllowlistService {

  private final EntityManager em;

  public RootIpAllowlistService(EntityManager em) {
    this.em = em;
  }

  public boolean isAllowed(String remoteIp) {
    long total = ((Number) em.createNativeQuery("SELECT count(*) FROM root_ip_allowlist").getSingleResult()).longValue();
    if (total == 0) return true;
    if (remoteIp == null || remoteIp.isBlank()) return false;
    long matches =
        ((Number)
                em.createNativeQuery(
                        "SELECT count(*) FROM root_ip_allowlist WHERE cidr >>= CAST(:ip AS inet)")
                    .setParameter("ip", remoteIp)
                    .getSingleResult())
            .longValue();
    return matches > 0;
  }
}
