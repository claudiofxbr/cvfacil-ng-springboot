package ng.cvfacil.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * BUG CORRIGIDO: o cookie {@code relink_state} (ver AuthController#startGoogleRelink) fica válido
 * por 5 minutos independente de qual navegação aconteça no navegador nesse intervalo — ele não está
 * amarrado a uma tentativa específica de autorização OAuth2 (diferente do cookie {@code
 * oauth2_auth_request} do {@link HttpCookieOAuth2AuthorizationRequestRepository}, que o Spring
 * Security correlaciona 1:1 com o "state" de cada ida-e-volta ao Google).
 *
 * <p>Consequência real: usuário A inicia a troca de conta Google (cookie {@code relink_state}
 * gravado) e desiste sem concluir no Google (fecha a aba, clica "voltar" etc.) — nos 5 minutos
 * seguintes, se QUALQUER autenticação/cadastro normal com Google acontecer nesse mesmo navegador
 * (ex.: um novo usuário faz "Entrar com Google" num computador compartilhado, ou o próprio usuário
 * A usa o botão de login normal em vez de tentar de novo a troca), {@link
 * OAuth2LoginSuccessHandler#consumeRelinkCookie} consome esse cookie remanescente e trata o login
 * como se fosse a troca de conta pendente de A — em vez de logar/cadastrar normalmente quem
 * autenticou, o e-mail da conta de A é sobrescrito pelo e-mail dessa outra autenticação. Isso é
 * exatamente o efeito relatado como "reset impede incluir novos logins com Gmail": o cadastro/login
 * normal nunca acontece, e some silenciosamente dentro do fluxo de troca de conta de outra pessoa.
 *
 * <p>Correção: o botão de login/cadastro normal com Google nunca envia {@code ?relink=1} (só o
 * fluxo de troca de conta faz isso — ver GoogleAccountSection#startRelink no frontend). Este filtro
 * intercepta {@code /oauth2/authorization/google} ANTES do redirect ao Google e apaga qualquer
 * {@code relink_state} remanescente sempre que a requisição não for explicitamente uma tentativa de
 * troca de conta — fechando a janela de contaminação cruzada na origem, sem alterar o fluxo de
 * troca de conta em si.
 *
 * <p>NOTA: o marcador é {@code relink}, não {@code prompt} — desde que o login/cadastro normal
 * também passou a enviar {@code ?prompt=select_account} (para deixar escolher uma conta Google
 * diferente da já ativa no navegador, em vez de reautenticar silenciosamente), o valor de {@code
 * prompt} deixou de diferenciar os dois fluxos.
 */
public class RelinkCookieGuardFilter extends OncePerRequestFilter {

  static final String AUTHORIZATION_PATH = "/oauth2/authorization/google";

  private final boolean cookieSecure;

  public RelinkCookieGuardFilter(boolean cookieSecure) {
    this.cookieSecure = cookieSecure;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (AUTHORIZATION_PATH.equals(request.getRequestURI())
        && !"1".equals(request.getParameter("relink"))) {
      Cookie expire = new Cookie("relink_state", "");
      expire.setPath("/");
      expire.setHttpOnly(true);
      expire.setSecure(cookieSecure);
      expire.setMaxAge(0);
      response.addCookie(expire);
    }
    filterChain.doFilter(request, response);
  }
}
