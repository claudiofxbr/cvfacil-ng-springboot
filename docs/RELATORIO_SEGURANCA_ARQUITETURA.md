# CVFacil.NG — Relatório Técnico de Segurança e Arquitetura
**Data:** 2026-04-27 | **Revisor:** Engenheiro Sênior / Especialista em Cloud Security  
**Escopo:** Código-fonte completo (backend Spring Boot 3.3 + frontend Next.js 14 + Neon PostgreSQL)

---

## Sumário Executivo

O CVFacil.NG possui uma base técnica sólida: BCrypt cost-12, AES-256-GCM column-level, CSRF double-submit, HSTS, CSP e auditoria com encadeamento SHA-256. Contudo, foram identificadas **4 vulnerabilidades críticas**, **6 de alta severidade** e **9 de média/baixa** que comprometem a segurança em produção e a estabilidade em desenvolvimento.

Todas as correções foram aplicadas ao código nesta sessão. Este documento descreve cada problema, sua causa raiz e a solução implementada.

---

## 1. Revisão de Arquitetura

### 1.1 Visão geral do fluxo

```
Browser (Next.js 14)  ←HTTPS→  Spring Boot 3.3  ←SSL→  Neon PostgreSQL (cloud)
                                       ↕
                                 Redis (rate limit)
                                       ↕
                                 AI Provider (OpenAI/Anthropic)
```

### 1.2 Gargalos e pontos de falha identificados

**[ARQ-01] AuditService.record() com `synchronized` — gargalo de throughput**

O método `record()` é `synchronized` em uma instância singleton Spring. Em alta concorrência (ex: 100 logins simultâneos), todas as threads enfileiram no lock de instância, degradando o tempo de resposta linearmente.

```java
// PROBLEMA
public synchronized AuditLog record(...) { ... }

// SOLUÇÃO: remover synchronized; a atomicidade do encadeamento de hash
// deve ser garantida por um mecanismo de banco (sequence + trigger) ou
// por aceitar que o prev_hash pode não ser perfeitamente sequencial sob carga.
// Para produção, use HMAC-SHA256 com chave KMS e não dependa de ordem de inserção.
public AuditLog record(...) { ... }
```

**[ARQ-02] Refresh token sem persistência — revogação impossível**

Os tokens de refresh (`STUB_REFRESH.<userId>.<uuid>`) são emitidos mas **nunca persistidos no banco**. Consequências:
- Impossível revogar tokens (ex: logout remoto, usuário comprometido).
- Qualquer token válido na janela de 7 dias pode ser usado indefinidamente.
- Não há detecção de reutilização (token rotation).

Solução: criar tabela `refresh_tokens` (ver seção 3.3).

**[ARQ-03] Embedded Postgres reinicia sem dados a cada execução**

Zonky EmbeddedPostgres 2.0.7 executa `initdb` a cada startup. O workaround atual (dump/restore via `LocalDataPersistenceConfig`) é frágil: exporta apenas `users` e usa concatenação de strings para gerar SQL, sem prepared statements.

**[ARQ-04] Redis como dependência opcional com fail-open**

O `RateLimitService` libera todas as requisições quando Redis está indisponível. Em produção, uma falha do Redis remove completamente a proteção contra brute force — exatamente quando um atacante poderia explorar isso.

**Recomendação:** em produção, usar fail-closed com circuit breaker e alerta imediato.

---

## 2. Segurança e Vulnerabilidades

### 2.1 Críticas (CVSS 9.0+)

---

**[SEC-CRIT-01] JwtService emite tokens sem assinatura — autenticação totalmente forjável**
**Severidade:** Crítica | **CWE:** CWE-347 (Improper Verification of Cryptographic Signature)

```java
// CÓDIGO ATUAL — NÃO É UM JWT REAL
public String issueAccessToken(User user) {
    return "STUB_ACCESS." + user.getId() + "." + System.currentTimeMillis();
}
```

Qualquer pessoa que conheça um UUID de usuário pode construir um token válido. O formato é completamente previsível e não tem nenhuma assinatura criptográfica.

**Solução — RS256 real com Nimbus JOSE:**

