package ng.cvfacil.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Profile "local": sobe um PostgreSQL 16 embarcado (zonky embedded-postgres), executa as migrations
 * Flyway normalmente e expoe o DataSource primario.
 *
 * <p>Este bean só é criado quando cvfacil.local.use-embedded-db=true (padrão). Para usar o banco de
 * dados Neon (nuvem), defina em application-local.yml:
 *
 * <p>cvfacil: local: use-embedded-db: false spring: datasource: url:
 * jdbc:postgresql://<host>.neon.tech/neondb?sslmode=require username: <usuario> password: <senha>
 *
 * <p>Quando use-embedded-db=false, o Spring Boot Autoconfigure cria o DataSource a partir do
 * spring.datasource.* configurado acima — este bean é ignorado.
 *
 * <p>Produção (Neon via variáveis de ambiente) NÃO é afetada — este bean só existe quando o profile
 * "local" está ativo.
 */
@Configuration
@Profile("local")
@ConditionalOnProperty(
    prefix = "cvfacil.local",
    name = "use-embedded-db",
    havingValue = "true",
    matchIfMissing = true // padrão: usa embedded se a propriedade não estiver definida
    )
public class LocalDatabaseConfig {

  private static final Logger log = LoggerFactory.getLogger(LocalDatabaseConfig.class);

  private EmbeddedPostgres embedded;

  @Bean
  @Primary
  public DataSource localDataSource() throws IOException {
    log.info("[local] Subindo PostgreSQL embarcado...");
    this.embedded = EmbeddedPostgres.builder().start();
    int port = embedded.getPort();
    log.info("[local] Postgres embarcado pronto na porta {}", port);

    HikariConfig cfg = new HikariConfig();
    cfg.setJdbcUrl("jdbc:postgresql://localhost:" + port + "/postgres");
    cfg.setUsername("postgres");
    cfg.setPassword("postgres");
    cfg.setMaximumPoolSize(5);
    cfg.setPoolName("local-embedded-pg");
    return new HikariDataSource(cfg);
  }

  @PreDestroy
  public void shutdown() throws IOException {
    if (embedded != null) {
      log.info("[local] Encerrando Postgres embarcado.");
      embedded.close();
    }
  }
}
