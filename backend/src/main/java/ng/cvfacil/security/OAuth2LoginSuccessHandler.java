package ng.cvfacil.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import ng.cvfacil.domain.User;
import ng.cvfacil.repository.UserRepository;
import ng.cvfacil.service.AuditService;
import ng.cvfacil.service.CreditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * BUG CORRIGIDO: SecurityConfig ativava .oauth2Login(oauth -> {}) sem nenhum success handler —
 * Spring Security autenticava o usuário no Google normalmente, mas nada persistia o User no banco
 * nem emitia o JWT usado pelo resto da API. Login com Google em produção não criava conta nenhuma;
 * só a versão mock local (LocalMockAuthController) provisionava usuário de fato.
 *
 * <p>Fluxo: usuário autentica no Google → aqui buscamos/criamos o User pelo e-mail (idempotente,
 * mesmo padrão do mock local) → emitimos refresh_token via cookie httpOnly → redirecionamos para o
 * frontend, que já tem um SessionHydrator (Providers.jsx) que chama /api/auth/refresh ao carregar
 * qualquer página e hidrata a sessão a partir desse cookie — não é necessário expor o JWT de acesso
 * na URL do redirect.
 */
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

  private final UserRepository users;
  private final JwtService jwt;
  private final CreditService credits;
  private final AuditService audit;

  @Value("${cvfacil.frontend.base-url:http://localhost:3000}")
  private String frontendBaseUrl;

  @Value("${cvfacil.security.cookie-secure:true}")
  private boolean cookieSecure;

  public OAuth2LoginSuccessHandler(
      UserRepository users, JwtService jwt, CreditService credits, AuditService audit) {
    this.users = users;
    this.jwt = jwt;
    this.credits = credits;
    this.audit = audit;
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException {
    OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
    String email = oAuth2User.getAttribute("email");
    String name = oAuth2User.getAttribute("name");

    if (email == null || email.isBlank()) {
      response.sendRedirect(frontendBaseUrl + "/login?error=oauth_no_email");
      return;
    }

    User user =
        users
            .findByEmailIgnoreCase(email)
            .orElseGet(
                () -> {
                  User newUser = new User();
                  newUser.setEmail(email);
                  newUser.setDisplayName(
                      (name != null && !name.isBlank()) ? name : email.split("@")[0]);
                  newUser.setLocale("pt-BR");
                  newUser.setEmailVerified(true); // Google já verificou o e-mail
                  // Sem passwordHash — conta somente-OAuth
                  User saved = users.save(newUser);
                  credits.grantCourtesyIfEligible(saved.getId());
                  return saved;
                });

    String refresh = jwt.issueRefreshToken(user.getId());
    ResponseCookie cookie =
        ResponseCookie.from("refresh_token", refresh)
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite("Strict")
            .path("/")
            .maxAge(jwt.refreshTtl())
            .build();
    response.addHeader("Set-Cookie", cookie.toString());

    audit.record(
        user.getId(),
        "LOGIN_GOOGLE_SUCCESS",
        request.getRemoteAddr(),
        request.getHeader("User-Agent"),
        null);

    response.sendRedirect(frontendBaseUrl + "/dashboard");
  }
}
