package ng.cvfacil.service;

import ng.cvfacil.domain.User;
import ng.cvfacil.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regra de negocio: {@link AdminService#PROTECTED_ROOT_EMAIL} deve sempre ser ROOT_MASTER no
 * CVFacil.NG.
 *
 * <p>So promove contas com {@code emailVerified == true} — esse flag hoje so e setado por {@link
 * ng.cvfacil.security.OAuth2LoginSuccessHandler} apos o Google confirmar a posse do email. Sem
 * essa checagem, qualquer pessoa poderia se cadastrar localmente com esse email (o registro por
 * senha nao verifica posse de caixa de entrada) e ganhar ROOT_MASTER — checar emailVerified fecha
 * esse vetor de escalonamento de privilegio, sem exigir um fluxo novo de verificacao de email.
 *
 * <p>Ativo apenas fora do profile local (decisao de escopo: a regra vale para producao). Rodando
 * como job periodico (nao a cada request) para nao pagar o custo de uma query extra por
 * requisicao autenticada.
 */
@Service
@Profile("!local")
public class RootEmailEnforcementService {

  private static final Logger log = LoggerFactory.getLogger(RootEmailEnforcementService.class);

  private final UserRepository users;
  private final AuditService audit;

  public RootEmailEnforcementService(UserRepository users, AuditService audit) {
    this.users = users;
    this.audit = audit;
  }

  @Scheduled(fixedRate = 300_000, initialDelay = 60_000)
  @Transactional
  public void enforceRootRole() {
    users
        .findByEmailIgnoreCase(AdminService.PROTECTED_ROOT_EMAIL)
        .filter(User::isEmailVerified)
        .filter(u -> u.getRole() != User.Role.ROOT_MASTER)
        .ifPresent(this::promote);
  }

  private void promote(User u) {
    User.Role previous = u.getRole();
    u.setRole(User.Role.ROOT_MASTER);
    users.save(u);
    log.warn(
        "[root-enforcement] {} estava como {}, corrigido para ROOT_MASTER pela regra de negocio.",
        AdminService.PROTECTED_ROOT_EMAIL,
        previous);
    audit.record(u.getId(), "ROOT_ROLE_AUTO_ENFORCED", null, null, "previousRole=" + previous);
  }
}
