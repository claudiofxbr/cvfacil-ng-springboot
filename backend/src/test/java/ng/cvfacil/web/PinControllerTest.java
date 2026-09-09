package ng.cvfacil.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import ng.cvfacil.domain.User;
import ng.cvfacil.dto.AuthDtos.ChangePinRequest;
import ng.cvfacil.dto.AuthDtos.LoginResponse;
import ng.cvfacil.dto.AuthDtos.PinSetupRequest;
import ng.cvfacil.dto.AuthDtos.PinStatusResponse;
import ng.cvfacil.dto.AuthDtos.PinVerifyRequest;
import ng.cvfacil.dto.AuthDtos.ResetPinRequest;
import ng.cvfacil.repository.UserRepository;
import ng.cvfacil.security.JwtService;
import ng.cvfacil.service.PinService;
import ng.cvfacil.service.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Testes unitários para PinController — cada endpoint resolve a identidade a partir do cookie de
 * desafio (nunca do corpo da requisição), então os testes montam esse cookie no formato STUB
 * (jwtDecoder não injetado aqui, mesmo padrão de profile "local").
 */
class PinControllerTest {

  private UserRepository users;
  private JwtService jwt;
  private PinService pinService;
  private RateLimitService rateLimit;
  private PinController controller;

  @BeforeEach
  void setup() {
    users = mock(UserRepository.class);
    jwt = mock(JwtService.class);
    pinService = mock(PinService.class);
    rateLimit = mock(RateLimitService.class);

    controller = new PinController(users, jwt, pinService, rateLimit);
    ReflectionTestUtils.setField(controller, "cookieSecure", true);

    when(rateLimit.allow(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
    when(pinService.isValidPin(anyString())).thenReturn(true);
    when(jwt.issueAccessToken(any())).thenReturn("access-token");
    when(jwt.issueRefreshToken(any())).thenReturn("STUB_REFRESH.token");
    when(jwt.refreshTtl()).thenReturn(Duration.ofDays(7));
  }

  private static int anyInt() {
    return org.mockito.ArgumentMatchers.anyInt();
  }

  private User user(UUID id) {
    User u = new User();
    ReflectionTestUtils.setField(u, "id", id);
    u.setEmail("teste@gmail.com");
    return u;
  }

  private MockHttpServletRequest requestWithCookie(String name, String value) {
    MockHttpServletRequest req = new MockHttpServletRequest();
    if (value != null) req.setCookies(new Cookie(name, value));
    return req;
  }

  // ---- status ----

  @Test
  void status_semCookieNenhum_retornaNone() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    ResponseEntity<PinStatusResponse> resp = controller.status(req);
    assertThat(resp.getBody().mode()).isEqualTo("none");
  }

  @Test
  void status_cookieSetupValido_retornaSetup() {
    UUID userId = UUID.randomUUID();
    MockHttpServletRequest req =
        requestWithCookie("pin_setup_state", "STUB_PINSETUP." + userId + "." + UUID.randomUUID());
    assertThat(controller.status(req).getBody().mode()).isEqualTo("setup");
  }

  @Test
  void status_cookieVerifyValido_retornaVerify() {
    UUID userId = UUID.randomUUID();
    MockHttpServletRequest req =
        requestWithCookie("pin_verify_state", "STUB_PINVERIFY." + userId + "." + UUID.randomUUID());
    assertThat(controller.status(req).getBody().mode()).isEqualTo("verify");
  }

  // ---- setup ----

  @Test
  void setup_pinEConfirmacaoDiferentes_retorna422SemConsumirPinService() {
    MockHttpServletRequest req =
        requestWithCookie(
            "pin_setup_state", "STUB_PINSETUP." + UUID.randomUUID() + "." + UUID.randomUUID());
    MockHttpServletResponse resp = new MockHttpServletResponse();

    ResponseEntity<?> result =
        controller.setup(new PinSetupRequest("12345678", "87654321"), req, resp);

    assertThat(result.getStatusCode().value()).isEqualTo(422);
    verify(pinService, never()).setupPin(any(), any(), any(), any());
  }

  @Test
  void setup_semCookie_retorna401() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    MockHttpServletResponse resp = new MockHttpServletResponse();

    ResponseEntity<?> result =
        controller.setup(new PinSetupRequest("12345678", "12345678"), req, resp);

    assertThat(result.getStatusCode().value()).isEqualTo(401);
  }

  @Test
  void setup_sucesso_criaPinEEmiteSessao() {
    UUID userId = UUID.randomUUID();
    User u = user(userId);
    when(users.findById(userId)).thenReturn(Optional.of(u));

    MockHttpServletRequest req =
        requestWithCookie("pin_setup_state", "STUB_PINSETUP." + userId + "." + UUID.randomUUID());
    MockHttpServletResponse resp = new MockHttpServletResponse();

    ResponseEntity<?> result =
        controller.setup(new PinSetupRequest("12345678", "12345678"), req, resp);

    assertThat(result.getStatusCode().value()).isEqualTo(200);
    assertThat(((LoginResponse) result.getBody()).accessToken()).isEqualTo("access-token");
    verify(pinService).setupPin(eq(u), eq("12345678"), any(), any());
    // cookie de desafio deve ser apagado (uso único) mesmo em caso de sucesso
    Cookie expired = resp.getCookie("pin_setup_state");
    assertThat(expired).isNotNull();
    assertThat(expired.getMaxAge()).isZero();
  }

