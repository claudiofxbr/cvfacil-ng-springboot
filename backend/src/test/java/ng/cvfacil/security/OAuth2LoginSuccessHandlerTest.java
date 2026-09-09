package ng.cvfacil.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import ng.cvfacil.domain.User;
import ng.cvfacil.repository.UserRepository;
import ng.cvfacil.service.AuditService;
import ng.cvfacil.service.CreditService;
import ng.cvfacil.service.TokenRevocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Testes unitários para OAuth2LoginSuccessHandler — cobre provisionamento de usuário novo, promoção
 * de emailVerified para conta pré-existente (bug corrigido nesta sessão), fallback de displayName,
 * ausência de e-mail e case-insensitivity do lookup por e-mail.
 */
class OAuth2LoginSuccessHandlerTest {

  private UserRepository users;
  private JwtService jwt;
  private CreditService credits;
  private AuditService audit;
  private TokenRevocationService revocation;
  private HttpServletRequest request;
  private HttpServletResponse response;
  private OAuth2LoginSuccessHandler handler;

  private static final String FRONTEND_BASE_URL = "https://cvfacil.ng";

  @BeforeEach
  void setup() {
    users = mock(UserRepository.class);
    jwt = mock(JwtService.class);
    credits = mock(CreditService.class);
    audit = mock(AuditService.class);
    revocation = mock(TokenRevocationService.class);
    request = mock(HttpServletRequest.class);
    response = mock(HttpServletResponse.class);

    handler = new OAuth2LoginSuccessHandler(users, jwt, credits, audit, revocation);
    ReflectionTestUtils.setField(handler, "frontendBaseUrl", FRONTEND_BASE_URL);
    ReflectionTestUtils.setField(handler, "cookieSecure", true);

    when(jwt.issueRefreshToken(any())).thenReturn("STUB_REFRESH.token");
    when(jwt.refreshTtl()).thenReturn(java.time.Duration.ofDays(7));
    when(jwt.issuePinSetupToken(any())).thenReturn("STUB_PINSETUP.token");
    when(jwt.issuePinVerifyToken(any())).thenReturn("STUB_PINVERIFY.token");
    when(request.getRemoteAddr()).thenReturn("203.0.113.10");
    when(request.getHeader("User-Agent")).thenReturn("JUnit-Agent");

    // save() por padrão devolve o mesmo objeto recebido (comportamento comum de
    // JpaRepository.save quando a entidade já tem os campos setados)
    when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  private OAuth2User oauth2User(String email, String name) {
    Map<String, Object> attrs = new java.util.HashMap<>();
    attrs.put("sub", "google-subject-id");
    if (email != null) attrs.put("email", email);
    if (name != null) attrs.put("name", name);
    return new DefaultOAuth2User(
        java.util.List.of(
            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")),
        attrs,
        "sub");
  }

  private Authentication authenticationFor(OAuth2User principal) {
    return new TestingAuthenticationToken(principal, null);
  }

  // ─── 1. Usuário novo ──────────────────────────────────────────────────────

  @Test
  void novoUsuario_criaContaComEmailVerificadoESemPasswordHash() throws Exception {
    String email = "novo.usuario@gmail.com";
    when(users.findByEmailIgnoreCase(email)).thenReturn(Optional.empty());

    OAuth2User principal = oauth2User(email, "Novo Usuario");
    handler.onAuthenticationSuccess(request, response, authenticationFor(principal));

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(users).save(captor.capture());
    User saved = captor.getValue();

    assertThat(saved.getEmail()).isEqualTo(email);
    assertThat(saved.getDisplayName()).isEqualTo("Novo Usuario");
    assertThat(saved.isEmailVerified()).isTrue();
    assertThat(saved.getPasswordHash()).isNull();
    assertThat(saved.getLocale()).isEqualTo("pt-BR");

    verify(credits).grantCourtesyIfEligible(any());
    verify(audit)
        .record(any(), eq("LOGIN_GOOGLE_SUCCESS"), eq("203.0.113.10"), eq("JUnit-Agent"), isNull());

    // BUG DE SEGURANÇA CORRIGIDO: nenhuma sessão (refresh_token) é emitida direto — mesmo conta
    // nova precisa passar pelo portão de PIN (aqui, "setup" já que pinHash é null por padrão).
    ArgumentCaptor<String> cookieCaptor = ArgumentCaptor.forClass(String.class);
    verify(response).addHeader(eq("Set-Cookie"), cookieCaptor.capture());
    String cookieHeader = cookieCaptor.getValue();
    assertThat(cookieHeader).contains("pin_setup_state=");
    assertThat(cookieHeader).containsIgnoringCase("HttpOnly");
    assertThat(cookieHeader).doesNotContain("refresh_token=");

    verify(response).sendRedirect(FRONTEND_BASE_URL + "/oauth2/pin");
  }

  // ─── 2. Usuário existente com emailVerified=false (bug corrigido) ─────────

  @Test
  void usuarioExistenteNaoVerificado_promoveEmailVerifiedParaTrue() throws Exception {
    String email = "pendente@gmail.com";
    User existing = new User();
    ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());
    existing.setEmail(email);
    existing.setDisplayName("Pendente");
    existing.setEmailVerified(false);
    existing.setPasswordHash("bcrypt-hash-de-senha-local");

    when(users.findByEmailIgnoreCase(email)).thenReturn(Optional.of(existing));

    OAuth2User principal = oauth2User(email, "Pendente");
    handler.onAuthenticationSuccess(request, response, authenticationFor(principal));

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(users).save(captor.capture());
    assertThat(captor.getValue().isEmailVerified()).isTrue();
    // conta local pré-existente não deve ser transformada em OAuth-only
    assertThat(captor.getValue().getPasswordHash()).isEqualTo("bcrypt-hash-de-senha-local");

    // não é conta nova — sem crédito de cortesia
    verify(credits, never()).grantCourtesyIfEligible(any());
    verify(response).sendRedirect(FRONTEND_BASE_URL + "/oauth2/pin");
  }

  // ─── 3. Usuário existente já verificado ───────────────────────────────────

  @Test
  void usuarioExistenteJaVerificado_naoSalvaNovamente() throws Exception {
    String email = "verificado@gmail.com";
    User existing = new User();
    ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());
    existing.setEmail(email);
    existing.setDisplayName("Verificado");
    existing.setEmailVerified(true);

    when(users.findByEmailIgnoreCase(email)).thenReturn(Optional.of(existing));

    OAuth2User principal = oauth2User(email, "Verificado");
    handler.onAuthenticationSuccess(request, response, authenticationFor(principal));

    // já estava verificado e já existia — o handler não deve chamar save() (nem
    // para criação nem para a promoção de emailVerified)
    verify(users, never()).save(any());
    verify(credits, never()).grantCourtesyIfEligible(any());
    // pinHash é null por padrão nesse User de teste -> ainda cai no portão de "setup"
    verify(response)
        .addHeader(eq("Set-Cookie"), org.mockito.ArgumentMatchers.contains("pin_setup_state="));
    verify(response).sendRedirect(FRONTEND_BASE_URL + "/oauth2/pin");
  }

