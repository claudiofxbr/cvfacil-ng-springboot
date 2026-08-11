#!/usr/bin/env bash
# =============================================================================
# start-cvfacil.sh
# -----------------------------------------------------------------------------
# Equivalente POSIX do start-cvfacil.ps1: limpa caches, sobe o backend
# Spring Boot, aguarda healthcheck e em seguida sobe o frontend Next.js.
#
# Uso:  ./scripts/start-cvfacil.sh            (tudo)
#       SKIP_CLEAN=1 ./scripts/start-cvfacil.sh
#       SKIP_INSTALL=1 NO_BROWSER=1 ./scripts/start-cvfacil.sh
# =============================================================================
set -euo pipefail

BACKEND_PORT="${BACKEND_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-3000}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-180}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKEND_DIR="$REPO_ROOT/backend"
FRONTEND_DIR="$REPO_ROOT/frontend"
LOG_DIR="$REPO_ROOT/.runtime/logs"
PID_DIR="$REPO_ROOT/.runtime/pids"
mkdir -p "$LOG_DIR" "$PID_DIR"
BACKEND_LOG="$LOG_DIR/backend.log"
FRONTEND_LOG="$LOG_DIR/frontend.log"

# -----------------------------------------------------------------------------
# UI
# -----------------------------------------------------------------------------
if [ -t 1 ]; then
    C_CYAN="\033[36m"; C_GREEN="\033[32m"; C_YELLOW="\033[33m"
    C_RED="\033[31m";  C_GRAY="\033[90m";  C_RESET="\033[0m"
else
    C_CYAN=""; C_GREEN=""; C_YELLOW=""; C_RED=""; C_GRAY=""; C_RESET=""
fi
section() { printf "\n${C_CYAN}==> %s${C_RESET}\n" "$*"; }
ok()      { printf "  ${C_GREEN}[OK]${C_RESET} %s\n"  "$*"; }
info()    { printf "  ${C_GRAY}[..]${C_RESET} %s\n"   "$*"; }
warn()    { printf "  ${C_YELLOW}[!!]${C_RESET} %s\n" "$*"; }
err()     { printf "  ${C_RED}[XX]${C_RESET} %s\n"    "$*" >&2; }

BACKEND_PID=""
FRONTEND_PID=""

cleanup() {
    section "Encerrando processos"
    if [ -n "$FRONTEND_PID" ] && kill -0 "$FRONTEND_PID" 2>/dev/null; then
        kill "$FRONTEND_PID" 2>/dev/null || true
        info "Frontend PID $FRONTEND_PID sinalizado"
    fi
    if [ -n "$BACKEND_PID" ] && kill -0 "$BACKEND_PID" 2>/dev/null; then
        kill "$BACKEND_PID" 2>/dev/null || true
        info "Backend PID $BACKEND_PID sinalizado"
    fi
    # grace period
    sleep 1
    [ -n "$FRONTEND_PID" ] && kill -9 "$FRONTEND_PID" 2>/dev/null || true
    [ -n "$BACKEND_PID" ]  && kill -9 "$BACKEND_PID"  2>/dev/null || true
    ok "Bye."
}
trap cleanup EXIT INT TERM

# -----------------------------------------------------------------------------
# Banner
# -----------------------------------------------------------------------------
echo ""
echo "  +---------------------------------------------+"
echo "  |         CVFacil.NG - launcher local         |"
echo "  +---------------------------------------------+"
echo "  Repo:      $REPO_ROOT"
echo "  Backend:   http://localhost:$BACKEND_PORT"
echo "  Frontend:  http://localhost:$FRONTEND_PORT"

# -----------------------------------------------------------------------------
# Pre-requisitos
# -----------------------------------------------------------------------------
section "Verificando pre-requisitos"

require_cmd() {
    local name="$1" hint="$2"
    if ! command -v "$name" >/dev/null 2>&1; then
        err "$name nao encontrado no PATH ($hint)"
        exit 1
    fi
    ok "$name disponivel"
}
require_cmd java "instale Java 21"
require_cmd node "instale Node 20"
require_cmd npm  "instale npm >= 9"
require_cmd curl "instale curl"

