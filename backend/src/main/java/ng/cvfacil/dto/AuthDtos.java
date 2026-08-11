package ng.cvfacil.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public class AuthDtos {

  public record LoginRequest(
      @Email @NotBlank String email,
      @NotBlank @Size(min = 10, max = 256) String password) {}

  public record RegisterRequest(
      @Email @NotBlank String email,
      @NotBlank
          @Size(min = 10, max = 256)
          @Pattern(
              regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
              message = "Senha deve incluir maiúsculas, minúsculas, dígitos e símbolos")
          String password,
      String displayName,
      @Pattern(regexp = "pt-BR|en-US|es-ES") String locale) {}

  public record ForgotPasswordRequest(@Email @NotBlank String email) {}

  public record ResetPasswordRequest(
      @NotBlank String token,
      @NotBlank
          @Size(min = 10, max = 256)
          @Pattern(
              regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
              message = "Senha deve incluir maiúsculas, minúsculas, dígitos e símbolos")
          String password) {}

  public record LoginResponse(UserView user, String accessToken) {}

  public record UserView(UUID id, String email, String displayName, String role, String locale) {}
}
