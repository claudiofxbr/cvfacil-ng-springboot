package ng.cvfacil.repository;

import ng.cvfacil.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
  AuditLog findTopByOrderByIdDesc();
}