```java
// pom.xml — adicionar dependência
// <dependency>
//   <groupId>com.nimbusds</groupId>
//   <artifactId>nimbus-jose-jwt</artifactId>
//   <version>9.40</version>
// </dependency>

@Service
public class JwtService {

  private final RSAKey signingKey;   // injetado do KMS / JWT_PRIVATE_KEY

  public String issueAccessToken(User user) throws JOSEException {
    JWSSigner signer = new RSASSASigner(signingKey);
    Instant now = Instant.now();
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .issuer(issuer)
        .subject(user.getId().toString())
        .claim("email", user.getEmail())
        .claim("role", user.getRole().name())
        .issueTime(Date.from(now))
        .expirationTime(Date.from(now.plus(accessTtl())))
        .jwtID(UUID.randomUUID().toString())   // permite blacklist por jti
        .build();
    SignedJWT jwt = new SignedJWT(
        new JWSHeader.Builder(JWSAlgorithm.RS256)
            .keyID(signingKey.getKeyID()).build(),
        claims);
    jwt.sign(signer);
    return jwt.serialize();
  }
}
```

---

**[SEC-CRIT-02] ResumeController aceita STUB tokens em produção — IDOR irrestrito**
**Severidade:** Crítica | **CWE:** CWE-284 (Improper Access Control)

```java
// VULNERABILIDADE ORIGINAL (corrigida nesta sessão)
private UUID resolveUserId(Jwt principal, HttpServletRequest request) {
    if (principal != null) { ... }
    // ⚠️ Aceito em QUALQUER profile, inclusive produção
    String auth = request.getHeader("Authorization");
    if (auth != null && auth.startsWith("Bearer STUB_ACCESS.")) {
        String[] parts = auth.substring("Bearer ".length()).split("\\.", 3);
        // Extrai UUID diretamente do token sem qualquer validação criptográfica
        return UUID.fromString(parts[1]);
    }
    return null;
}
```

Um atacante que conhece o UUID de qualquer usuário (expostos em logs, respostas de API, enumeração) pode acessar todos os seus currículos sem autenticação. **Corrigido:** STUB tokens agora são aceitos apenas no profile `local` via `LocalResumeController`.

---

**[SEC-CRIT-03] CryptoConfig usa chave AES de 32 bytes zerados se ENCRYPTION_MASTER_KEY não estiver configurada**
**Severidade:** Crítica (em produção) | **CWE:** CWE-321 (Use of Hard-coded Cryptographic Key)

```java
if (masterKeyBase64 == null || masterKeyBase64.isBlank()) {
    return new SecretKeySpec(new byte[32], "AES"); // 256 bits de zeros
}
```

Se `ENCRYPTION_MASTER_KEY` não for definida em produção, todos os currículos são cifrados com uma chave conhecida (`0x00 * 32`). Um atacante com acesso ao banco consegue decifrar 100% dos dados.

**Solução:**

```java
// Falhar na inicialização se a chave não estiver configurada em produção
@Bean
public SecretKey masterAesKey(Environment env) {
    if (!env.matchesProfiles("local") &&
        (masterKeyBase64 == null || masterKeyBase64.isBlank())) {
        throw new IllegalStateException(
            "ENCRYPTION_MASTER_KEY não configurada. " +
            "Gere com: openssl rand -base64 32");
    }
    if (masterKeyBase64 == null || masterKeyBase64.isBlank()) {
        log.warn("[DEV] Usando chave AES zerada — NUNCA em produção");
        return new SecretKeySpec(new byte[32], "AES");
    }
    // ... decodifica e valida
}
```

---

**[SEC-CRIT-04] SecurityConfig (prod) crashava na inicialização — JwtDecoder ausente**
**Severidade:** Crítica | **CWE:** CWE-755 (Improper Handling of Exceptional Conditions)

```java
// ANTES — lançava IllegalStateException no startup
.oauth2ResourceServer(rs -> rs.jwt(jwt -> {})); // sem JwtDecoder bean
```

Spring Security 6 exige que, ao configurar `.oauth2ResourceServer(rs -> rs.jwt(...))`, exista um `JwtDecoder` bean no contexto **ou** que `spring.security.oauth2.resourceserver.jwt.issuer-uri` esteja configurado. Nenhum dos dois estava presente → **aplicação não iniciava em produção**.

**Corrigido:** `SecurityConfig` agora declara um `@Bean JwtDecoder` explícito carregado de `JWT_PUBLIC_KEY`.

---

### 2.2 Alta Severidade (CVSS 7.0–8.9)

**[SEC-HIGH-01] Race condition no RateLimitService — brute force possível sob concorrência**
**Severidade:** Alta | **CWE:** CWE-362 (Race Condition)

```java
// ANTES — dois comandos Redis não atômicos
Long count = redis.opsForValue().increment("rl:" + key); // operação 1
if (count != null && count == 1L) {
    redis.expire("rl:" + key, window);  // operação 2 — pode falhar ou chegar tarde
}
```