  @Test
  void usuarioComPinJaCadastrado_caiNoPortaoDeVerificacaoNaoDeSetup() throws Exception {
    String email = "com.pin@gmail.com";
    User existing = new User();
    ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());
    existing.setEmail(email);
    existing.setDisplayName("Com Pin");
    existing.setEmailVerified(true);
    existing.setPinHash("bcrypt-hash-do-pin");

    when(users.findByEmailIgnoreCase(email)).thenReturn(Optional.of(existing));

    OAuth2User principal = oauth2User(email, "Com Pin");
    handler.onAuthenticationSuccess(request, response, authenticationFor(principal));

    verify(response)
        .addHeader(eq("Set-Cookie"), org.mockito.ArgumentMatchers.contains("pin_verify_state="));
    verify(response).sendRedirect(FRONTEND_BASE_URL + "/oauth2/pin");
  }

  // ─── 4. name ausente/vazio: fallback para parte antes do @ ────────────────

  @Test
  void nomeAusente_usaFallbackDaParteAntesDoArroba() throws Exception {
    String email = "sem.nome@gmail.com";
    when(users.findByEmailIgnoreCase(email)).thenReturn(Optional.empty());

    OAuth2User principal = oauth2User(email, null);
    handler.onAuthenticationSuccess(request, response, authenticationFor(principal));

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(users).save(captor.capture());
    assertThat(captor.getValue().getDisplayName()).isEqualTo("sem.nome");
  }

  @Test
  void nomeVazio_usaFallbackDaParteAntesDoArroba() throws Exception {
    String email = "outro.sem.nome@gmail.com";
    when(users.findByEmailIgnoreCase(email)).thenReturn(Optional.empty());

    OAuth2User principal = oauth2User(email, "   ");
    handler.onAuthenticationSuccess(request, response, authenticationFor(principal));

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(users).save(captor.capture());
    assertThat(captor.getValue().getDisplayName()).isEqualTo("outro.sem.nome");
  }

  // ─── 5. email nulo ou vazio ────────────────────────────────────────────────

  @Test
  void emailNulo_redirecionaParaErroENaoCriaUsuario() throws Exception {
    OAuth2User principal = oauth2User(null, "Sem Email");
    handler.onAuthenticationSuccess(request, response, authenticationFor(principal));

    verify(users, never()).findByEmailIgnoreCase(any());
    verify(users, never()).save(any());
    verify(response).sendRedirect(FRONTEND_BASE_URL + "/login?error=oauth_no_email");
    verifyNoInteractions(jwt, credits, audit);
  }

  @Test
  void emailVazio_redirecionaParaErroENaoCriaUsuario() throws Exception {
    OAuth2User principal = oauth2User("", "Sem Email");
    handler.onAuthenticationSuccess(request, response, authenticationFor(principal));

    verify(users, never()).findByEmailIgnoreCase(any());
    verify(users, never()).save(any());
    verify(response).sendRedirect(FRONTEND_BASE_URL + "/login?error=oauth_no_email");
    verifyNoInteractions(jwt, credits, audit);
  }

  // ─── 6. Case-insensitivity do lookup por e-mail ───────────────────────────

  @Test
  void emailComCapitalizacaoDiferente_respeitaFindByEmailIgnoreCaseSemDuplicar() throws Exception {
    String googleEmail = "Claudio.Xavier@Gmail.com";
    String storedEmail = "claudio.xavier@gmail.com";

    User existing = new User();
    ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());
    existing.setEmail(storedEmail);
    existing.setDisplayName("Claudio Xavier");
    existing.setEmailVerified(true);

    when(users.findByEmailIgnoreCase(googleEmail)).thenReturn(Optional.of(existing));

    OAuth2User principal = oauth2User(googleEmail, "Claudio Xavier");
    handler.onAuthenticationSuccess(request, response, authenticationFor(principal));

    verify(users).findByEmailIgnoreCase(googleEmail);
    // já verificado -> não deve salvar (comportamento do caso 3), e sobretudo
    // nunca deve criar um segundo usuário via save() com um User novo
    verify(users, never()).save(any());
    verify(response).sendRedirect(FRONTEND_BASE_URL + "/oauth2/pin");
  }

  // ─── 7. Troca de conta Google (relink) ────────────────────────────────────
  // jwtDecoder não é injetado neste teste unitário (permanece null, como em profile "local"),
  // então o cookie relink_state usa o formato STUB — mesmo padrão já usado por refresh/mfa tokens.

  private jakarta.servlet.http.Cookie[] relinkCookie(UUID userId) {
    return new jakarta.servlet.http.Cookie[] {
      new jakarta.servlet.http.Cookie(
          "relink_state", "STUB_RELINK." + userId + "." + UUID.randomUUID())
    };
  }

  @Test
  void relink_trocaEmailDaContaERevogaSessaoAnterior() throws Exception {
    UUID userId = UUID.randomUUID();
    User current = new User();
    ReflectionTestUtils.setField(current, "id", userId);
    current.setEmail("conta.antiga@gmail.com");
    current.setEmailVerified(true);

    when(request.getCookies()).thenReturn(relinkCookie(userId));
    when(users.findById(userId)).thenReturn(Optional.of(current));
    when(users.findByEmailIgnoreCase("conta.nova@gmail.com")).thenReturn(Optional.empty());

    OAuth2User principal = oauth2User("conta.nova@gmail.com", "Conta Nova");
    handler.onAuthenticationSuccess(request, response, authenticationFor(principal));

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(users).save(captor.capture());
    assertThat(captor.getValue().getEmail()).isEqualTo("conta.nova@gmail.com");
    assertThat(captor.getValue().isEmailVerified()).isTrue();

    // apaga o cookie de relink (uso único) e emite um refresh_token novo
    verify(response).addCookie(any());
    verify(response).addHeader(eq("Set-Cookie"), contains("refresh_token="));

    verify(audit)
        .record(
            eq(userId),
            eq("GOOGLE_ACCOUNT_RELINKED"),
            any(),
            any(),
            contains("conta.nova@gmail.com"));
    verify(response).sendRedirect(FRONTEND_BASE_URL + "/dashboard/security?relink=success");
    // sem jwtDecoder (profile local) não há como decodificar o refresh_token antigo — não revoga
    verify(revocation, never()).revoke(any(), any());
  }

  @Test
  void relink_emailJaPertenceAOutraConta_rejeitaSemAlterarNada() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID outroUserId = UUID.randomUUID();
    User current = new User();
    ReflectionTestUtils.setField(current, "id", userId);
    current.setEmail("conta.antiga@gmail.com");

    User outraConta = new User();
    ReflectionTestUtils.setField(outraConta, "id", outroUserId);
    outraConta.setEmail("ja.cadastrado@gmail.com");

    when(request.getCookies()).thenReturn(relinkCookie(userId));
    when(users.findById(userId)).thenReturn(Optional.of(current));
    when(users.findByEmailIgnoreCase("ja.cadastrado@gmail.com"))
        .thenReturn(Optional.of(outraConta));

    OAuth2User principal = oauth2User("ja.cadastrado@gmail.com", "Ja Cadastrado");
    handler.onAuthenticationSuccess(request, response, authenticationFor(principal));

    verify(users, never()).save(any());
    verify(response)
        .sendRedirect(FRONTEND_BASE_URL + "/dashboard/security?relink=error&reason=in_use");
    verifyNoInteractions(audit);
  }

  @Test
  void relink_paraOMesmoEmailJaVinculado_naoConflitaComSiMesmo() throws Exception {
    UUID userId = UUID.randomUUID();
    User current = new User();
    ReflectionTestUtils.setField(current, "id", userId);
    current.setEmail("mesma.conta@gmail.com");

    when(request.getCookies()).thenReturn(relinkCookie(userId));
    when(users.findById(userId)).thenReturn(Optional.of(current));
    when(users.findByEmailIgnoreCase("mesma.conta@gmail.com")).thenReturn(Optional.of(current));

    OAuth2User principal = oauth2User("mesma.conta@gmail.com", "Mesma Conta");
    handler.onAuthenticationSuccess(request, response, authenticationFor(principal));

    verify(users).save(any());
    verify(response).sendRedirect(FRONTEND_BASE_URL + "/dashboard/security?relink=success");
  }
}
