# Deploy automático — Hostinger

`ci_deploy.py` é um espelho (documentação/versionamento) do script que
efetivamente roda na VPS em `/root/cvfacil-sb/ci_deploy.py`. Editar este
arquivo aqui NÃO afeta a VPS — é preciso copiar manualmente para lá depois
de qualquer mudança (`scp infra/deploy/ci_deploy.py root@69.62.87.38:/root/cvfacil-sb/`).

## Como funciona o workflow (`.github/workflows/deploy.yml`)

1. Todo push em `main` (ou disparo manual via `workflow_dispatch`) roda o job.
2. O runner do GitHub Actions envia `backend/` e `frontend/` (código-fonte,
   sem segredos) para `/root/cvfacil-build/` na VPS via SSH.
3. O runner roda `python3 ci_deploy.py <sha-curto>` na VPS, que builda as
   duas imagens Docker e faz o cutover.
4. **Segredos da aplicação nunca passam pelo GitHub Actions.** O
   `ci_deploy.py` reaproveita as env vars já configuradas no container em
   produção (`docker inspect ... Config.Env`) — DATABASE_PASSWORD, chaves
   JWT, AI_API_KEY etc. continuam só na VPS. O GitHub Actions só precisa da
   chave SSH (`HOSTINGER_SSH_KEY`) para acessar o servidor.
5. Se o `docker run` da nova imagem falhar, o script reverte automaticamente
   para a versão anterior (mantida como `<container>-prev`).

## Secrets do GitHub Actions necessários

- `HOSTINGER_SSH_KEY` — chave privada dedicada (`cvfacil-ng-ci-deploy`),
  gerada só para esse fim. A pública está em
  `~/.ssh/authorized_keys` na VPS.
- `HOSTINGER_SSH_HOST` — `69.62.87.38`
- `HOSTINGER_SSH_USER` — `root`

## Rollback manual

Se precisar reverter manualmente um serviço:

```bash
docker stop cvfacil-sb-backend && docker rm cvfacil-sb-backend
docker rename cvfacil-sb-backend-prev cvfacil-sb-backend
docker start cvfacil-sb-backend
```

(troque `backend` por `frontend` conforme necessário)