  @Test
  void setup_contaJaTemPin_retorna409() {
    UUID userId = UUID.randomUUID();
    User u = user(userId);
    u.setPinHash("ja-tem-hash");
    when(users.findById(userId)).thenReturn(Optional.of(u));

    MockHttpServletRequest req =
        requestWithCookie("pin_setup_state", "STUB_PINSETUP." + userId + "." + UUID.randomUUID());
    MockHttpServletResponse resp = new MockHttpServletResponse();

    ResponseEntity<?> result =
        controller.setup(new PinSetupRequest("12345678", "12345678"), req, resp);

    assertThat(result.getStatusCode().value()).isEqualTo(409);
    verify(pinService, never()).setupPin(any(), any(), any(), any());
  }

  // ---- verify ----

  @Test
  void verify_pinCorreto_emiteSessaoEApagaCookie() {
    UUID userId = UUID.randomUUID();
    User u = user(userId);
    u.setPinHash("hash-qualquer");
    when(users.findById(userId)).thenReturn(Optional.of(u));
    when(pinService.isLocked(u)).thenReturn(false);
    when(pinService.verifyPin(eq(u), eq("12345678"), any(), any())).thenReturn(true);

    MockHttpServletRequest req =
        requestWithCookie("pin_verify_state", "STUB_PINVERIFY." + userId + "." + UUID.randomUUID());
    MockHttpServletResponse resp = new MockHttpServletResponse();

    ResponseEntity<?> result = controller.verify(new PinVerifyRequest("12345678"), req, resp);

    assertThat(result.getStatusCode().value()).isEqualTo(200);
    Cookie expired = resp.getCookie("pin_verify_state");
    assertThat(expired).isNotNull();
    assertThat(expired.getMaxAge()).isZero();
  }

  @Test
  void verify_pinErrado_retorna401ComTentativasRestantes() {
    UUID userId = UUID.randomUUID();
    User u = user(userId);
    u.setPinHash("hash-qualquer");
    u.setPinFailedAttempts(2); // já errou 2 antes desta tentativa
    when(users.findById(userId)).thenReturn(Optional.of(u));
    when(pinService.isLocked(u)).thenReturn(false);
    when(pinService.verifyPin(eq(u), eq("00000000"), any(), any()))
        .thenAnswer(
            inv -> {
              u.setPinFailedAttempts(3); // simula o incremento que o service real faria
              return false;
            });

    MockHttpServletRequest req =
        requestWithCookie("pin_verify_state", "STUB_PINVERIFY." + userId + "." + UUID.randomUUID());
    MockHttpServletResponse resp = new MockHttpServletResponse();

    ResponseEntity<?> result = controller.verify(new PinVerifyRequest("00000000"), req, resp);

    assertThat(result.getStatusCode().value()).isEqualTo(401);
    // cookie continua valendo — ainda há tentativas restantes
    assertThat(resp.getCookie("pin_verify_state")).isNull();
  }

  @Test
  void verify_contaBloqueada_retorna423EApagaCookie() {
    UUID userId = UUID.randomUUID();
    User u = user(userId);
    u.setPinHash("hash-qualquer");
    when(users.findById(userId)).thenReturn(Optional.of(u));
    when(pinService.isLocked(u)).thenReturn(true);

    MockHttpServletRequest req =
        requestWithCookie("pin_verify_state", "STUB_PINVERIFY." + userId + "." + UUID.randomUUID());
    MockHttpServletResponse resp = new MockHttpServletResponse();

    ResponseEntity<?> result = controller.verify(new PinVerifyRequest("12345678"), req, resp);

    assertThat(result.getStatusCode().value()).isEqualTo(423);
    verify(pinService, never()).verifyPin(any(), any(), any(), any());
    Cookie expired = resp.getCookie("pin_verify_state");
    assertThat(expired).isNotNull();
    assertThat(expired.getMaxAge()).isZero();
  }

  // ---- changePin (self-service, autenticado) ----

  @Test
  void changePin_autenticadoViaStub_delegaAoPinService() {
    UUID userId = UUID.randomUUID();
    User u = user(userId);
    when(users.findById(userId)).thenReturn(Optional.of(u));
    when(pinService.changePin(eq(u), eq("12345678"), eq("87654321"), any(), any()))
        .thenReturn(true);

    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addHeader("Authorization", "Bearer STUB_ACCESS." + userId + ".x");

    ResponseEntity<Void> result =
        controller.changePin(new ChangePinRequest("12345678", "87654321"), null, req);

    assertThat(result.getStatusCode().value()).isEqualTo(200);
  }

  // ---- reset (público, por token) ----

  @Test
  void reset_delegaAoPinServiceERetorna200QuandoValido() {
    when(pinService.resetPin("token-valido")).thenReturn(true);
    ResponseEntity<Void> result = controller.reset(new ResetPinRequest("token-valido"));
    assertThat(result.getStatusCode().value()).isEqualTo(200);
  }

  @Test
  void reset_tokenInvalido_retorna400() {
    when(pinService.resetPin("token-invalido")).thenReturn(false);
    ResponseEntity<Void> result = controller.reset(new ResetPinRequest("token-invalido"));
    assertThat(result.getStatusCode().value()).isEqualTo(400);
  }
}
