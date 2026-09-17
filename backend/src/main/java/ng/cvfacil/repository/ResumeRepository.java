package ng.cvfacil.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ng.cvfacil.domain.Resume;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeRepository extends JpaRepository<Resume, UUID> {
  List<Resume> findByUserIdOrderByUpdatedAtDesc(UUID userId);

  List<Resume> findByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(UUID userId);

  Optional<Resume> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

  List<Resume> findByUserIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(UUID userId);

  Optional<Resume> findByIdAndUserIdAndDeletedAtIsNotNull(UUID id, UUID userId);
}
