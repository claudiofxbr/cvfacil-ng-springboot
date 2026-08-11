package ng.cvfacil.web;

import ng.cvfacil.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints exclusivos da role ROOT_MASTER.
 * Em produção, este controller é servido em subdomínio admin.cvfacil.ng (PRD §4.4).
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ROOT_MASTER')")
public class AdminController {

  private final UserRepository users;

  public AdminController(UserRepository users) { this.users = users; }

  @GetMapping("/stats")
  public Stats stats() {
    return new Stats(users.count());
  }

  public record Stats(long totalUsers) {}
}