# Java: tenta `java -version` (stderr) e depois `--version`. Cobre formatos
# `version "21.0.1"`, `version "1.8.0_391"` (1.8 -> 8) e `openjdk 21.0.1`.
detect_java_major() {
    local out=""
    out="$(java -version 2>&1 || true)"
    [ -z "$out" ] && out="$(java --version 2>&1 || true)"
    if [[ "$out" =~ version\ \"([0-9]+)(\.([0-9]+))? ]]; then
        local m1="${BASH_REMATCH[1]}"; local m2="${BASH_REMATCH[3]}"
        if [ "$m1" = "1" ] && [ -n "$m2" ]; then echo "$m2"; else echo "$m1"; fi
        return
    fi
    if [[ "$out" =~ ([0-9]+)\.[0-9]+\.[0-9]+ ]]; then
        echo "${BASH_REMATCH[1]}"; return
    fi
    echo ""
}

ensure_java21() {
    local tools="$REPO_ROOT/.runtime/tools"
    # Ja tem JDK 21 local provisionado?
    local local_jdk=""
    if [ -d "$tools" ]; then
        local_jdk="$(ls -d "$tools"/jdk-21* 2>/dev/null | head -n1 || true)"
    fi
    if [ -n "$local_jdk" ] && [ -d "$local_jdk" ]; then
        local home="$local_jdk"
        [ -d "$local_jdk/Contents/Home" ] && home="$local_jdk/Contents/Home"
        export JAVA_HOME="$home"
        export PATH="$home/bin:$PATH"
        local m; m="$(detect_java_major)"
        if [ -n "$m" ] && [ "$m" -ge 21 ]; then
            ok "JDK 21 local: $home (major $m)"
            return
        fi
        warn "JDK local em $home nao respondeu como 21; ignorando."
        unset JAVA_HOME
    fi
    # Tenta o Java do sistema
    local m; m="$(detect_java_major)"
    if [ -n "$m" ] && [ "$m" -ge 21 ]; then
        ok "java OK (major $m)"; return
    fi
    if [ -z "$m" ]; then
        warn "Java nao encontrado; provisionando Temurin 21 local."
    else
        warn "Java $m detectado, requer 21. Provisionando Temurin 21 local."
    fi
    mkdir -p "$tools"
    local os arch suffix ext
    case "$(uname -s)" in
        Linux)  os="linux" ; ext="tar.gz" ;;
        Darwin) os="mac"   ; ext="tar.gz" ;;
        *) err "SO nao suportado para auto-install de JDK: $(uname -s)"; exit 1 ;;
    esac
    case "$(uname -m)" in
        x86_64|amd64)     arch="x64" ;;
        aarch64|arm64)    arch="aarch64" ;;
        *) err "Arquitetura nao suportada: $(uname -m)"; exit 1 ;;
    esac
    local url="https://api.adoptium.net/v3/binary/latest/21/ga/${os}/${arch}/jdk/hotspot/normal/eclipse"
    local tmp="$tools/temurin21.$ext"
    info "Baixando Temurin 21 (~200 MB) de Adoptium..."
    if ! curl -fsSL -o "$tmp" "$url"; then
        err "Falha ao baixar JDK 21. Instale manualmente: https://adoptium.net/temurin/releases/?version=21"
        exit 1
    fi
    info "Extraindo JDK..."
    tar -xz -C "$tools" -f "$tmp"
    rm -f "$tmp"
    local_jdk="$(ls -d "$tools"/jdk-21* 2>/dev/null | head -n1 || true)"
    if [ -z "$local_jdk" ]; then err "Pasta jdk-21* nao encontrada apos extracao."; exit 1; fi
    local home="$local_jdk"
    [ -d "$local_jdk/Contents/Home" ] && home="$local_jdk/Contents/Home"
    export JAVA_HOME="$home"
    export PATH="$home/bin:$PATH"
    m="$(detect_java_major)"
    if [ -z "$m" ] || [ "$m" -lt 21 ]; then
        err "JDK extraido mas detecao reporta '$m'."; exit 1
    fi
    ok "JDK 21 instalado em $home (major $m)"
}

ensure_java21
JAVA_MAJOR="$(detect_java_major)"
NODE_MAJOR=$(node -v 2>/dev/null | sed -E 's/v([0-9]+).*/\1/')
if [ -n "$NODE_MAJOR" ] && [ "$NODE_MAJOR" -lt 20 ]; then
    err "Node $NODE_MAJOR detectado, requer >= 20"
    exit 1
fi
ok "Java ${JAVA_MAJOR:-?} / Node $NODE_MAJOR"

# Maven: wrapper -> global -> auto-download para .runtime/tools/
if [ -x "$BACKEND_DIR/mvnw" ]; then
    MVN="$BACKEND_DIR/mvnw"
    ok "Usando mvnw"
elif command -v mvn >/dev/null 2>&1; then
    MVN="$(command -v mvn)"
    ok "Usando mvn global"
