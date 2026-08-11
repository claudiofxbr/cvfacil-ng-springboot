# Segurança — CVFacil.NG

Este documento sumariza o modelo de segurança. A especificação completa está na Seção 4 do PRD (`PRD_CVFacil_NG.docx`).

## Modelo de Ameaças (STRIDE resumido)

| Categoria | Ameaça | Mitigação |
|---|---|---|
| Spoofing | Roubo de sessão | JWT RS256 curto + refresh httpOnly + SameSite=Strict |
| Tampering | Alteração de payload | Assinatura JWT + HMAC em logs de auditoria |
| Repudiation | Usuário nega ação | AuditLog imutável append-only com hash encadeado |
| Information Disclosure | Vazamento de PII | AES-256-GCM column-level em `contentJson` |
| DoS | Força bruta em login | Rate limit 5 req/min/IP via Redis |
| Elevation of Privilege | Escalada para admin | RootMaster isolado, MFA TOTP, IP allowlist |

## Backlog (SEC-01 a SEC-15)

Ver Tabela 4.7.1 do PRD. Cada item é rastreável como issue do projeto com label `security`.

## Política de Senhas

- Mínimo 10 caracteres (16 para RootMaster)
- Verificação contra HIBP via k-anonymity (hash prefix)
- BCrypt cost 12 + pepper em variável de ambiente
- Rotação obrigatória em 90 dias (RootMaster)
- Histórico de 12 senhas (RootMaster)

## Reportar Vulnerabilidades

security@cvfacil.ng (GPG obrigatório para payload sensível).
