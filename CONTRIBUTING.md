# Contribuindo para CVFacil.NG

## Fluxo de trabalho

1. Clone o monorepo e crie branch a partir de `develop`:
   ```bash
   git checkout develop && git pull
   git checkout -b feature/<tema-curto>
   ```
2. Faça commits pequenos, com mensagens no padrão Conventional Commits:
   - `feat(frontend): adiciona filtro por paleta`
   - `fix(backend): corrige rate limit em /api/auth/login`
   - `chore(ci): ativa Trivy`
3. Abra Pull Request contra `develop`. O CI precisa passar verde e pelo menos
   um Code Owner (ver `.github/CODEOWNERS`) deve aprovar.
4. Releases: merge `develop -> main` via Pull Request de release.

## Padrões de qualidade

- Cobertura de testes ≥ 80% (backend) e ≥ 75% (frontend).
- ESLint (frontend) e Spotless (backend) sem erros.
- Sem secrets no repositório — use `.env.example` e GitHub Secrets.
- Mudanças em `backend/src/main/resources/db/migration/` exigem revisão
  dupla e nunca são editadas após serem aplicadas em qualquer ambiente.

## Estrutura de commits sensíveis

Mudanças em qualquer arquivo dentro de `/backend/src/main/java/ng/cvfacil/config/`
ou `/backend/src/main/java/ng/cvfacil/security/` devem referenciar o ID da
tarefa de segurança (SEC-01 a SEC-15) no corpo do commit.

## Executando localmente

Ver README.md seção "Quick Start".

## Reportar bug

Abra issue no GitHub com template `bug_report`. Para vulnerabilidades de
segurança, envie e-mail para **security@cvfacil.ng** (ver `docs/SECURITY.md`).
