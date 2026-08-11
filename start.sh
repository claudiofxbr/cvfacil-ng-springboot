#!/bin/bash

################################################################################
#                                                                              #
#              CVfacil.NG - Startup Script (Bash/Linux/macOS)                #
#                                                                              #
#  Este script automatiza a inicialização completa do aplicativo CVfacil.NG  #
#  com limpeza de ambiente, instalação de dependências e abertura automática  #
#  do navegador.                                                              #
#                                                                              #
#  Uso: ./start.sh [opções]                                                  #
#  Opções: --skip-clean, --skip-install, --no-browser                        #
#                                                                              #
################################################################################

set -euo pipefail

# Cores para output
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly CYAN='\033[0;36m'
readonly NC='\033[0m'

# Diretórios
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$SCRIPT_DIR"
readonly NODE_MODULES="$PROJECT_ROOT/node_modules"
readonly LOG_DIR="$PROJECT_ROOT/logs"

# Configurações
readonly APP_PORT=3000
readonly APP_URL="http://localhost:$APP_PORT"

# Flags
SKIP_CLEAN=false
SKIP_INSTALL=false
NO_BROWSER=false
DRY_RUN=false

# FUNÇÕES DE LOGGING
log_info() { echo -e "${BLUE}ℹ️  INFO${NC}: $*"; }
log_success() { echo -e "${GREEN}✅ SUCCESS${NC}: $*"; }
log_warning() { echo -e "${YELLOW}⚠️  WARNING${NC}: $*"; }
log_error() { echo -e "${RED}❌ ERROR${NC}: $*" >&2; }

log_step() {
    echo -e "\n${CYAN}═══════════════════════════════════════════════════════${NC}"
    echo -e "${CYAN}→ $*${NC}"
    echo -e "${CYAN}═══════════════════════════════════════════════════════${NC}\n"
}

print_header() {
    echo -e "${CYAN}"
    echo "╔════════════════════════════════════════════════════════════╗"
    echo "║                   CVfacil.NG STARTUP                       ║"
    echo "║        Inicializando com verificações automatizadas...    ║"
    echo "╚════════════════════════════════════════════════════════════╝"
    echo -e "${NC}"
}

# VALIDAÇÕES
check_prerequisites() {
    log_step "Verificando Pré-requisitos"

    if [[ ! -f "$PROJECT_ROOT/package.json" ]]; then
        log_error "package.json não encontrado"
        exit 1
    fi
    log_success "package.json encontrado"

    if ! command -v node &> /dev/null; then
        log_error "Node.js não está instalado"
        exit 1
    fi
    log_success "Node.js: $(node -v)"

    if ! command -v npm &> /dev/null; then
        log_error "npm não está instalado"
        exit 1
    fi
    log_success "npm: $(npm -v)"
}

# LIMPEZA
cleanup_environment() {
    [[ "$SKIP_CLEAN" == true ]] && return 0

    log_step "Limpando Ambiente"

    local -a items=(".next" ".turbo" "dist" "build" "coverage" "logs")
    for item in "${items[@]}"; do
        [[ -e "$PROJECT_ROOT/$item" ]] && rm -rf "$PROJECT_ROOT/$item" && log_info "Removido: $item"
    done

    npm cache clean --force > /dev/null 2>&1 || true
    log_success "Ambiente limpo"
}

# INSTALAÇÃO
install_dependencies() {
    [[ "$SKIP_INSTALL" == true ]] && return 0

    log_step "Instalando Dependências"

    if [[ -d "$NODE_MODULES" && -n "$(ls -A "$NODE_MODULES" 2>/dev/null)" ]]; then
        log_warning "node_modules já existe"
        return 0
    fi

    cd "$PROJECT_ROOT"
    if npm install --legacy-peer-deps; then
        log_success "Dependências instaladas"
    else
        log_error "Falha ao instalar dependências"
        exit 1
    fi
}

# BUILD
build_project() {
    log_step "Compilando Projeto"
    cd "$PROJECT_ROOT"
    if npm run build; then
        log_success "Projeto compilado"
    else
        log_error "Falha ao compilar"
        exit 1
    fi
}

# SERVIDOR
start_server() {
    log_step "Iniciando Servidor"

    mkdir -p "$LOG_DIR"
    cd "$PROJECT_ROOT"

    nohup npm run dev > "$LOG_DIR/server.log" 2>&1 &
    local server_pid=$!

    sleep 3
    if ! kill -0 $server_pid 2>/dev/null; then
        log_error "Falha ao iniciar servidor"
        cat "$LOG_DIR/server.log" | tail -20
        exit 1
    fi

    echo "$server_pid" > "$PROJECT_ROOT/.server.pid"
    log_success "Servidor iniciado (PID: $server_pid)"
}

# HEALTH CHECK
wait_for_server() {
    log_step "Aguardando Servidor"

    local max_attempts=30
    local attempt=1

    while [[ $attempt -le $max_attempts ]]; do
        if curl -s "$APP_URL" > /dev/null 2>&1; then
            log_success "Servidor respondendo"
            return 0
        fi
        printf "\rTentativa %d/%d..." "$attempt" "$max_attempts"
        attempt=$((attempt + 1))
        sleep 2
    done

    echo
    log_warning "Timeout, mas continuando..."
}

# NAVEGADOR
open_browser() {
    [[ "$NO_BROWSER" == true ]] && return 0

    log_step "Abrindo Navegador"

    if [[ "$OSTYPE" == "darwin"* ]]; then
        open "$APP_URL" &
    elif command -v xdg-open &> /dev/null; then
        xdg-open "$APP_URL" &
    else
        log_warning "Abra manualmente: $APP_URL"
        return
    fi

    sleep 1
    log_success "Navegador aberto"
}

# RESUMO
show_summary() {
    cat << EOF

${CYAN}╔════════════════════════════════════════════════════════════╗${NC}
${CYAN}║              ✅ INICIALIZAÇÃO COMPLETA                     ║${NC}
${CYAN}╚════════════════════════════════════════════════════════════╝${NC}

${GREEN}CVfacil.NG está rodando!${NC}

${CYAN}URL:${NC} $APP_URL
${CYAN}PID:${NC} $(cat "$PROJECT_ROOT/.server.pid" 2>/dev/null || echo "N/A")
${CYAN}Logs:${NC} $LOG_DIR/server.log

${YELLOW}Comandos Úteis:${NC}
  npm run dev    - Reinicia servidor
  npm test       - Executa testes
  npm run build  - Build para produção
  tail -f $LOG_DIR/server.log - Ver logs em tempo real

EOF
}

# PARSER
parse_arguments() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --skip-clean) SKIP_CLEAN=true ;;
            --skip-install) SKIP_INSTALL=true ;;
            --no-browser) NO_BROWSER=true ;;
            --dry-run) DRY_RUN=true; log_warning "DRY-RUN MODE" ;;
            -h|--help) echo "Uso: $0 [--skip-clean] [--skip-install] [--no-browser]"; exit 0 ;;
            *) log_error "Argumento desconhecido: $1"; exit 1 ;;
        esac
        shift
    done
}

# MAIN
main() {
    print_header
    parse_arguments "$@"
    check_prerequisites
    cleanup_environment
    install_dependencies
    build_project
    start_server
    wait_for_server
    open_browser
    show_summary
}

[[ "${BASH_SOURCE[0]}" == "${0}" ]] && main "$@"
