package ng.cvfacil.repository;

import java.util.Optional;
import java.util.UUID;
import ng.cvfacil.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, UUID> {
  Optional<User> findByEmailIgnoreCase(String email);
  boolean existsByEmailIgnoreCase(String email);

  /**
   * Decremento atômico de 1 crédito — UPDATE ... WHERE credits >= 1 é atômico
   * a nível de linha no Postgres, sem precisar de lock explícito (mesmo
   * princípio usado para corrigir a race condition do audit log). Retorna 0
   * linhas afetadas quando o saldo é insuficiente (nunca fica negativo).
   */
  @Modifying
  @Query("UPDATE User u SET u.credits = u.credits - 1 WHERE u.id = :id AND u.credits >= 1")
  int decrementOneCreditIfAvailable(UUID id);

  @Modifying
  @Query("UPDATE User u SET u.credits = u.credits + :amount WHERE u.id = :id")
  int incrementCredits(UUID id, int amount);
}
