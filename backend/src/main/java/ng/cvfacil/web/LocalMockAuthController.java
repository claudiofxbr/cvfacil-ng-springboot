package ng.cvfacil.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import ng.cvfacil.domain.User;
import ng.cvfacil.dto.AuthDtos.LoginResponse;
import ng.cvfacil.dto.AuthDtos.UserView;
import ng.cvfacil.repository.UserRepository;
import ng.cvfacil.security.JwtService;
import ng.cvfacil.service.AuditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints disponíveis SOMENTE no profile "local".
 *
 * Simula o fluxo de autenticação Google sem precisar de credenciais OAuth reais.
 * Útil em desenvolvimento para testar login social sem acesso ao Google Cloud Console.
 *
 * Fluxo:
 *   1. Frontend clica em "Entrar com Google".
 *   2. Backend intercepta /oauth2/authorization/google (credenciais dummy) e
 *      redireciona para http://localhost:3000/login?mock_oauth=true.
 *   3. Frontend detecta o param e exibe um formulário simples pedindo o e-mail.
 *   4. Usuário informa um e-mail qualquer e clica em "Continuar".
 *   5. Frontend chama POST /api/auth/mock-google com o e-mail.
 *   6. Este controller cria o usuário (se ainda não existir) e emite tokens normais.
 */
@RestController
@RequestMapping("/api/auth")
@Profile("local")
public class LocalMockAuthController {

    private final UserRepository users;
    private final JwtService jwt;
    private final AuditService audit;

    @Value("${cvfacil.security.cookie-secure:true}")
    private boolean cookieSecure;

    public LocalMockAuthController(UserRepository users, JwtService jwt, AuditService audit) {
        this.users = users;
        this.jwt = jwt;
        this.audit = audit;
    }

    public record MockGoogleRequest(@Email @NotBlank String email) {}

    @PostMapping("/mock-google")
    public ResponseEntity<LoginResponse> mockGoogle(
            @Valid @RequestBody MockGoogleRequest req,
            HttpServletRequest http) {

        // Busca ou cria o usuário com este e-mail (idempotente).
        User u = users.findByEmailIgnoreCase(req.email()).orElseGet(() -> {
            User newUser = new User();
            newUser.setEmail(req.email());
            // displayName extraído da parte local do e-mail (ex: "joao.silva@..." → "joao.silva")
            String name = req.email().split("@")[0].replace('.', ' ').replace('_', ' ');
            newUser.setDisplayName(capitalize(name));
            newUser.setLocale("pt-BR");
            // Sem passwordHash — conta somente-OAuth
            return users.save(newUser);
        });

        String access  = jwt.issueAccessToken(u);
        String refresh = jwt.issueRefreshToken(u.getId());

        audit.record(u.getId(), "MOCK_GOOGLE_LOGIN",
                http.getRemoteAddr(), http.getHeader("User-Agent"), null);

        ResponseCookie cookie = ResponseCookie.from("refresh_token", refresh)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/")
                .maxAge(jwt.refreshTtl())
                .build();

        return ResponseEntity.ok()
                .header("Set-Cookie", cookie.toString())
                .body(new LoginResponse(view(u), access));
    }

    // ---- helpers ----

    private UserView view(User u) {
        return new UserView(u.getId(), u.getEmail(), u.getDisplayName(),
                u.getRole().name(), u.getLocale());
    }

    /** Capitaliza cada palavra (ex: "joao silva" → "Joao Silva"). */
    private static String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        String[] words = s.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0)));
            if (w.length() > 1) sb.append(w.substring(1).toLowerCase());
        }
        return sb.toString();
    }
}