else
    MVN_VER="3.9.6"
    TOOLS_DIR="$REPO_ROOT/.runtime/tools"
    MVN_ROOT="$TOOLS_DIR/apache-maven-$MVN_VER"
    if [ ! -x "$MVN_ROOT/bin/mvn" ]; then
        warn "Maven nao encontrado; baixando apache-maven-$MVN_VER..."
        mkdir -p "$TOOLS_DIR"
        URL="https://archive.apache.org/dist/maven/maven-3/$MVN_VER/binaries/apache-maven-$MVN_VER-bin.tar.gz"
        if ! curl -fsSL "$URL" | tar -xz -C "$TOOLS_DIR"; then
            err "Falha ao baixar Maven. Instale Maven 3.9+ manualmente."
            exit 1
        fi
    fi
    if [ ! -x "$MVN_ROOT/bin/mvn" ]; then
        err "Instalacao local de Maven falhou em $MVN_ROOT."
        exit 1
    fi
    MVN="$MVN_ROOT/bin/mvn"
    ok "Maven local: $MVN"
fi

# -----------------------------------------------------------------------------
# Env files
# -----------------------------------------------------------------------------
section "Checando arquivos de ambiente"
ensure_env() {
    local target="$1" example="$2"
    if [ -f "$target" ]; then
        ok "Encontrado: $(basename "$target")"
    elif [ -f "$example" ]; then
        cp "$example" "$target"
        warn "Copiado de exemplo: $(basename "$target") (revise!)"
    else
        warn "Nenhum exemplo em $(basename "$example"); seguindo."
    fi
}
ensure_env "$BACKEND_DIR/src/main/resources/application-local.yml" \
           "$BACKEND_DIR/src/main/resources/application-local.yml.example"
ensure_env "$FRONTEND_DIR/.env.local" "$REPO_ROOT/.env.example"

# -----------------------------------------------------------------------------
# Limpeza de caches
# -----------------------------------------------------------------------------
if [ "${SKIP_CLEAN:-0}" != "1" ]; then
    section "Limpando caches"
    for p in "$FRONTEND_DIR/.next" "$FRONTEND_DIR/node_modules/.cache" "$BACKEND_DIR/target"; do
        if [ -e "$p" ]; then
            info "Removendo $p"
            rm -rf "$p"
            ok "Removido $(basename "$p")"
        fi
    done
    info "Maven clean..."
    ( cd "$BACKEND_DIR" && "$MVN" -q clean )
    ok "Maven clean OK"
else
    section "Limpeza ignorada (SKIP_CLEAN=1)"
fi

# -----------------------------------------------------------------------------
# Dependencias frontend
# -----------------------------------------------------------------------------
section "Dependencias do frontend"
if [ ! -d "$FRONTEND_DIR/node_modules" ] && [ "${SKIP_INSTALL:-0}" != "1" ]; then
    info "node_modules ausente; npm ci..."
    if [ -f "$FRONTEND_DIR/package-lock.json" ]; then
        ( cd "$FRONTEND_DIR" && npm ci )
    else
        ( cd "$FRONTEND_DIR" && npm install )
    fi
    ok "Dependencias instaladas"
else
    ok "node_modules ok"
fi

# -----------------------------------------------------------------------------
# Backend
# -----------------------------------------------------------------------------
# ABORDAGEM ROBUSTA: em vez de `mvn spring-boot:run` (que em Windows cria um
# grandchild JVM e o wrapper .cmd confunde o tracker de PID), empacotamos o
# JAR uma vez com `mvn package` e depois rodamos `java -jar` direto. O PID
# que guardamos e o da propria JVM.
section "Subindo backend (Spring Boot)"
: > "$BACKEND_LOG"

# 1) Localiza (ou gera) o JAR
JAR=""
if [ -d "$BACKEND_DIR/target" ]; then
    JAR=$(ls -t "$BACKEND_DIR"/target/cvfacil-backend-*.jar 2>/dev/null \
          | grep -vE '\-plain\.jar$|\-sources\.jar$' | head -n1 || true)
fi
if [ -z "$JAR" ]; then
    info "Gerando jar do backend (mvn package -DskipTests)..."
    ( cd "$BACKEND_DIR" && "$MVN" -q -DskipTests -Dspotless.check.skip=true package ) \
        | tee "$LOG_DIR/backend.build.log" || {
            err "mvn package falhou. Veja $LOG_DIR/backend.build.log"
            exit 1
        }
    JAR=$(ls -t "$BACKEND_DIR"/target/cvfacil-backend-*.jar 2>/dev/null \
          | grep -vE '\-plain\.jar$|\-sources\.jar$' | head -n1 || true)
    [ -z "$JAR" ] && { err "JAR nao encontrado apos build."; exit 1; }
    ok "JAR criado: $JAR"
