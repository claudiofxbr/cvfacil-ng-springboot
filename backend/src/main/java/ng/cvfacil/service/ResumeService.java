package ng.cvfacil.service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ng.cvfacil.domain.Resume;
import ng.cvfacil.dto.ResumeDtos.ResumeRequest;
import ng.cvfacil.dto.ResumeDtos.ResumeView;
import ng.cvfacil.repository.ResumeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lógica de CRUD de currículos compartilhada entre ResumeController (produção)
 * e LocalResumeController (dev local).
 *
 * ANTES: os dois controllers duplicavam integralmente esta lógica (cifragem,
 * filtro de propriedade por userId, mapeamento de view). Isso é justamente o
 * código sensível a segurança — uma correção aplicada em um controller e
 * esquecida no outro teria criado uma divergência silenciosa entre profiles.
 * Agora existe uma única implementação; os controllers só resolvem o userId
 * (a única coisa que difere entre profiles) e delegam para cá.
 */
@Service
public class ResumeService {

  private final ResumeRepository resumes;
  private final AesGcmCipherService cipher;
  private final CreditService credits;

  public ResumeService(ResumeRepository resumes, AesGcmCipherService cipher, CreditService credits) {
    this.resumes = resumes;
    this.cipher = cipher;
    this.credits = credits;
  }

  public List<ResumeView> list(UUID userId) {
    return resumes.findByUserIdOrderByUpdatedAtDesc(userId).stream().map(this::toView).toList();
  }

  public Optional<ResumeView> get(UUID id, UUID userId) {
    return resumes.findById(id).filter(r -> r.getUserId().equals(userId)).map(this::toView);
  }

  /**
   * @throws CreditService.InsufficientCreditsException se o usuário não tiver
   *         crédito disponível — 1 crédito é consumido por currículo criado
   *         (não se aplica a edição, só à criação).
   */
  @Transactional
  public ResumeView create(UUID userId, ResumeRequest req) {
    credits.consumeOneForResumeCreation(userId);
    Resume r = new Resume();
    r.setUserId(userId);
    applyRequest(r, req);
    resumes.save(r);
    return toView(r);
  }

  public Optional<ResumeView> update(UUID id, UUID userId, ResumeRequest req) {
    return resumes.findById(id)
        .filter(r -> r.getUserId().equals(userId))
        .map(r -> {
          applyRequest(r, req);
          r.setVersion(r.getVersion() + 1);
          resumes.save(r);
          return toView(r);
        });
  }

  public boolean delete(UUID id, UUID userId) {
    Resume found = resumes.findById(id).filter(r -> r.getUserId().equals(userId)).orElse(null);
    if (found == null) return false;
    resumes.delete(found);
    return true;
  }

  private void applyRequest(Resume r, ResumeRequest req) {
    r.setLayoutId(req.layoutId());
    if (req.locale() != null) r.setLocale(req.locale());
    r.setContentEnc(cipher.encrypt(req.content().getBytes(StandardCharsets.UTF_8)));
    if (req.photoUrl() != null) r.setPhotoUrl(req.photoUrl());
  }

  private ResumeView toView(Resume r) {
    String content;
    try {
      content = new String(cipher.decrypt(r.getContentEnc()), StandardCharsets.UTF_8);
    } catch (Exception e) {
      content = "{}"; // conteúdo corrompido ou chave diferente — não expõe erro
    }
    return new ResumeView(
        r.getId(),
        r.getLayoutId(),
        r.getLocale(),
        r.getVersion(),
        content,
        r.getPhotoUrl(),
        r.getCreatedAt(),
        r.getUpdatedAt());
  }
}