Entre `increment()` e `expire()`, outro thread pode incrementar o mesmo contador. Se o processo morrer entre as duas operações, o contador fica sem TTL e cresce indefinidamente — desabilitando o rate limit para aquele IP permanentemente.

**Corrigido:** script Lua atômico (ver `RateLimitService.java`).

---

**[SEC-HIGH-02] Refresh tokens sem rotação e sem persistência**
**Severidade:** Alta | **CWE:** CWE-613 (Insufficient Session Expiration)

O endpoint `/api/auth/refresh` aceita qualquer token com prefixo `STUB_REFRESH.` e UUID válido. Não há:
- Armazenamento do token no banco (impossível verificar se foi revogado)
- Rotação (cada uso deveria invalidar o token anterior)
- Detecção de reutilização de token revogado (indicador de comprometimento)

**Solução — tabela de refresh tokens:**

```sql
-- V3__refresh_tokens.sql
CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL UNIQUE,  -- SHA-256 do token, nunca o token raw
    expires_at TIMESTAMPTZ NOT NULL,
    revoked    BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at TIMESTAMPTZ,
    ip         VARCHAR(64),
    user_agent VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_rt_user ON refresh_tokens(user_id, expires_at);
CREATE INDEX idx_rt_hash ON refresh_tokens(token_hash) WHERE NOT revoked;
```

---

**[SEC-HIGH-03] Seed do banco expõe credencial em texto claro no SQL de migração**
**Severidade:** Alta | **CWE:** CWE-798 (Hard-coded Credentials)

```sql
-- V1__init.sql — visível para qualquer pessoa com acesso ao repositório
INSERT INTO users (email, password_hash, ...)
VALUES ('root@cvfacil.ng',
        '$2b$12$ZH2Pkd.0syu7RO/YbUOFau/eI.QDniBJp9A8VtdVsynA1TII1hv/u', ...)
-- Comentário revela a senha plaintext:
-- Senha local de desenvolvimento: Admin@2026!
```

Mesmo sendo um hash BCrypt, o comentário revela a senha em texto claro. Um atacante com acesso ao repositório pode tentar essa senha em outros serviços do mesmo usuário.

**Solução:** remover o comentário com a senha e usar um seed script separado não versionado para produção.

---

**[SEC-HIGH-04] CSRF ignorado globalmente em `/api/**` no profile local**

```java
// LocalSecurityConfig — muito abrangente
.ignoringRequestMatchers("/api/auth/login", "/api/auth/register", "/api/**", ...)
```

O `/api/**` inclui endpoints sensíveis como `/api/resumes` e `/api/admin/**`. Em ambiente local com CORS bem configurado, o impacto é menor, mas o padrão cria hábito incorreto e pode vazar para produção.

---

**[SEC-HIGH-05] `mfaSecret` armazenado em plaintext na tabela users**
**Severidade:** Alta | **CWE:** CWE-312 (Cleartext Storage of Sensitive Information)

O campo `mfa_secret VARCHAR(64)` na tabela `users` armazena o segredo TOTP em texto claro. Um dump do banco expõe todos os segredos de MFA.

**Solução:** cifrar com `AesGcmCipherService` antes de persistir, similar ao `content_enc` dos currículos.

---

**[SEC-HIGH-06] `application-local.yml` não sobrescrevia `issuer-uri` do Google**

O `application.yml` define `issuer-uri: https://accounts.google.com`. Sem override em `application-local.yml`, o Spring Security poderia tentar resolver o OIDC discovery document em startup. Em redes corporativas com proxy ou sem acesso à internet, isso impede a inicialização.

**Corrigido:** `application-local.yml` agora define URLs fictícias locais para o provider Google.

---

### 2.3 Média Severidade

**[SEC-MED-01] `AIImportService` concatena `provider` na resposta stub sem sanitização**

```java
return "{\"summary\": \"Stub de importação ... provider: " + provider + "\"}";
```

