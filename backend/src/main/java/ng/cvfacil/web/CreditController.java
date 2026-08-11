package ng.cvfacil.web;

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

/**
 * Carteira de créditos do usuário logado — ativo apenas em produção (@Profile("!local")). Lógica
 * compartilhada com LocalCreditController em CreditRequestSupport.
 */
@RestController
@RequestMapping("/api/credits")
@Profile("!local")
public class CreditController {

  private final CreditService credits;

  public CreditController(CreditService credits) {
    this.credits = credits;
  }

  @GetMapping("/wallet")
  public ResponseEntity<WalletView> wallet(@AuthenticationPrincipal Jwt principal) {
    UUID userId = resolveUserId(principal);
    if (userId == null) return ResponseEntity.status(401).build();
    return CreditRequestSupport.wallet(credits, userId);
  }

  @PostMapping("/purchase")
  public ResponseEntity<Void> purchase(
      @Valid @RequestBody PurchaseRequest req, @AuthenticationPrincipal Jwt principal) {
    if (resolveUserId(principal) == null) return ResponseEntity.status(401).build();
    return CreditRequestSupport.purchase(credits);
  }

  private UUID resolveUserId(Jwt principal) {
    if (principal == null) return null;
    try {
      return UUID.fromString(principal.getSubject());
    } catch (Exception ignored) {
      return null;
    }
  }
}
