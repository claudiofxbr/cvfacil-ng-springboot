package ng.cvfacil.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RelinkCookieGuardFilterTest {

  private final RelinkCookieGuardFilter filter = new RelinkCookieGuardFilter(true);

  @Test
  void loginNormalSemPromptApagaRelinkStateRemanescente() throws Exception {
    MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/oauth2/authorization/google");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilterInternal(request, response, chain);

    Cookie expired = response.getCookie("relink_state");
    assertThat(expired).isNotNull();
    assertThat(expired.getMaxAge()).isZero();
    verify(chain).doFilter(request, response);
  }

  @Test
  void trocaDeContaComPromptSelectAccountNaoMexeNoCookie() throws Exception {
    MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/oauth2/authorization/google");
    request.setParameter("prompt", "select_account");
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    filter.doFilterInternal(request, response, chain);

    verifyNoInteractions(response);
    verify(chain).doFilter(request, response);
  }

  @Test
  void requisicaoParaOutroPathNaoEAfetada() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/login");
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);

    filter.doFilterInternal(request, response, chain);

    verifyNoInteractions(response);
    verify(chain).doFilter(request, response);
  }
}