else
    info "Reutilizando JAR: $JAR"
fi

# 2) Resolve java
JAVA_BIN="$(command -v java || true)"
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_BIN="$JAVA_HOME/bin/java"
fi
[ -z "$JAVA_BIN" ] && { err "java nao resolvido."; exit 1; }

# 3) Launch direto da JVM
(
    cd "$BACKEND_DIR"
    SPRING_PROFILES_ACTIVE=local \
    "$JAVA_BIN" -jar "$JAR" \
      --spring.profiles.active=local \
      --server.port="$BACKEND_PORT" \
      > "$BACKEND_LOG" 2>&1
) &
BACKEND_PID=$!
echo "$BACKEND_PID" > "$PID_DIR/backend.pid"
ok "Backend PID $BACKEND_PID (log: $BACKEND_LOG)"

HEALTH_URL="http://localhost:$BACKEND_PORT/api/health"
info "Aguardando $HEALTH_URL (ate ${HEALTH_TIMEOUT}s)..."
START=$(date +%s)
READY=0
while :; do
    if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
        err "Backend morreu. Ultimas linhas do log:"
        tail -n 40 "$BACKEND_LOG" >&2 || true
        exit 1
    fi
    if curl -fsS --max-time 3 "$HEALTH_URL" 2>/dev/null | grep -q '"UP"'; then
        READY=1; break
    fi
    NOW=$(date +%s); ELAPSED=$(( NOW - START ))
    if [ "$ELAPSED" -ge "$HEALTH_TIMEOUT" ]; then break; fi
    printf "."
    sleep 2
done
echo ""
[ "$READY" = "1" ] || { err "Backend nao respondeu UP. Log: $BACKEND_LOG"; exit 1; }
ok "Backend healthcheck UP"

# -----------------------------------------------------------------------------
# Frontend
# -----------------------------------------------------------------------------
section "Subindo frontend (Next.js)"
: > "$FRONTEND_LOG"
(
    cd "$FRONTEND_DIR"
    PORT=$FRONTEND_PORT npm run dev -- -p $FRONTEND_PORT > "$FRONTEND_LOG" 2>&1
) &
FRONTEND_PID=$!
echo "$FRONTEND_PID" > "$PID_DIR/frontend.pid"
ok "Frontend PID $FRONTEND_PID (log: $FRONTEND_LOG)"

HOME_URL="http://localhost:$FRONTEND_PORT/"
info "Aguardando $HOME_URL..."
START=$(date +%s)
READY=0
while :; do
    if ! kill -0 "$FRONTEND_PID" 2>/dev/null; then
        err "Frontend morreu. Log:"
        tail -n 40 "$FRONTEND_LOG" >&2 || true
        exit 1
    fi
    if curl -fsS -o /dev/null --max-time 3 "$HOME_URL" 2>/dev/null; then
        READY=1; break
    fi
    NOW=$(date +%s); ELAPSED=$(( NOW - START ))
    if [ "$ELAPSED" -ge 120 ]; then break; fi
    printf "."
    sleep 2
done
echo ""
[ "$READY" = "1" ] || { err "Frontend nao respondeu. Log: $FRONTEND_LOG"; exit 1; }
ok "Frontend respondendo"

# -----------------------------------------------------------------------------
# Sucesso
# -----------------------------------------------------------------------------
echo ""
echo "  +---------------------------------------------+"
echo "  |   CVFacil.NG operacional - Ctrl+C encerra   |"
echo "  +---------------------------------------------+"
echo "  Frontend: http://localhost:$FRONTEND_PORT"
echo "  Backend:  http://localhost:$BACKEND_PORT/api/health"
echo "  Logs:     $LOG_DIR"
echo ""

if [ "${NO_BROWSER:-0}" != "1" ]; then
    if command -v xdg-open >/dev/null 2>&1; then xdg-open "http://localhost:$FRONTEND_PORT" >/dev/null 2>&1 || true
    elif command -v open    >/dev/null 2>&1; then open    "http://localhost:$FRONTEND_PORT" >/dev/null 2>&1 || true
    fi
fi

# Bloqueia ate um dos dois morrer; trap cuida do resto.
wait -n "$BACKEND_PID" "$FRONTEND_PID" || true
