package ng.cvfacil.repository;

import java.util.List;
import java.util.UUID;
import ng.cvfacil.domain.CreditTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, UUID> {
  List<CreditTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId);
  boolean existsByUserIdAndType(UUID userId, CreditTransaction.Type type);
}
