package ng.cvfacil.repository;

import java.util.Optional;
import java.util.UUID;
import ng.cvfacil.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
  Optional<User> findByEmailIgnoreCase(String email);
  boolean existsByEmailIgnoreCase(String email);
}
