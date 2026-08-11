package ng.cvfacil.repository;

import java.util.List;
import java.util.UUID;
import ng.cvfacil.domain.Resume;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeRepository extends JpaRepository<Resume, UUID> {
  List<Resume> findByUserIdOrderByUpdatedAtDesc(UUID userId);
}
