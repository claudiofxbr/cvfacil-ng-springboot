# Runbook Operacional — CVFacil.NG

## Deploy blue/green (Hostinger)

```
/opt/cvfacil/
├── blue/   (conjunto A)
├── green/  (conjunto B)
├── current -> blue   # symlink atômico
└── scripts/
    ├── switch-color.sh
    └── healthcheck.sh
```

### Troca de cor

```bash
# switch-color.sh <color>
systemctl stop cvfacil-backend
ln -sfn /opt/cvfacil/$1 /opt/cvfacil/current
systemctl start cvfacil-backend
pm2 reload cvfacil-frontend
```

### Healthcheck

```bash
# healthcheck.sh  (sai com 0 se saudável)
curl -fsS http://127.0.0.1:8080/actuator/health | grep '"status":"UP"'
curl -fsS http://127.0.0.1:3000/api/health     | grep '"status":"UP"'
```

## Rotação de chaves

| Chave | Frequência | Como |
|---|---|---|
| JWT RS256 | 30 dias | Gerar nova par PEM; publicar JWKS com `kid` novo; revogar antigo após TTL |
| AI_API_KEY | 90 dias | Rotacionar no provedor; atualizar Hostinger env var; reiniciar serviço |
| BCrypt pepper | 180 dias | Migração dupla: re-hashar no próximo login bem-sucedido |

## Incidentes

1. **Login quebrado** — verificar Redis (`redis-cli ping`), logs do Spring, taxa
   de 429. Se rate limit estiver pressionando usuários legítimos, ajustar
   `cvfacil.security.rate-limit-login-per-minute` temporariamente.
2. **IA indisponível** — desabilitar a aba "Importar com IA" via feature flag
   (pendente SEC/feature flag). Retornar 503 com mensagem amigável.
3. **Vazamento suspeito** — rotacionar `ENCRYPTION_MASTER_KEY`, JWT e
   `AI_API_KEY` imediatamente; congelar contas RootMaster.

## Backup & restore

- Neon: PITR de 7 dias. Drill mensal via `neon restore --branch recovery`.
- Verificação: script em `docs/scripts/restore-drill.md` (pendente).
