package ng.cvfacil;

import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.core.env.Environment;

@SpringBootApplication
public class CvFacilApplication {
  public static void main(String[] args) {
    SpringApplication app = new SpringApplication(CvFacilApplication.class);
    app.addListeners(
        (org.springframework.context.ApplicationListener<ApplicationEnvironmentPreparedEvent>)
            event -> guardAgainstLocalProfileInProduction(event.getEnvironment()));
    app.run(args);
  }

  /**
   * O profile "local" (LocalSecurityConfig) libera todos os endpoints (permitAll, CSRF desligado) e
   * é o profile DEFAULT quando SPRING_PROFILES_ACTIVE não é definido (application.yml:
   * spring.profiles.default=local). Se o deploy de produção esquecer essa variável, a aplicação
   * sobe sem autenticação nenhuma.
   *
   * <p>DATABASE_URL só é definido em ambientes reais (Neon) — em "local" o DataSource vem de
   * LocalDatabaseConfig (Postgres embarcado) e esse valor fica vazio. Se DATABASE_URL estiver
   * presente enquanto "local" está ativo, é sinal de um ambiente de produção configurado rodando
   * com segurança de dev: falha rápido em vez de subir aberto. Lança antes do contexto Spring ser
   * criado, então nenhuma porta chega a ser aberta.
   */
  static void guardAgainstLocalProfileInProduction(Environment env) {
    String[] activeProfiles = env.getActiveProfiles();
    boolean localActive =
        Arrays.asList(activeProfiles).contains("local")
            || (activeProfiles.length == 0
                && "local".equals(env.getProperty("spring.profiles.default", "local")));
    String databaseUrl = env.getProperty("DATABASE_URL");
    if (localActive && databaseUrl != null && !databaseUrl.isBlank()) {
      throw new IllegalStateException(
          "[STARTUP ABORTADO] O profile 'local' está ativo (endpoints abertos, CSRF "
              + "desligado) mas DATABASE_URL aponta para um banco real, indicando um "
              + "ambiente de produção/staging. Defina SPRING_PROFILES_ACTIVE explicitamente "
              + "(ex.: 'prod') antes de subir este ambiente.");
    }
  }
}
