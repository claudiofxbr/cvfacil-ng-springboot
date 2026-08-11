package ng.cvfacil.repository;

import java.util.List;
import java.util.UUID;
import ng.cvfacil.domain.PasswordHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, UUID> {

  List<PasswordHistory> findTop12ByUserIdOrderByCreatedAtDesc(UUID userId);
}
