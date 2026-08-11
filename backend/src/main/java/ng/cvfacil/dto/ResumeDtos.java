package ng.cvfacil.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * DTOs de criação, atualização e visualização de currículos.
 *
 * <p>{@code content} trafega como String JSON — o backend cifra/decifra em
 * AES-256-GCM antes de persistir em {@code resumes.content_enc}.
 */
public class ResumeDtos {

  /**
   * Payload enviado pelo frontend ao criar ou atualizar um currículo.
   * {@code content} é o objeto JSON do currículo serializado como string.
   */
  public record ResumeRequest(
      @NotBlank @Size(max = 32) String layoutId,
      @Size(max = 10) String locale,
      @NotBlank String content,           // JSON serializado do currículo
      @Size(max = 500) String photoUrl
  ) {}

  /**
   * Representação do currículo enviada ao cliente.
   * {@code content} é o JSON decifrado — nunca os bytes crus de {@code content_enc}.
   */
  public record ResumeView(
      UUID id,
      String layoutId,
      String locale,
      int version,
      String content,     // JSON decifrado
      String photoUrl,
      Instant createdAt,
      Instant updatedAt
  ) {}
}
