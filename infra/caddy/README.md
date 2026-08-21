# Caddyfile da VPS — snapshot de documentação

Este arquivo é uma CÓPIA (não editável a partir daqui) do `Caddyfile` real, que
vive em `/opt/caddy-cvfacil/Caddyfile` na VPS Hostinger (`69.62.87.38`),
montado como bind mount no container `caddy-cvfacil`. Editar este arquivo no
repositório NÃO afeta a VPS — é só para consulta/histórico.

## Domínios servidos

- `xavierbr-vps.tech:8443` → app Prisma/Node existente (`localhost:3002`).
- `cvfacil-ng.xavierbr-vps.tech:8443` → app CVFacil.NG Spring Boot desta
  sessão: `/api/*`, `/oauth2/*` e `/login/oauth2/*` roteados para o backend
  (`localhost:8095`), resto para o frontend (`localhost:3012`). As rotas
  OAuth precisam ir para o backend mesmo fora de `/api/*` — sem esses dois
  `handle` extras, o link "Entrar com Google" (gerado como caminho relativo
  ao mesmo domínio, já que o frontend é buildado com
  `NEXT_PUBLIC_API_BASE_URL=https://cvfacil-ng.xavierbr-vps.tech`) cairia no
  frontend Next.js em vez do Spring Security.

## Bug corrigido: `dns_ttl`

A emissão do certificado do subdomínio `cvfacil-ng.xavierbr-vps.tech` falhava
com `[DNS:4005] Resource record TTL must be at least 60 seconds!` — o plugin
`dns.providers.hostinger` usa um TTL padrão abaixo do mínimo aceito pela API
da Hostinger para o registro `_acme-challenge` (TXT) do desafio DNS-01.

Corrigido com a subdiretiva padrão do Caddy `dns_ttl` (documentação:
https://caddyserver.com/docs/caddyfile/directives/tls), aplicada só no bloco
do `cvfacil-ng.xavierbr-vps.tech` — o bloco do domínio raiz não foi tocado
(seu certificado já existia e continua funcionando sem essa diretiva).

```caddyfile
tls {
	dns_ttl 60s
	dns hostinger {env.HOSTINGER_API_TOKEN}
}
```

Certificado obtido com sucesso após essa mudança + `caddy reload` (log
confirmado: `certificate obtained successfully`).

## Status (resolvido)

DNS A record criado pelo dono da conta Hostinger, HTTPS confirmado via DNS
público real (`curl https://cvfacil-ng.xavierbr-vps.tech:8443/` → 200),
registro/login testados de ponta a ponta.

Link final do app: **https://cvfacil-ng.xavierbr-vps.tech:8443**

## Login com Google — redirect URI correto

A `redirect_uri` que o Spring Security calcula muda conforme o host/porta da
requisição. Com o domínio HTTPS ativo, a URI a cadastrar no Google Cloud
Console (Authorized redirect URIs) é:

```
https://cvfacil-ng.xavierbr-vps.tech:8443/login/oauth2/code/google
```

(Substitui a orientação anterior, que usava `http://69.62.87.38:8095/...` —
válida só enquanto o app rodava sem domínio/HTTPS.)

Authorized JavaScript origins:
```
https://cvfacil-ng.xavierbr-vps.tech:8443
```

**Status (resolvido em 2026-08-21):** credenciais reais configuradas
(`GOOGLE_OAUTH_CLIENT_ID`/`GOOGLE_OAUTH_CLIENT_SECRET` no `backend.env` da
VPS, Client ID `130848217255-...apps.googleusercontent.com`). Login Google
testado de ponta a ponta em navegador real: consentimento do Google →
redirect → `/api/auth/refresh`, `/api/resumes`, `/api/credits/wallet`
todos 200 → sessão autenticada no dashboard.

Enquanto os valores estiverem com o placeholder antigo
(`disabled-client.apps.googleusercontent.com` / `disabled-secret`), o botão
"Entrar com Google" redireciona ao Google mas retorna **Erro 401:
invalid_client — "The OAuth client was not found"**, porque esse client_id
nunca existiu de fato no Google Cloud Console — é só um valor sentinela
para o Spring Boot subir sem falhar a validação de `client-id` não-vazio
(ver comentário em `application.yml`).

### Pegadinha: `docker restart` NÃO recarrega env vars

Editar `backend.env` e rodar `docker restart <container>` **não é
suficiente** — variáveis de ambiente de um container Docker são fixadas na
**criação** (`docker run --env ...`), não relidas do arquivo a cada
restart. Foi exatamente isso que causou o erro persistir mesmo depois do
`backend.env` já estar com os valores corretos.

Além disso, `ci_deploy.py` (usado em todo deploy via CI/CD) **copia o env
do container atualmente rodando** para o container novo — ele não lê
`backend.env` também. Ou seja, uma vez corrigido manualmente uma vez, o
valor correto se propaga sozinho nos deploys seguintes; mas se o container
for recriado do zero (`docker rm` + `docker run` manual sem copiar o env
antigo), a variável precisa ser reaplicada.

Para aplicar uma env var nova/corrigida em produção sem esperar o próximo
deploy: recriar o container copiando o env atual e substituindo só as
chaves necessárias (mesmo padrão do `ci_deploy.py` — `docker inspect
--format '{{json .Config.Env}}'`, editar, `docker run` de novo com
`--env` por chave). Um `docker restart` sozinho não resolve.

## Bug corrigido: porta `:8443` faltando em 3 variáveis

O Caddy escuta esse subdomínio na porta `8443` (não na `443` padrão — a
`443`/`80` já são usadas pelo `nginx` da VPS para outros vhosts). No
primeiro deploy com o domínio HTTPS, três variáveis foram configuradas
**sem** a porta:

- `NEXT_PUBLIC_API_BASE_URL` (build-arg do frontend, baked-in no bundle JS)
- `CORS_ALLOWED_ORIGINS` / `JWT_ISSUER` / `FRONTEND_BASE_URL` (env do backend)

Efeito em cascata:
1. O frontend chamava a API em `https://cvfacil-ng.xavierbr-vps.tech`
   (porta 443 implícita) em vez de `:8443`.
2. Como o `nginx` não tem `server_name` para esse subdomínio, a requisição
   caía no vhost "default" dele — que por coincidência é **outro app
   Spring Boot completamente diferente**, hospedado na mesma VPS
   (`cvfacil.xavierbr-vps.tech`, proxy para `localhost:7777`). Esse app
   respondeu com um erro de validação genérico só que de outro código-fonte
   — confundindo o diagnóstico (parecia erro de cadastro duplicado/erro
   aleatório, mas nem chegava a tocar no backend certo).
3. Mesmo corrigindo só a URL, `CORS_ALLOWED_ORIGINS` sem a porta bloquearia
   a resposta no navegador (`curl` não detecta isso — CORS é aplicado só
   pelo navegador, por isso os testes via `curl` "passavam" enquanto o
   registro real pela UI falhava).

**Corrigido**: as 4 variáveis passaram a incluir `:8443` explicitamente.
Confirmado end-to-end via navegador real (não só `curl`): cadastro e login
funcionando em `https://cvfacil-ng.xavierbr-vps.tech:8443`.

**Lição para reproduzir esse deploy em outra porta/domínio no futuro**:
sempre incluir a porta em TODAS as URLs absolutas passadas ao app quando
ela não for a 80/443 padrão — inconsistência entre elas quebra CORS e/ou
gera roteamento cruzado silencioso para outro serviço na mesma VPS.
