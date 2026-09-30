# syntax=docker/dockerfile:1

# ---- deps ----
FROM node:20-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
# npm ci exige o lock file — build determinístico (nunca npm install).
RUN npm ci

# ---- build ----
FROM node:20-alpine AS build
WORKDIR /app
ARG NEXT_PUBLIC_API_BASE_URL
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL
COPY --from=deps /app/node_modules ./node_modules
COPY . .
# next.config.js já define output: 'standalone' — gera .next/standalone com
# só as dependências realmente usadas (não arrasta node_modules inteiro).
RUN npm run build

# ---- runtime ----
FROM node:20-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
RUN addgroup -S cvfacil && adduser -S cvfacil -G cvfacil
COPY --from=build --chown=cvfacil:cvfacil /app/.next/standalone ./
COPY --from=build --chown=cvfacil:cvfacil /app/.next/static ./.next/static
# BUG CORRIGIDO: output 'standalone' do Next.js nao copia public/ sozinho (limitacao
# documentada da propria Vercel) — todo arquivo estatico em public/ (favicon, robots.txt,
# arquivos de verificacao de dominio) respondia 404 em producao sem esta linha.
COPY --from=build --chown=cvfacil:cvfacil /app/public ./public
USER cvfacil

EXPOSE 3000
ENV PORT=3000
# BUG REAL DE PRODUCAO (30/09): sem isso, o server standalone do Next.js
# bina so na interface da rede "principal" do container no boot (ex.:
# 172.16.7.5 da rede cvfacil-sb-net) -- nao em 0.0.0.0, nem em 127.0.0.1.
# Container multi-rede (cvfacil-sb-net + easypanel, para o Traefik) ficava
# inalcancavel por QUALQUER outra rede/interface, incluindo o proprio
# Traefik (502 Bad Gateway). Fix oficial documentado pelo Next.js para
# deploy standalone em Docker.
ENV HOSTNAME="0.0.0.0"
CMD ["node", "server.js"]
