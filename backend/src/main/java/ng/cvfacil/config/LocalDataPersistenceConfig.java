package ng.cvfacil.config;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Profile "local": persiste os dados da tabela `users` entre reinicializações.
 *
 * <p>Ativo apenas quando cvfacil.local.use-embedded-db=true (padrão). Quando o usuário configura
 * spring.datasource.url apontando para o Neon, este bean é desativado — a persistência é
 * responsabilidade do Neon.
 *
 * <p>Zonky EmbeddedPostgres 2.0.7 sempre chama `initdb` ao iniciar — não suporta reusar um cluster
 * existente. Por isso, usamos dump-on-shutdown / restore-on-startup:
 *
 * <p>- Ao encerrar (@PreDestroy): exporta todos os usuários para
 * <repo-root>/.runtime/pg-users-backup.sql (INSERTs idempotentes).
 *
 * <p>- A cada 60 s (@Scheduled): faz backup incremental enquanto a app roda, garantindo que
 * usuários criados entre reinicias não sejam perdidos mesmo em caso de kill abrupto (SIGKILL,
 * fechamento forçado da janela).
 *
 * <p>- Ao iniciar (ApplicationReadyEvent, após Flyway migrar o schema): se o arquivo de backup
 * existir, executa os INSERTs para restaurar os dados.
 *
 * <p>O arquivo fica em .runtime/ que já está no .gitignore.
 */
@Configuration
@Profile("local")
@EnableScheduling
@ConditionalOnProperty(
    prefix = "cvfacil.local",
    name = "use-embedded-db",
    havingValue = "true",
    matchIfMissing = true // padrão: ativo quando embedded DB está em uso
    )
public class LocalDataPersistenceConfig {

  private static final Logger log = LoggerFactory.getLogger(LocalDataPersistenceConfig.class);

  private final DataSource dataSource;

  public LocalDataPersistenceConfig(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  // ── Restauração após o contexto estar completamente pronto ──────────────────
  // ApplicationReadyEvent garante que Flyway já rodou e o schema existe.

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    Path backup = backupPath();
    if (!Files.exists(backup)) {
      log.info("[local] Nenhum backup de usuários encontrado em {}", backup);
      return;
    }
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      String sql = Files.readString(backup, StandardCharsets.UTF_8);
      stmt.execute(sql);
      log.info("[local] Usuários restaurados do backup: {}", backup);
    } catch (Exception e) {
      log.warn(
          "[local] Erro ao restaurar backup (pode ser normal na 1ª execução): {}", e.getMessage());
    }
  }

  // ── Backup periódico a cada 60 segundos ────────────────────────────────────
  // Protege contra kill abrupto (SIGKILL, fechamento da janela do terminal).
  // @PreDestroy NÃO é chamado em SIGKILL — este agendamento garante que
  // usuários recém-criados não sejam perdidos na próxima reinicialização.

  @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
  public void periodicBackup() {
    doBackup(false);
  }

  // ── Dump antes do contexto ser destruído ────────────────────────────────────
  // @PreDestroy é chamado antes da DataSource ser fechada porque este bean
  // foi criado depois dela (depende dela via construtor), portanto é destruído ANTES.
  // Cobre o caso de shutdown gracioso (Ctrl+C, SIGTERM).

  @PreDestroy
  public void onDestroy() {
    doBackup(true);
  }

  // ── Implementação do backup ─────────────────────────────────────────────────

  private void doBackup(boolean isShutdown) {
    Path backup = backupPath();
    try {
      Files.createDirectories(backup.getParent());
    } catch (IOException e) {
      log.error("[local] Não foi possível criar diretório de backup: {}", e.getMessage());
      return;
    }

    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {

      StringBuilder sb = new StringBuilder();
      sb.append("-- CVFacil.NG local users backup — gerado automaticamente\n");

      // Descobre colunas dinamicamente (resiliente a migrações futuras)
      try (ResultSet meta = stmt.executeQuery("SELECT * FROM users LIMIT 0")) {
        ResultSetMetaData rsmeta = meta.getMetaData();
        int cols = rsmeta.getColumnCount();
        List<String> colNames = new ArrayList<>(cols);
        for (int i = 1; i <= cols; i++) colNames.add(rsmeta.getColumnName(i));

        try (ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM users")) {
          int count = 0;
          while (rs.next()) {
            count++;
            sb.append("INSERT INTO users (")
                .append(String.join(", ", colNames))
                .append(") VALUES (");
            for (int i = 1; i <= cols; i++) {
              if (i > 1) sb.append(", ");
              Object val = rs.getObject(i);
              if (val == null) {
                sb.append("NULL");
              } else if (val instanceof Number || val instanceof Boolean) {
                sb.append(val);
              } else {
                // Strings, UUIDs, timestamps, enums → quoted
                sb.append('\'')
                    .append(val.toString().replace("\\", "\\\\").replace("'", "''"))
                    .append('\'');
              }
            }
            // ON CONFLICT (id): id é a PK (UUID), sem risco de colisão na restauração
            sb.append(") ON CONFLICT (id) DO NOTHING;\n");
          }
          // Ao restaurar, zera contadores de falha para evitar que eventuais
          // erros de auditoria acumulados bloqueiem contas entre reinicializações.
          if (count > 0) {
            sb.append("UPDATE users SET failed_logins = 0, locked_until = NULL;\n");
          }
          Files.writeString(backup, sb.toString(), StandardCharsets.UTF_8);
          if (isShutdown) {
            log.info("[local] Backup final de {} usuário(s) salvo em {}", count, backup);
          } else {
            log.debug("[local] Backup periódico de {} usuário(s) salvo em {}", count, backup);
          }
        }
      }
    } catch (Exception e) {
      if (isShutdown) {
        log.error("[local] Erro ao salvar backup final de usuários: {}", e.getMessage());
      } else {
        log.warn("[local] Erro no backup periódico de usuários: {}", e.getMessage());
      }
    }
  }

  // ── Helper ──────────────────────────────────────────────────────────────────

  private static Path backupPath() {
    // user.dir quando o JAR é executado pelo startup script = backend/
    // parent = <repo-root>; toAbsolutePath() evita que user.dir relativo retorne null em
    // getParent().
    Path userDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
    Path parent = userDir.getParent();
    // NPE CORRIGIDO: getParent() retorna null se userDir for um caminho raiz (ex: C:\ ou /).
    // Nesse caso, fallback para o próprio userDir (JAR iniciado do root, situação atípica).
    Path base = (parent != null) ? parent : userDir;
    return base.resolve(".runtime/pg-users-backup.sql");
  }
}
