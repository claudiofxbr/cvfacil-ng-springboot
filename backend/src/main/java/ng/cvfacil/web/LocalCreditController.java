package ng.cvfacil.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import ng.cvfacil.dto.CreditDtos.PurchaseRequest;
import ng.cvfacil.dto.CreditDtos.WalletView;
import ng.cvfacil.service.CreditService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** Versão local de CreditController — ativa apenas em @Profile("local"), aceita STUB tokens. */
@RestController
@RequestMapping("/api/credits")
@Profile("local")
public class LocalCreditController {

  private final CreditService credits;

  public LocalCreditController(CreditService credits) {
    this.credits = credits;
  }

  @GetMapping("/wallet")
  public ResponseEntity<WalletView> wallet(
      @AuthenticationPrincipal Jwt principal, HttpServletRequest request) {
    UUID userId = resolveUserId(principal, request);
    if (userId == null) return ResponseEntity.status(401).build();
    return CreditRequestSupport.wallet(credits, userId);
  }

  @PostMapping("/purchase")
  public ResponseEntity<Void> purchase(
      @Valid @RequestBody PurchaseRequest req,
      @AuthenticationPrincipal Jwt principal,
      HttpServletRequest request) {
    if (resolveUserId(principal, request) == null) return ResponseEntity.status(401).build();
    return CreditRequestSupport.purchase(credits);
  }

  private UUID resolveUserId(Jwt principal, HttpServletRequest request) {
    if (principal != null) {
      try {
        return UUID.fromString(principal.getSubject());
      } catch (Exception ignored) {
      }
    }
    String auth = request.getHeader("Authorization");
    if (auth != null && auth.startsWith("Bearer STUB_ACCESS.")) {
      String[] parts = auth.substring("Bearer ".length()).split("\\.", 3);
      if (parts.length >= 2) {
        try {
          return UUID.fromString(parts[1]);
        } catch (Exception ignored) {
        }
      }
    }
    return null;
  }
}
