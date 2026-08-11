# `scripts/` — utilitários de desenvolvimento local

## Inicializar a stack completa

Os scripts abaixo fazem, nesta ordem:

1. Verificam Java 21, Node 20 e Maven. Ordem de resolução do Maven:
   `backend/mvnw[.cmd]` → `mvn` no `PATH` → auto-download de
   `apache-maven-3.9.6` para `.runtime/tools/` (primeira execução só).
2. Garantem que existam `backend/src/main/resources/application-local.yml` e
   `frontend/.env.local` — copiam dos `.example` se faltarem.
3. Limpam `.next`, `node_modules/.cache`, `target/` e rodam `mvn clean`.
4. Executam `npm ci` no frontend se `node_modules` não existir.
5. Sobem o backend Spring Boot em background e aguardam `/api/health` = UP.
6. Sobem o frontend Next.js em background e aguardam a home responder.
7. Abrem o navegador em `http://localhost:3000` e ficam monitorando os dois
   processos. Ctrl+C encerra backend e frontend juntos.

### Windows

Duplo-clique em **`start-cvfacil.bat`** (ou execute via PowerShell):

```powershell
powershell -ExecutionPolicy Bypass -File scripts\start-cvfacil.ps1
```

Flags úteis do PowerShell:

| Flag             | Efeito                                               |
|------------------|------------------------------------------------------|
| `-SkipClean`     | Pula a limpeza de caches e `mvn clean`.              |
| `-SkipInstall`   | Não roda `npm ci` mesmo sem `node_modules`.          |
| `-NoBrowser`     | Não abre o navegador automaticamente.                |
| `-BackendPort N` | Porta do backend (default `8080`).                   |
| `-FrontendPort N`| Porta do frontend (default `3000`).                  |

### Linux / macOS

```bash
chmod +x scripts/start-cvfacil.sh   # primeira vez
./scripts/start-cvfacil.sh
```

Variáveis de ambiente aceitas:

| Variável          | Default | Efeito                               |
|-------------------|---------|--------------------------------------|
| `SKIP_CLEAN=1`    | —       | Pula limpeza de caches.              |
| `SKIP_INSTALL=1`  | —       | Pula `npm ci`.                       |
| `NO_BROWSER=1`    | —       | Não abre navegador.                  |
| `BACKEND_PORT`    | 8080    | Porta do backend.                    |
| `FRONTEND_PORT`   | 3000    | Porta do frontend.                   |
| `HEALTH_TIMEOUT`  | 180     | Segundos de espera pelo healthcheck. |

## Runtime artefacts

Ambos os scripts gravam em:

```
.runtime/
├── logs/
│   ├── backend.log
│   └── frontend.log
└── pids/
    ├── backend.pid
    └── frontend.pid
```

Essa pasta é ignorada pelo Git (ver `.gitignore`).
