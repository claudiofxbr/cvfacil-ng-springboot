package ng.cvfacil.service;

/**
 * Falha na chamada ao provedor de IA externo (OpenAI/Anthropic/Gemini) — chave invalida, modelo
 * indisponivel, rate limit, resposta bloqueada por filtro de seguranca, etc.
 *
 * <p>Ao contrario de uma falha interna do servidor, o cliente pode agir sobre isso (tentar
 * novamente, usar o parser client-side) — por isso carrega um httpStatus proprio (502/503) e a
 * mensagem especifica precisa chegar ao usuario, nunca ser descartada pelo catch-all generico de
 * {@code AIImportRequestSupport}.
 */
public class AIProviderException extends RuntimeException {

  private final int httpStatus;

  public AIProviderException(String message, int httpStatus) {
    super(message);
    this.httpStatus = httpStatus;
  }

  public AIProviderException(String message, int httpStatus, Throwable cause) {
    super(message, cause);
    this.httpStatus = httpStatus;
  }

  public int getHttpStatus() {
    return httpStatus;
  }
}
