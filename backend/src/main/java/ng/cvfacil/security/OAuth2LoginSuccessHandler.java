package ng.cvfacil.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import ng.cvfacil.domain.User;
import ng.cvfacil.repository.UserRepository;
import ng.cvfacil.service.AuditService;
import ng.cvfacil.service.CreditService;
import ng.cvfacil.service.TokenRevocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
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
  private final TokenRevocationService revocation;

  @Value("${cvfacil.frontend.base-url:http://localhost:3000}")
  private String frontendBaseUrl;

  @Value("${cvfacil.security.cookie-secure:true}")
  private boolean cookieSecure;

  /** Só disponível fora do profile "local" (SecurityConfig) — ver AuthController.jwtDecoder. */
  @Autowired(required = false)
  private JwtDecoder jwtDecoder;

  public OAuth2LoginSuccessHandler(
      UserRepository users,
      JwtService jwt,
      CreditService credits,
      AuditService audit,
      TokenRevocationService revocation) {
    this.users = users;
    this.jwt = jwt;
    this.credits = credits;
    this.audit = audit;
    this.revocation = revocation;
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

    UUID relinkUserId = consumeRelinkCookie(request, response);
    if (relinkUserId != null) {
      handleGoogleRelink(relinkUserId, email, request, response);
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

    // BUG CORRIGIDO: o orElseGet acima só marca emailVerified=true na CRIAÇÃO da conta — uma
    // conta pré-existente (ex.: cadastrada antes por e-mail/senha, sem verificação) nunca tinha
    // esse flag atualizado ao logar com Google depois, mesmo o Google confirmando a posse do
    // e-mail nesse exato momento. Isso é sempre incorreto (login Google bem-sucedido = e-mail
    // verificado, ponto), e também é o que RootEmailEnforcementService usa como sinal confiável
    // de posse — sem este fix, uma conta protegida criada localmente antes do dono real logar
    // com Google nunca seria promovida automaticamente.
    if (!user.isEmailVerified()) {
      user.setEmailVerified(true);
      user = users.save(user);
    }

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

  /**
   * Lê e apaga o cookie {@code relink_state} (uso único), validando o token nele contido. Retorna
   * {@code null} quando não é um pedido de troca de conta — o que cobre tanto o login normal quanto
   * um token de relink ausente/expirado/adulterado, que aqui é tratado como "sem pedido de troca"
   * em vez de erro, deixando o fluxo cair no login normal por e-mail.
   */
  private UUID consumeRelinkCookie(HttpServletRequest request, HttpServletResponse response) {
    if (request.getCookies() == null) return null;
    String token = null;
    for (Cookie c : request.getCookies()) {
      if ("relink_state".equals(c.getName())) {
        token = c.getValue();
        break;
      }
    }
    if (token == null || token.isBlank()) return null;

    // Apaga o cookie imediatamente — uso único, independente do token ser válido ou não.
    Cookie expire = new Cookie("relink_state", "");
    expire.setPath("/");
    expire.setMaxAge(0);
    response.addCookie(expire);

    if (jwtDecoder == null) {
      if (token.startsWith("STUB_RELINK.")) {
        String[] parts = token.split("\\.", 3);
        if (parts.length < 2) return null;
        try {
          return UUID.fromString(parts[1]);
        } catch (IllegalArgumentException e) {
          return null;
        }
      }
      return null;
    }
    try {
      Jwt decoded = jwtDecoder.decode(token);
      if (!"google_relink".equals(decoded.getClaimAsString("type"))) return null;
      return UUID.fromString(decoded.getSubject());
    } catch (JwtException | IllegalArgumentException e) {
      return null;
    }
  }

  /**
   * Troca a conta Google vinculada ao login do usuário {@code relinkUserId} para {@code
   * newGoogleEmail} — como o vínculo com Google neste app é o próprio e-mail da conta (não há
   * coluna separada de "google id"), reassociar a uma conta Google diferente é, na prática, mudar o
   * e-mail de login para o da nova conta Google (já verificado pelo Google neste momento).
   *
   * <p>Nunca deixa o usuário sem vínculo válido no meio do caminho: só grava a mudança depois de
   * confirmar que nenhuma outra conta já usa esse e-mail; a conta antiga só "some" porque o e-mail
   * dela é sobrescrito, num único save.
   */
  private void handleGoogleRelink(
      UUID relinkUserId, String newGoogleEmail, HttpServletRequest request,
      HttpServletResponse response)
      throws IOException {
    User target = users.findById(relinkUserId).orElse(null);
    if (target == null) {
      response.sendRedirect(frontendBaseUrl + "/dashboard/security?relink=error&reason=session");
      return;
    }

    User existingOwner = users.findByEmailIgnoreCase(newGoogleEmail).orElse(null);
    if (existingOwner != null && !existingOwner.getId().equals(target.getId())) {
      response.sendRedirect(frontendBaseUrl + "/dashboard/security?relink=error&reason=in_use");
      return;
    }

    String oldEmail = target.getEmail();
    target.setEmail(newGoogleEmail);
    target.setEmailVerified(true); // Google acabou de confirmar a posse desse e-mail
    users.save(target);

    // Revoga a sessão anterior (mesmo padrão do logout) — força reautenticação com o vínculo novo
    // em vez de deixar o refresh token antigo continuar válido até expirar sozinho.
    revokeCurrentRefreshToken(request);

    audit.record(
        target.getId(),
        "GOOGLE_ACCOUNT_RELINKED",
        request.getRemoteAddr(),
        request.getHeader("User-Agent"),
        "{\"oldEmail\":\"" + oldEmail + "\",\"newEmail\":\"" + newGoogleEmail + "\"}");

    String refresh = jwt.issueRefreshToken(target.getId());
    ResponseCookie cookie =
        ResponseCookie.from("refresh_token", refresh)
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite("Strict")
            .path("/")
            .maxAge(jwt.refreshTtl())
            .build();
    response.addHeader("Set-Cookie", cookie.toString());
    response.sendRedirect(frontendBaseUrl + "/dashboard/security?relink=success");
  }

  private void revokeCurrentRefreshToken(HttpServletRequest request) {
    if (request.getCookies() == null || jwtDecoder == null) return;
    for (Cookie c : request.getCookies()) {
      if (!"refresh_token".equals(c.getName())) continue;
      try {
        Jwt decoded = jwtDecoder.decode(c.getValue());
        if ("refresh".equals(decoded.getClaimAsString("type"))) {
          revocation.revoke(decoded.getId(), decoded.getExpiresAt());
        }
      } catch (JwtException ignored) {
        // Token antigo já inválido/expirado — nada a revogar.
      }
      return;
    }
  }
}
