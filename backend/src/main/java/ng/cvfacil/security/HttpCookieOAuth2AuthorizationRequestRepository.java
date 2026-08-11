package ng.cvfacil.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Base64;
import java.util.Optional;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.util.SerializationUtils;

/**
 * Repositório stateless do OAuth2AuthorizationRequest, via cookie em vez de HttpSession.
 *
 * <p>BUG EVITADO: a app roda com SessionCreationPolicy.STATELESS (JWT em toda a API), mas o
 * repositório default do Spring Security (HttpSessionOAuth2AuthorizationRequestRepository) guarda o
 * "state" do fluxo OAuth2 na sessão HTTP entre o redirect para o Google e o callback — sem uma
 * sessão persistente e confiável nesse intervalo, o login com Google quebraria de forma
 * intermitente com "authorization_request_not_found". Este repositório guarda o mesmo dado em um
 * cookie de curta duração (3 min, tempo suficiente para o usuário completar o consentimento no
 * Google), eliminando a dependência de sessão.
 */
public class HttpCookieOAuth2AuthorizationRequestRepository
    implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

  public static final String COOKIE_NAME = "oauth2_auth_request";
  private static final int COOKIE_EXPIRE_SECONDS = 180;

  @Override
  public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
    return getCookie(request)
        .map(
            c ->
                (OAuth2AuthorizationRequest)
                    SerializationUtils.deserialize(Base64.getUrlDecoder().decode(c.getValue())))
        .orElse(null);
  }

  @Override
  public void saveAuthorizationRequest(
      OAuth2AuthorizationRequest authorizationRequest,
      HttpServletRequest request,
      HttpServletResponse response) {
    if (authorizationRequest == null) {
      deleteCookie(request, response);
      return;
    }
    Cookie cookie =
        new Cookie(
            COOKIE_NAME,
            Base64.getUrlEncoder()
                .encodeToString(SerializationUtils.serialize(authorizationRequest)));
    cookie.setPath("/");
    cookie.setHttpOnly(true);
    cookie.setMaxAge(COOKIE_EXPIRE_SECONDS);
    response.addCookie(cookie);
  }

  @Override
  public OAuth2AuthorizationRequest removeAuthorizationRequest(
      HttpServletRequest request, HttpServletResponse response) {
    OAuth2AuthorizationRequest authRequest = loadAuthorizationRequest(request);
    deleteCookie(request, response);
    return authRequest;
  }

  private Optional<Cookie> getCookie(HttpServletRequest request) {
    if (request.getCookies() == null) return Optional.empty();
    for (Cookie c : request.getCookies()) {
      if (COOKIE_NAME.equals(c.getName())) return Optional.of(c);
    }
    return Optional.empty();
  }

  private void deleteCookie(HttpServletRequest request, HttpServletResponse response) {
    getCookie(request)
        .ifPresent(
            c -> {
              c.setValue("");
              c.setPath("/");
              c.setMaxAge(0);
              response.addCookie(c);
            });
  }
}
