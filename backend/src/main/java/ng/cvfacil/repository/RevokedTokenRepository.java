package ng.cvfacil.repository;

import java.time.Instant;
import java.util.UUID;
import ng.cvfacil.domain.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, UUID> {

  @Modifying
  @Query("DELETE FROM RevokedToken t WHERE t.expiresAt < :now")
  int deleteByExpiresAtBefore(Instant now);
}