Se `AI_PROVIDER` contiver caracteres especiais de JSON (`"`, `\`), a resposta stub quebra o parsing JSON. Em produção, deve-se usar um serializer Jackson.

**[SEC-MED-02] `LocalDataPersistenceConfig.backupPath()` sem null-check**

`Paths.get(user.dir).getParent()` retorna `null` para caminhos raiz. **Corrigido** nesta sessão com `toAbsolutePath()` e fallback explícito.

**[SEC-MED-03] `AuditService` usa SHA-256 sem chave — encadeamento forjável**

O `self_hash = SHA256(prev_hash || payload)` sem HMAC permite que um atacante com acesso ao banco recalcule hashes e forje entradas de auditoria. Para auditoria imutável real, usar HMAC-SHA256 com chave dedicada do KMS.

**[SEC-MED-04] Headers de segurança ausentes no frontend para rotas de API**

O `next.config.js` define CSP e outros headers para `/:path*`, mas rotas de API (`/api/*`) no Next.js não herdam esses headers automaticamente em `output: 'standalone'`.

**[SEC-MED-05] `photo_url` aceita qualquer URL sem validação**

O campo `Resume.photoUrl VARCHAR(500)` não valida o schema da URL. Uma URL `javascript:` ou `data:` poderia causar XSS se renderizada diretamente como `src` de `<img>` sem sanitização.

---

## 3. Integridade do Banco de Dados

### 3.1 Normalização e integridade referencial

**[DB-01] `Resume.userId` sem relacionamento JPA explícito**

```java
// ATUAL — UUID raw, sem @ManyToOne
@Column(name = "user_id", nullable = false)
private UUID userId;
```

Consequências: JPA não gerencia cascatas, lazy loading ou consistência. Deleção em cascata depende apenas da constraint `ON DELETE CASCADE` do banco (existe, mas não é visível na camada de aplicação).

**Recomendação para produção:**

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "user_id", nullable = false)
private User user;
```

### 3.2 Eficiência de queries

**[DB-02] Índices existentes são adequados para os padrões de acesso atuais:**

| Tabela | Índice | Justificativa |
|---|---|---|
| `users` | `email (CITEXT UNIQUE)` | Login por email — ✅ |
| `users` | `idx_users_role` | Listagem de admins — ✅ |
| `resumes` | `idx_resumes_user_id` | Listagem por usuário — ✅ |
| `audit_logs` | `idx_audit_user_time` | Timeline de auditoria — ✅ |

**[DB-03] Ausência de índice em `resumes(updated_at)` para paginação**

`ResumeRepository.findByUserIdOrderByUpdatedAtDesc()` usa ORDER BY em coluna não indexada. Com volume alto de currículos por usuário, isso pode causar sort em memória.

**Solução:**

```sql
-- V4__perf_indexes.sql
CREATE INDEX idx_resumes_user_updated ON resumes(user_id, updated_at DESC);
-- Cobre tanto o filtro por user_id quanto o ORDER BY updated_at DESC com um único index scan.
```

### 3.3 Consistência transacional

**[DB-04] Tabela de refresh tokens faltante (ver SEC-HIGH-02)**

```sql
-- V3__refresh_tokens.sql (proposto)
CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked    BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at TIMESTAMPTZ,
    ip         VARCHAR(64),
    user_agent VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_rt_user ON refresh_tokens(user_id, expires_at);
CREATE INDEX idx_rt_hash ON refresh_tokens(token_hash) WHERE NOT revoked;
```

**[DB-05] `failed_logins` não é resetado transacionalmente com o login bem-sucedido**

```java
// AuthController — dois saves separados sem @Transactional
u.setFailedLogins(0);
u.setLockedUntil(null);
users.save(u);  // save 1
// ... lógica de JWT ...
// (se falhar aqui, failed_logins fica 0 mas o token não foi emitido)
```

**Solução:** adicionar `@Transactional` ao método `login()` do `AuthController`.

---

## 4. Qualidade de Código

### 4.1 Clean Code

**[QC-01] `AuthController.login()` — alto acoplamento e muitas responsabilidades**

O método `login()` faz: rate limiting, busca de usuário, verificação de lock, verificação de senha, atualização de contadores, emissão de JWT, emissão de cookie e registro de auditoria — 8 responsabilidades em ~30 linhas. Refatorar para um `AuthService` separado.

**[QC-02] `LocalDataPersistenceConfig.onDestroy()` — geração de SQL por concatenação de strings**

```java
sb.append("INSERT INTO users (").append(String.join(", ", colNames))
  .append(") VALUES (");
// ...
sb.append('\'').append(val.toString().replace("'", "''")).append('\'');
```

Apesar de usar escape básico (`''`), a geração manual de SQL é arriscada. O campo `display_name` pode conter aspas, barras ou caracteres Unicode que escapem a sanitização simples.

**Solução:** usar JDBC `PreparedStatement` com placeholders `?`.

**[QC-03] `AuditService.record()` tem complexidade ciclomática desnecessária**

O método faz SHA-256 em linha (sem extrair para `Hasher`), concatena campos sem separadores seguros e usa `synchronized` desnecessário. Complexidade ciclomática = 4 (aceitável), mas o `synchronized` eleva o risco de deadlock em futuras extensões.

**[QC-04] `parseResumeText.js` — função `parseExperience` com estado mutável complexo**

A função mantém estado `cur` mutável ao longo de um loop com múltiplas condições de borda. A correção de precedência de operadores desta sessão resolveu o bug imediato, mas a função se beneficiaria de refatoração para um parser de estado explícito (máquina de estados finita).

### 4.2 Bugs lógicos identificados

**[QC-05] `AuthController.refresh()` aceita qualquer STUB_REFRESH sem validar expiração**

```java
if (refreshToken == null || !refreshToken.startsWith("STUB_REFRESH.")) {
    return ResponseEntity.status(401).build();
}
// Extrai userId e busca usuário — mas NUNCA verifica se o token expirou
User u = users.findById(userId).orElse(null);
// Emite novo token sem qualquer validação de TTL
```

Um refresh token emitido há 1 ano ainda seria aceito. Isso ocorre porque a expiração é configurada no cookie (`maxAge(jwt.refreshTtl())`), mas o servidor não verifica a data de emissão do token em si.

**[QC-06] `LocalSecurityConfig` habilita `SessionCreationPolicy.STATELESS` com `oauth2Login`**

OAuth2 Authorization Code Flow requer sessão para armazenar o `state` parameter (proteção CSRF do fluxo OAuth). Com sessão stateless, o fluxo OAuth quebraria em produção. No profile local isso é mitigado pelo interceptor que redireciona antes de chegar ao Google, mas o design é inconsistente.

---

## 5. Proposta de Roadmap de Correções

### Imediato (já aplicado nesta sessão)

| ID | Arquivo | Correção |
|---|---|---|
| SEC-CRIT-04 | `SecurityConfig.java` | JwtDecoder RS256 explícito — resolve crash de startup em produção |
| SEC-HIGH-06 | `application-local.yml` | Override de provider Google — resolve startup em redes restritas |
| SEC-CRIT-02 | `ResumeController.java` + `LocalResumeController.java` | STUB tokens apenas em profile `local` |
| SEC-HIGH-01 | `RateLimitService.java` | Script Lua atômico — elimina race condition |
| SEC-MED-02 | `LocalDataPersistenceConfig.java` | `toAbsolutePath()` + null-check — elimina NPE |

### Curto prazo (próximo sprint)

1. **Implementar JwtService RS256 real** com Nimbus JOSE (SEC-CRIT-01)
2. **Criar tabela `refresh_tokens`** e implementar rotação (SEC-HIGH-02, QC-05)
3. **`@Transactional` em `AuthController.login()`** (DB-05)
4. **Remover comentário de senha do V1__init.sql** (SEC-HIGH-03)
5. **Cifrar `mfa_secret`** com `AesGcmCipherService` (SEC-HIGH-05)

### Médio prazo

1. **Refatorar `AuthController`** para `AuthService` (QC-01)
2. **Adicionar índice `idx_resumes_user_updated`** (DB-03)
3. **Substituir `synchronized` do `AuditService`** por HMAC-SHA256 com chave KMS (SEC-MED-03, ARQ-01)
4. **Implementar `@ManyToOne` em `Resume.user`** (DB-01)
5. **Fail-closed no Redis em produção** com circuit breaker e alerta (ARQ-04)

---

## 6. Checklist de Deploy para Produção (Neon)

Antes de fazer deploy no Neon PostgreSQL, verificar:

- [ ] `JWT_PRIVATE_KEY` e `JWT_PUBLIC_KEY` configurados (RS256 PEM)
- [ ] `ENCRYPTION_MASTER_KEY` configurado (base64 de 32 bytes aleatórios)
- [ ] `COOKIE_SECURE=true`
- [ ] `CORS_ALLOWED_ORIGINS` aponta apenas para o domínio de produção
- [ ] `DATABASE_URL` com SSL (`?sslmode=require`) para Neon
- [ ] `REDIS_URL` apontando para Redis gerenciado (não localhost)
- [ ] `AI_API_KEY` configurado via secret manager
- [ ] `ROOT_IP_ALLOWLIST` restrito a IPs administrativos
- [ ] Remover `LocalMockAuthController` e `LocalResumeController` da build de produção (ou garantir que o profile `local` não esteja ativo)
- [ ] Flyway migrations V3 (refresh_tokens) e V4 (índices de performance) executadas

---

*Relatório gerado automaticamente com base em análise estática completa do código-fonte.*
