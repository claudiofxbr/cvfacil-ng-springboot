package ng.cvfacil.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public class AuthDtos {

  public record LoginRequest(
      @Email @NotBlank String email, @NotBlank @Size(min = 10, max = 256) String password) {}

  public record RegisterRequest(
      @Email @NotBlank String email,
      @NotBlank
          @Size(min = 10, max = 256)
          @Pattern(
              regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
              message = "Senha deve incluir maiúsculas, minúsculas, dígitos e símbolos")
          String password,
      String displayName,
      @Pattern(regexp = "pt-BR|en-US|es-ES") String locale,
      @AssertTrue(message = "É necessário aceitar os Termos de Uso e a Política de Privacidade")
          boolean termsAccepted) {}

  public record ForgotPasswordRequest(@Email @NotBlank String email) {}

  public record ResetPasswordRequest(
      @NotBlank String token,
      @NotBlank
          @Size(min = 10, max = 256)
          @Pattern(
              regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
              message = "Senha deve incluir maiúsculas, minúsculas, dígitos e símbolos")
          String password) {}

  public record ChangePasswordRequest(
      @NotBlank String currentPassword,
      @NotBlank
          @Size(min = 10, max = 256)
          @Pattern(
              regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
              message = "Senha deve incluir maiúsculas, minúsculas, dígitos e símbolos")
          String newPassword) {}

  public record VerifyPasswordRequest(@NotBlank String password) {}

  public record MfaVerifyRequest(
      @NotBlank String challengeToken, @NotBlank @Pattern(regexp = "\\d{6}") String code) {}

  public record MfaChallengeResponse(boolean mfaRequired, String challengeToken) {}

  public record LoginResponse(UserView user, String accessToken) {}

  public record UserView(
      UUID id, String email, String displayName, String role, String locale, boolean mfaEnabled) {}

  public record SecurityStatus(
      boolean mfaEnabled, long passwordAgeDays, boolean rotationOverdue, String role) {}
}
