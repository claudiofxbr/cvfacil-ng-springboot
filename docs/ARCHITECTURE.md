# Arquitetura — CVFacil.NG

Este documento resume as decisões de arquitetura. A especificação completa está
na Seção 3 do PRD (`PRD_CVFacil_NG.docx`).

## Visão C4 resumida

```
[Usuário Web] ──HTTPS──▶ [Next.js 14 (App Router, SSR)]
                              │
                              ▼  fetch + JWT Bearer + cookie XSRF
                    [Spring Boot 3.3 REST API]
                    ├─▶ Spring Security (OAuth2 + JWT RS256)
                    ├─▶ Serviço de Currículos  ─▶ Neon PostgreSQL 16
                    ├─▶ Serviço de IA          ─▶ API IA externa
                    ├─▶ Serviço de Uploads     ─▶ Bucket de objetos
                    └─▶ Redis (cache, rate limit)
```

## Decisões chave (ADR-style resumido)

| ADR | Decisão | Motivo |
|-----|---------|--------|
| 001 | Monorepo | Frontend e backend evoluem juntos; um PR pode cobrir contrato ponta-a-ponta |
| 002 | Next.js App Router | SSR para SEO + React Server Components para performance |
| 003 | Spring Boot + JPA | Ecossistema maduro para segurança e persistência |
| 004 | Neon PostgreSQL | Serverless, branches de DB para preview deploys |
| 005 | JWT RS256 + refresh httpOnly | Stateless + mitigação de XSS |
| 006 | AES-256-GCM column-level | PII do currículo cifrada em repouso |
| 007 | CSP estrita + `react/no-danger` error | Defesa em profundidade contra XSS |
| 008 | AuditLog imutável encadeado | Não-repúdio e compliance |

## Camadas do backend

- **web/**   — REST controllers, DTOs de entrada/saída.
- **service/** — regras de negócio.
- **domain/** — entidades JPA.
- **repository/** — interfaces Spring Data.
- **config/** — beans de infraestrutura e segurança.
- **security/** — componentes de autenticação e tokens.

## Observabilidade

- Actuator + Prometheus scrape em `/actuator/prometheus`.
- Logs JSON (padrão único no `application.yml`) com `traceId` correlacionado por
  OpenTelemetry.
- Sentry para captura de erros não tratados (FE e BE).
