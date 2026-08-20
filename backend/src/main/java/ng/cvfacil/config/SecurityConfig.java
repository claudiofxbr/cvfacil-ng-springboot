package ng.cvfacil.config;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import ng.cvfacil.security.HttpCookieOAuth2AuthorizationRequestRepository;
import ng.cvfacil.security.OAuth2LoginSuccessHandler;
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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configuração central de segurança (perfil produção — @Profile("!local")): - CSRF: double-submit
 * cookie (XSRF-TOKEN) - CORS: apenas origens configuradas via CORS_ALLOWED_ORIGINS - Sessões
 * stateless: autenticação via JWT RS256 - OAuth2 Login (Google) + Resource Server (JWT) com
 * JwtDecoder explícito - BCrypt cost 12
 *
 * <p>CORREÇÃO (startup bug): o bloco anterior usava .oauth2ResourceServer(rs -> rs.jwt(jwt -> {}))
 * sem nenhum JwtDecoder bean configurado. Spring Security 6 lança IllegalStateException "No
 * JwtDecoder bean was found" ao construir o SecurityFilterChain, impedindo a inicialização. Agora o
 * JwtDecoder é construído explicitamente a partir de JWT_PUBLIC_KEY (RSA PEM).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // habilita @PreAuthorize / @PostAuthorize em todos os beans
@Profile("!local")
public class SecurityConfig {

  @Value("${cvfacil.security.cors-allowed-origins}")
  private String allowedOrigins;

  /** Chave pública RSA em formato PEM (sem cabeçalhos) fornecida via JWT_PUBLIC_KEY. */
  @Value("${cvfacil.jwt.public-key:}")
  private String jwtPublicKeyPem;

  /** URI do issuer JWT — usado como alternativa quando JWT_PUBLIC_KEY não está definida. */
  @Value("${cvfacil.jwt.issuer}")
  private String jwtIssuer;

  @Value("${cvfacil.frontend.base-url:http://localhost:3000}")
  private String frontendBaseUrl;

  private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

  public SecurityConfig(OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler) {
    this.oAuth2LoginSuccessHandler = oAuth2LoginSuccessHandler;
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  public HttpCookieOAuth2AuthorizationRequestRepository cookieAuthorizationRequestRepository() {
    return new HttpCookieOAuth2AuthorizationRequestRepository();
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

  /**
   * JwtDecoder RS256 construído a partir da chave pública PEM (JWT_PUBLIC_KEY).
   *
   * <p>Isso resolve o crash de startup: Spring Security precisa de um JwtDecoder bean explícito
   * quando .oauth2ResourceServer(rs -> rs.jwt(...)) é chamado sem que
   * spring.security.oauth2.resourceserver.jwt.* esteja configurado no application.yml.
   *
   * <p>Em produção, JWT_PUBLIC_KEY deve conter a chave pública RS256 correspondente à chave privada
   * usada em JwtService para assinar os tokens.
   */
  @Bean
  public JwtDecoder jwtDecoder() {
    if (jwtPublicKeyPem == null || jwtPublicKeyPem.isBlank()) {
      throw new IllegalStateException(
          "[SecurityConfig] JWT_PUBLIC_KEY não configurada. "
              + "Em produção, defina a variável de ambiente JWT_PUBLIC_KEY com a chave pública RSA PEM. "
              + "Para gerar: openssl genrsa -out private.pem 2048 && "
              + "openssl rsa -in private.pem -pubout -out public.pem");
    }
    try {
      // Remove cabeçalhos PEM e espaços em branco
      String stripped =
          jwtPublicKeyPem
              .replace("-----BEGIN PUBLIC KEY-----", "")
              .replace("-----END PUBLIC KEY-----", "")
              .replaceAll("\\s+", "");
      byte[] decoded = Base64.getDecoder().decode(stripped);
      RSAPublicKey publicKey =
          (RSAPublicKey)
              KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
      return NimbusJwtDecoder.withPublicKey(publicKey).build();
    } catch (Exception e) {
      throw new IllegalStateException(
          "[SecurityConfig] Falha ao carregar JWT_PUBLIC_KEY: " + e.getMessage(), e);
    }
  }

  /**
   * BUG ENCONTRADO E CORRIGIDO: sem este bean, o Spring Security usa o conversor default de
   * authorities, que lê o claim "scope"/"scp" — mas JwtService assina tokens com um claim "role"
   * (USER/ADMIN/ROOT_MASTER), não "scope". Resultado: toda authority ficava vazia e
   * .hasAuthority("ROLE_ROOT_MASTER") nunca era concedida a ninguém, mesmo para o ROOT_MASTER
   * legítimo — /api/admin/** ficava inacessível na prática.
   */
  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
    authorities.setAuthoritiesClaimName("role");
    authorities.setAuthorityPrefix("ROLE_");
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(authorities);
    return converter;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
    csrfHandler.setCsrfRequestAttributeName(null);

    http.cors(cors -> {})
        .csrf(
            csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(csrfHandler)
                    .ignoringRequestMatchers(
                        "/api/credits/webhook/**",
                        "/api/auth/login",
                        "/api/auth/register",
                        "/oauth2/**",
                        "/login/oauth2/**",
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
                                org.springframework.security.web.header.writers
                                    .ReferrerPolicyHeaderWriter.ReferrerPolicy
                                    .STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.GET, "/actuator/health")
                    .permitAll()
                    .requestMatchers("/api/auth/**", "/oauth2/**", "/login/oauth2/**")
                    .permitAll()
                    // Webhook do PagSeguro: chamado pelo gateway (sem JWT nosso), autenticado
                    // pela assinatura x-authenticity-token dentro do próprio controller —
                    // ver PagSeguroWebhookController/PagSeguroClient.isValidWebhookSignature.
                    .requestMatchers(HttpMethod.POST, "/api/credits/webhook/**")
                    .permitAll()
                    // Root e Admin entram no /api/admin/**; as ações restritas ao Root
                    // (excluir usuário, conceder créditos) são checadas dentro do
                    // controller — ver AdminController/AdminService.
                    .requestMatchers("/api/admin/**")
                    .hasAnyAuthority("ROLE_ADMIN", "ROLE_ROOT_MASTER")
                    .anyRequest()
                    .authenticated())
        .oauth2Login(
            oauth ->
                oauth
                    .authorizationEndpoint(
                        a ->
                            a.authorizationRequestRepository(
                                cookieAuthorizationRequestRepository()))
                    .successHandler(oAuth2LoginSuccessHandler)
                    .failureHandler(
                        new SimpleUrlAuthenticationFailureHandler(
                            frontendBaseUrl + "/login?error=oauth_failed")))
        .oauth2ResourceServer(
            rs -> rs.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

    return http.build();
  }
}
