package ng.cvfacil.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * BUG CORRIGIDO: o fluxo de troca de conta Google (AuthController#startGoogleRelink) redireciona o
 * navegador para {@code /oauth2/authorization/google?prompt=select_account} para forçar o Google a
 * exibir o seletor de contas em vez de logar direto na conta já ativa no navegador — mas o {@link
 * DefaultOAuth2AuthorizationRequestResolver} do Spring Security ignora parâmetros de query
 * arbitrários da requisição de entrada; ele nunca chegava a ser enviado ao Google, então o
 * "reset"/troca de conta na prática relogava sempre na mesma conta já autenticada no navegador.
 *
 * <p>Este resolver decora o resolver padrão e, quando a requisição de entrada traz {@code
 * ?prompt=...}, repassa esse valor como additional parameter da {@link OAuth2AuthorizationRequest}
 * — é assim, e não via propriedades estáticas do client registration, que se chega à URL final do
 * Google (que usa {@code prompt} para decidir se reexibe o seletor de contas).
 */
public class PromptAwareAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

  private final OAuth2AuthorizationRequestResolver delegate;

  public PromptAwareAuthorizationRequestResolver(
      ClientRegistrationRepository clientRegistrationRepository,
      String authorizationRequestBaseUri) {
    this.delegate =
        new DefaultOAuth2AuthorizationRequestResolver(
            clientRegistrationRepository, authorizationRequestBaseUri);
  }

  @Override
  public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
    return withPrompt(delegate.resolve(request), request);
  }

  @Override
  public OAuth2AuthorizationRequest resolve(
      HttpServletRequest request, String clientRegistrationId) {
    return withPrompt(delegate.resolve(request, clientRegistrationId), request);
  }

  private OAuth2AuthorizationRequest withPrompt(
      OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request) {
    if (authorizationRequest == null) return null;
    String prompt = request.getParameter("prompt");
    if (prompt == null || prompt.isBlank()) return authorizationRequest;

    Map<String, Object> additionalParameters =
        new LinkedHashMap<>(authorizationRequest.getAdditionalParameters());
    additionalParameters.put("prompt", prompt);
    return OAuth2AuthorizationRequest.from(authorizationRequest)
        .additionalParameters(additionalParameters)
        .build();
  }
}
