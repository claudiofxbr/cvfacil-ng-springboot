package ng.cvfacil.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import ng.cvfacil.domain.User;
import ng.cvfacil.dto.PrivacyDtos.UserExport;
import ng.cvfacil.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * Regras de RBAC compartilhadas entre AdminController (produção) e LocalAdminController (dev local)
 * — mesmo padrão de dedup usado em ResumeService/AIImportRequestSupport: a lógica sensível a
 * segurança vive em um único lugar.
 *
 * <p>Papéis (ver User.Role): USER (Cliente): fora do escopo deste serviço — só mexe nos próprios
 * currículos. ADMIN: tudo abaixo, EXCETO excluir usuário e conceder créditos. ROOT_MASTER (Root):
 * tudo, sem exceção.
 */
@Service
public class AdminService {

  /**
   * Regra de negocio: esta conta e ROOT_MASTER permanente (ver RootEmailEnforcementService, que
   * corrige o papel periodicamente) e nunca pode ser excluida, mesmo por outro ROOT_MASTER.
   */
  public static final String PROTECTED_ROOT_EMAIL = "claudio.xavier@gmail.com";

  /** Lancada quando uma acao tenta remover/rebaixar a conta Root protegida por regra de negocio. */
  public static final class ProtectedRootAccountException extends RuntimeException {
    public ProtectedRootAccountException(String message) {
      super(message);
    }
  }

  private final UserRepository users;
  private final AuditService audit;
  private final PrivacyService privacy;

  public AdminService(UserRepository users, AuditService audit, PrivacyService privacy) {
    this.users = users;
    this.audit = audit;
    this.privacy = privacy;
  }

  /**
   * PRD §4.4: MFA obrigatório para o RootMaster ao exercer ações privilegiadas (excluir usuário,
   * conceder créditos). Checado aqui — e não no login — para não travar a conta antes da primeira
   * ativação via POST /api/mfa/setup.
   */
  public boolean rootHasMfaEnabled(UUID userId) {
    return users.findById(userId).map(User::isMfaEnabled).orElse(false);
  }

  public long countUsers() {
    return users.count();
  }

  private static final int MAX_PAGE_SIZE = 100;

  /**
   * Disponível para ADMIN e ROOT_MASTER — checagem de papel fica no controller. Paginado: {@code
   * findAll()} sem limite materializava a base inteira em memória a cada carregamento do painel,
   * o que não escala conforme a base de usuários cresce.
   */
  public Page<UserAdminView> listUsers(int page, int size) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    return users
        .findAll(PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")))
        .map(this::toView);
  }

  /**
   * Exportação dos dados de um usuário por um Admin/Root — mesma visão que o próprio titular obtém
   * via PrivacyService.export (LGPD Art. 18), mas disparada por quem tem acesso ao painel admin
   * (ex.: atender solicitação do titular por outro canal). Disponível para ADMIN e ROOT_MASTER
   * (mesmo nível de listUsers); fica registrado em audit_logs por envolver dados pessoais de
   * terceiros.
   */
  public Optional<UserExport> exportUser(
      UUID actingUserId, User.Role actingRole, UUID targetUserId) {
    if (actingRole != User.Role.ADMIN && actingRole != User.Role.ROOT_MASTER) {
      return Optional.empty();
    }
    Optional<UserExport> export = privacy.export(targetUserId);
    if (export.isPresent()) {
      audit.record(actingUserId, "ADMIN_EXPORT_USER_DATA", null, null, "target=" + targetUserId);
    }
    return export;
  }

  /**
   * Exclusão de usuário — SOMENTE ROOT_MASTER. O controller deve garantir que {@code actingRole ==
   * Role.ROOT_MASTER} antes de chamar este método; aqui repetimos a checagem como segunda barreira
   * (defense in depth).
   *
   * @return true se excluído; false se não autorizado, alvo inexistente, ou alvo é outro
   *     ROOT_MASTER (nunca excluível por esta rota).
   * @throws ProtectedRootAccountException se o alvo for a conta Root permanente
   *     (PROTECTED_ROOT_EMAIL) — erro visível em vez de 404 silencioso, para que a tentativa fique
   *     clara nos logs/resposta em vez de parecer "usuário não encontrado".
   */
  public boolean deleteUser(UUID actingUserId, User.Role actingRole, UUID targetUserId) {
    if (actingRole != User.Role.ROOT_MASTER) return false;
    User target = users.findById(targetUserId).orElse(null);
    if (target == null) return false;
    if (PROTECTED_ROOT_EMAIL.equalsIgnoreCase(target.getEmail())) {
      throw new ProtectedRootAccountException(
          "Esta conta é Root permanente por regra de negócio e não pode ser excluída.");
    }
    if (target.getRole() == User.Role.ROOT_MASTER) return false;
    users.delete(target);
    audit.record(actingUserId, "ADMIN_DELETE_USER", null, null, "target=" + targetUserId);
    return true;
  }

  private UserAdminView toView(User u) {
    return new UserAdminView(
        u.getId(), u.getEmail(), u.getDisplayName(), u.getRole().name(), u.getCreatedAt());
  }

  public record UserAdminView(
      UUID id, String email, String displayName, String role, Instant createdAt) {}
}
