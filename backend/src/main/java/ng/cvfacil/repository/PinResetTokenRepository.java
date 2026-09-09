package ng.cvfacil.repository;

import java.util.Optional;
import java.util.UUID;
import ng.cvfacil.domain.PinResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PinResetTokenRepository extends JpaRepository<PinResetToken, UUID> {
  Optional<PinResetToken> findByTokenHash(String tokenHash);
}
