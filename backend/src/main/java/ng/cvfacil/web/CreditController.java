package ng.cvfacil.web;

import jakarta.validation.Valid;
import java.util.UUID;
import ng.cvfacil.dto.CreditDtos.PurchaseRequest;
import ng.cvfacil.dto.CreditDtos.PurchaseResponse;
import ng.cvfacil.dto.CreditDtos.WalletView;
import ng.cvfacil.repository.UserRepository;
import ng.cvfacil.service.CreditService;
import ng.cvfacil.service.PagSeguroClient;
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
  private final PagSeguroClient pagSeguro;
  private final UserRepository users;

  public CreditController(CreditService credits, PagSeguroClient pagSeguro, UserRepository users) {
    this.credits = credits;
    this.pagSeguro = pagSeguro;
    this.users = users;
  }

  @GetMapping("/wallet")
  public ResponseEntity<WalletView> wallet(@AuthenticationPrincipal Jwt principal) {
    UUID userId = resolveUserId(principal);
    if (userId == null) return ResponseEntity.status(401).build();
    return CreditRequestSupport.wallet(credits, userId);
  }

  @PostMapping("/purchase")
  public ResponseEntity<PurchaseResponse> purchase(
      @Valid @RequestBody PurchaseRequest req, @AuthenticationPrincipal Jwt principal) {
    UUID userId = resolveUserId(principal);
    if (userId == null) return ResponseEntity.status(401).build();
    return CreditRequestSupport.purchase(credits, pagSeguro, users, userId, req);
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
