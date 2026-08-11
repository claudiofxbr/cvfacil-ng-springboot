package ng.cvfacil.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Configuracao de seguranca SOMENTE para o profile "local".
 *
 * Diferencas vs. SecurityConfig (producao):
 *  - Ativa oauth2Login com um failureHandler que redireciona para o frontend em vez de
 *    retornar a pagina Whitelabel 404 quando as credenciais Google sao invalidas/dummy.
 *  - NAO ativa oauth2ResourceServer (nao precisa de JWKs reais).
 *  - CORS / CSRF / HSTS / headers continuam iguais — queremos testar esses controles.
 *  - Endpoints autenticados ficam abertos em dev para permitir smoke test sem JWT valido.
 *    Em producao, SecurityConfig exige autenticacao real.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity           // habilita @PreAuthorize / @PostAuthorize em todos os beans
@Profile("local")
public class LocalSecurityConfig {

  @Value("${cvfacil.security.cors-allowed-origins}")
  private String allowedOrigins;

  @Value("${spring.security.oauth2.client.registration.google.client-id:}")
  private String googleClientId;

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration cfg = new CorsConfiguration();
    cfg.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
    cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN"));
    cfg.setAllowCredentials(true);
    cfg.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
    src.registerCorsConfiguration("/**", cfg);
    return src;
  }

  @Bean
  public SecurityFilterChain localSecurityFilterChain(HttpSecurity http) throws Exception {
    CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
    csrfHandler.setCsrfRequestAttributeName(null);

    http.cors(cors -> {})
        .csrf(
            csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(csrfHandler)
                    .ignoringRequestMatchers(
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/**",
                        "/actuator/health"))
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .headers(
            h ->
                h.httpStrictTransportSecurity(hsts -> hsts.maxAgeInSeconds(63072000).preload(true))
                    .frameOptions(fo -> fo.deny())
                    .contentTypeOptions(cto -> {})
                    .referrerPolicy(
                        rp ->
                            rp.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.GET, "/actuator/**", "/api/health").permitAll()
                    .requestMatchers("/api/auth/**").permitAll()
                    .anyRequest().permitAll())
        // Ativa oauth2Login para que /oauth2/authorization/google exista.
        // Se as credenciais forem dummy (local-dev-unused), um filtro anterior
        // intercepta a requisicao e redireciona ao frontend antes de chegar ao Google.
        .oauth2Login(oauth -> oauth
            .failureHandler(new SimpleUrlAuthenticationFailureHandler(
                "http://localhost:3000/login?error=oauth_failed")));

    // Intercepta /oauth2/authorization/google ANTES que o filtro do Spring
    // redirecione ao Google — evita o erro 401 invalid_client do Google
    // quando as credenciais sao dummy (local-dev-unused) ou nao configuradas.
    http.addFilterBefore(new OncePerRequestFilter() {
      @Override
      protected void doFilterInternal(
          HttpServletRequest request, HttpServletResponse response, FilterChain chain)
          throws ServletException, IOException {
        if ("/oauth2/authorization/google".equals(request.getRequestURI())) {
          boolean notConfigured =
              googleClientId == null
                  || googleClientId.isBlank()
                  || "local-dev-unused".equals(googleClientId);
          if (notConfigured) {
            // Credenciais dummy → abre o formulário de mock OAuth no frontend
            // em vez de mostrar uma mensagem de erro.
            response.sendRedirect(
                "http://localhost:3000/login?mock_oauth=true");
            return;
          }
        }
        chain.doFilter(request, response);
      }
    }, OAuth2AuthorizationRequestRedirectFilter.class);

    return http.build();
  }
}
