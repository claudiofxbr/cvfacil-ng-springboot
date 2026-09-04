# CVFacil.NG

Plataforma web de alto nível para criação de currículos profissionais, com 9 layouts modernos, internacionalização (pt-BR / en-US / es-ES), autenticação federada (Google OAuth 2.0) e importação de currículos assistida por Inteligência Artificial.

> Este repositório é o **scaffold oficial** gerado a partir do PRD `PRD_CVFacil_NG.docx`. Consulte o PRD para a especificação completa de produto, segurança, UX e plano de execução.

---

## Arquitetura

```
CVFacil.NG/
├── frontend/          Next.js 14 (App Router, JavaScript, Tailwind CSS 3)
├── backend/           Spring Boot 3.3 (Java 21, Spring Security, JPA, Flyway)
├── .github/workflows/ Pipelines de CI/CD (GitHub Actions)
├── docs/              Documentação adicional
└── PRD_CVFacil_NG.docx  Documento de Requisitos do Produto
```

| Camada | Tecnologia | Versão |
|---|---|---|
| Frontend | Next.js, Tailwind, next-intl, Framer Motion | 14.2 / 3.4 |
| Backend | Java + Spring Boot | 21 / 3.3 |
| Banco | Neon PostgreSQL | 16 |
| Cache | Redis (Upstash) | 7 |
| CI/CD | GitHub Actions | — |
| Deploy | Hostinger | VPS Ubuntu 24.04 |

---

## Pré-requisitos

- **Node.js 20 LTS** e **npm 10+**
- **Java 21 (Temurin)** e **Maven 3.9+**
- **Docker** (opcional, para PostgreSQL e Redis locais)
- Contas de acesso (para produção): Neon, Google Cloud Console (OAuth), Hostinger, provedor de IA

---

## Quick Start — Frontend

```bash
cd frontend
cp .env.local.example .env.local     # preencher variáveis
npm ci
npm run dev                          # http://localhost:3000
```

## Quick Start — Backend

```bash
cd backend
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
# preencher DATABASE_URL, GOOGLE_OAUTH_*, AI_API_KEY, JWT_*, etc.
./mvnw spring-boot:run -Dspring-boot.run.profiles=local   # http://localhost:8080
```

---

## Variáveis de Ambiente

Ver `.env.example` na raiz e os exemplos específicos em `frontend/.env.local.example` e `backend/src/main/resources/application-local.yml.example`.

**A chave da API de IA (`AI_API_KEY`) nunca deve ser commitada.** Sua localização oficial é:

| Ambiente | Local |
|---|---|
| Dev local | `backend/src/main/resources/application-local.yml` (não versionado) |
| Staging / Prod | Variável de ambiente `AI_API_KEY` no painel Hostinger |
| CI | GitHub Secrets (`AI_API_KEY` no ambiente `production`) |

---

## Scripts Úteis

```bash
# Frontend
npm run dev          # servidor Next.js em modo dev
npm run build        # build de produção (output: standalone)
npm run start        # inicia build de produção
npm run lint         # ESLint
npm run test         # Jest

# Backend (via wrapper)
./mvnw verify                          # lint + testes
./mvnw spring-boot:run                 # executa aplicação
./mvnw -DskipTests package             # gera JAR fat
```

---

## Segurança

Este projeto segue os requisitos de segurança do PRD (Seção 4). Destaques:

- **Autenticação**: Google OAuth 2.0 com PKCE + login local BCrypt (cost 12)
- **JWT RS256** com rotação de chaves a cada 30 dias
- **RootMaster**: conta de super-admin com MFA TOTP obrigatório
- **Proteções**: CSP estrita, CSRF double-submit, Bean Validation, sanitização OWASP
- **Criptografia**: TLS 1.3 em trânsito, AES-256-GCM em repouso (column-level)

Ver `docs/SECURITY.md` para o modelo de ameaças completo.

---

## CI/CD

- **`.github/workflows/ci.yml`**: lint, build, testes unitários, SAST (CodeQL), verificação de dependências, DAST (OWASP ZAP baseline em PRs).
- **`.github/workflows/deploy.yml`**: deploy blue/green na Hostinger com healthcheck e rollback automático.

---

## Torre de Controle dos Processos

Painel de status do pipeline CI/CD, publicado como Claude Artifact: https://claude.ai/code/artifact/5571a2a0-087b-4ff3-9cbe-15d76b05564e

**Mecanismo de atualização: manual, sob demanda ("clique único"), não automático.**

Uma rotina cloud agendada (`cvfacil-ng-torre-controle-refresh`, hora em hora) foi testada e descartada: ela consegue ler o GitHub Actions (`gh run list`) normalmente, mas trava ao tentar gravar o resultado no banco do artifact — a escrita dispara um prompt de permissão que nenhuma sessão desacompanhada pode aprovar, e a plataforma bloqueia deliberadamente qualquer tentativa de configurar a sessão para pular esse prompt (`bypassPermissions`), por ser uma barreira de segurança contra automações que escrevem dados sem supervisão humana. A rotina permanece cadastrada porém **desativada** (`enabled:false`).

Por isso, a atualização do painel é feita pedindo ao Claude, em uma sessão interativa: *"atualiza a torre de controle"*. O passo a passo (reprodutível manualmente também via `gh` CLI, se necessário):

1. `gh run list --repo claudiofxbr/cvfacil-ng-springboot --limit 10 --json databaseId,name,event,status,conclusion,createdAt,headBranch,headSha`
2. Mapear os workflows `CI` e `Deploy — Hostinger` para `active`/`running`/`failed` conforme o `status`/`conclusion` do run mais recente.
3. Gravar o resultado no documento `status/cvfacil-ng` do artifact acima (`write_db`, `db_op: "set"`), preservando os campos `project`, `categories`, `statusLabels`, `readyBanner` e a fila (`queue`) já cadastrados, e atualizando `updatedAt` para a hora UTC atual.

---

## Plano de Execução (Resumo)

| Fase | Semanas | Entregável |
|---|---|---|
| 1 — Setup | 1–2 | Monorepo + pipeline básico + Hello World em staging |
| 2 — Core | 3–6 | CRUD de currículos, dashboard, 9 layouts, i18n |
| 3 — Segurança | 7–9 | OAuth, JWT, RootMaster, SEC-01…SEC-15 |
| 4 — IA/UI | 10–12 | Importação IA, foto 3x4 com moldura, animações |
| 5 — Deploy | 13–14 | Blue/green, observabilidade, runbooks |

---

## Licença

Proprietário — Uso Interno CVFacil.NG.
