#!/bin/bash

# CVfacil.NG - Cleanup Script (Bash/Linux/macOS)
# Limpa todos os arquivos temporários, caches e processos

set -euo pipefail

readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly CYAN='\033[0;36m'
readonly NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"

log_info() { echo -e "${CYAN}ℹ️  INFO${NC}: $*"; }
log_success() { echo -e "${GREEN}✅ SUCCESS${NC}: $*"; }
log_warning() { echo -e "${YELLOW}⚠️  WARNING${NC}: $*"; }
log_error() { echo -e "${RED}❌ ERROR${NC}: $*" >&2; }

echo -e "${CYAN}"
echo "╔════════════════════════════════════════════════════════════╗"
echo "║             CVfacil.NG CLEANUP                             ║"
echo "║        Limpando arquivos temporários e caches...         ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo -e "${NC}"

# Parar servidor se estiver rodando
if [[ -f "$PROJECT_ROOT/.server.pid" ]]; then
    pid=$(cat "$PROJECT_ROOT/.server.pid")
    if kill -0 "$pid" 2>/dev/null; then
        log_info "Parando servidor (PID: $pid)..."
        kill "$pid" 2>/dev/null || true
        rm "$PROJECT_ROOT/.server.pid"
        log_success "Servidor parado"
    fi
fi

# Limpeza de diretórios
dirs_to_clean=(".next" ".turbo" "dist" "build" "coverage" "logs" ".env.local.bak" ".webpack")
for dir in "${dirs_to_clean[@]}"; do
    path="$PROJECT_ROOT/$dir"
    if [[ -d "$path" ]]; then
        log_info "Removendo: $dir"
        rm -rf "$path"
    fi
done

# Limpeza de arquivos
files_to_clean=(".server.pid" "*.log" ".npmrc.bak")
for pattern in "${files_to_clean[@]}"; do
    if [[ "$pattern" == "*.log" ]]; then
        find "$PROJECT_ROOT" -maxdepth 1 -name "$pattern" -delete 2>/dev/null || true
    else
        path="$PROJECT_ROOT/$pattern"
        [[ -f "$path" ]] && rm "$path"
    fi
done

# Limpar cache npm
log_info "Limpando cache npm..."
npm cache clean --force > /dev/null 2>&1 || true

# Limpar cache yarn (se houver)
if [[ -f "$PROJECT_ROOT/yarn.lock" ]]; then
    log_info "Limpando cache yarn..."
    yarn cache clean > /dev/null 2>&1 || true
fi

echo ""
log_success "Limpeza completa!"
echo ""
echo "Resumo:"
echo "  ✓ Servidor parado"
echo "  ✓ Diretórios de build removidos"
echo "  ✓ Logs removidos"
echo "  ✓ Cache npm/yarn limpo"
echo ""
echo "Para reiniciar: ./start.sh"
