# Caddyfile da VPS — snapshot de documentação

Este arquivo é uma CÓPIA (não editável a partir daqui) do `Caddyfile` real, que
vive em `/opt/caddy-cvfacil/Caddyfile` na VPS Hostinger (`69.62.87.38`),
montado como bind mount no container `caddy-cvfacil`. Editar este arquivo no
repositório NÃO afeta a VPS — é só para consulta/histórico.

## Domínios servidos

- `xavierbr-vps.tech:8443` → app Prisma/Node existente (`localhost:3002`).
- `cvfacil-ng.xavierbr-vps.tech:8443` → app CVFacil.NG Spring Boot desta
  sessão: `/api/*` roteado para o backend (`localhost:8095`), resto para o
  frontend (`localhost:3012`).

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

## Pendência

O certificado já existe e o site responde em HTTPS (`--resolve` confirma),
mas o **registro DNS tipo A** `cvfacil-ng` → `69.62.87.38` ainda não foi
criado no painel da Hostinger (hPanel → Domínios → xavierbr-vps.tech → DNS).
Sem isso, o subdomínio resolve `NXDOMAIN` publicamente. Essa é uma ação
administrativa na conta Hostinger — precisa ser feita manualmente pelo dono
da conta, não é algo que se resolve por config/código.
