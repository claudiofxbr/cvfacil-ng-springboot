package ng.cvfacil.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.UUID;
import ng.cvfacil.domain.AuditLog;
import ng.cvfacil.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Cobre o mascaramento de PII em detailsJson — ver AuditService.maskPii/EMAIL_PATTERN. */
class AuditServiceTest {

  private AuditLogRepository repo;
  private EntityManager entityManager;
  private AuditService service;

  @BeforeEach
  void setUp() {
    repo = mock(AuditLogRepository.class);
    entityManager = mock(EntityManager.class);
    Query query = mock(Query.class);
    when(entityManager.createNativeQuery(anyString())).thenReturn(query);
    when(query.setParameter(anyString(), any())).thenReturn(query);
    when(query.getSingleResult()).thenReturn(1);
    when(repo.findTopByOrderByIdDesc()).thenReturn(null);
    when(repo.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));
    service = new AuditService(repo, entityManager);
  }

  @Test
  void record_masksEmailInDetailsJson() {
    AuditLog entry =
        service.record(
            UUID.randomUUID(), "USER_SELF_DELETE", null, null, "email=ana.silva@gmail.com");

    assertThat(entry.getDetailsJson()).isEqualTo("email=a***@gmail.com");
    assertThat(entry.getDetailsJson()).doesNotContain("ana.silva");
  }

  @Test
  void record_withoutEmail_leavesDetailsJsonUnchanged() {
    AuditLog entry =
        service.record(UUID.randomUUID(), "ADMIN_DELETE_USER", null, null, "target=abc-123");

    assertThat(entry.getDetailsJson()).isEqualTo("target=abc-123");
  }

  @Test
  void record_withNullDetails_doesNotThrow() {
    AuditLog entry =
        service.record(UUID.randomUUID(), "LOGIN_SUCCESS", "127.0.0.1", "curl/8.0", null);

    assertThat(entry.getDetailsJson()).isNull();
  }
}
