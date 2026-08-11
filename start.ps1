# CVfacil.NG - Startup Script (PowerShell/Windows)
# Usage: .\start.ps1 [-SkipClean] [-SkipInstall] [-NoBrowser]

param(
    [switch]$SkipClean = $false,
    [switch]$SkipInstall = $false,
    [switch]$NoBrowser = $false
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

# Configurações
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = $scriptDir
$nodeModules = Join-Path $projectRoot "node_modules"
$logDir = Join-Path $projectRoot "logs"
$appPort = 3000
$appUrl = "http://localhost:$appPort"

# FUNÇÕES DE LOGGING
function Write-Info { param([string]$msg) Write-Host "ℹ️  INFO: $msg" -ForegroundColor Cyan }
function Write-Success { param([string]$msg) Write-Host "✅ SUCCESS: $msg" -ForegroundColor Green }
function Write-Warning { param([string]$msg) Write-Host "⚠️  WARNING: $msg" -ForegroundColor Yellow }
function Write-Error { param([string]$msg) Write-Host "❌ ERROR: $msg" -ForegroundColor Red }

function Write-Step {
    param([string]$msg)
    Write-Host "`n$('=' * 60)"
    Write-Host "→ $msg"
    Write-Host "$('=' * 60)`n" -ForegroundColor Cyan
}

function Print-Header {
    Write-Host @"
╔════════════════════════════════════════════════════════════╗
║                  CVfacil.NG STARTUP                        ║
║       Inicializando com verificações automatizadas...     ║
╚════════════════════════════════════════════════════════════╝
"@ -ForegroundColor Cyan
}

# VALIDAÇÕES
function Check-Prerequisites {
    Write-Step "Verificando Pré-requisitos"

    if (-not (Test-Path "$projectRoot\package.json")) {
        Write-Error "package.json não encontrado"
        exit 1
    }
    Write-Success "package.json encontrado"

    if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
        Write-Error "Node.js não está instalado - https://nodejs.org/"
        exit 1
    }
    Write-Success "Node.js: $(node -v)"

    if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
        Write-Error "npm não está instalado"
        exit 1
    }
    Write-Success "npm: $(npm -v)"
}

# LIMPEZA
function Cleanup-Environment {
    if ($SkipClean) {
        Write-Warning "Limpeza pulada"
        return
    }

    Write-Step "Limpando Ambiente"

    $items = @(".next", ".turbo", "dist", "build", "coverage", "logs")
    foreach ($item in $items) {
        $path = Join-Path $projectRoot $item
        if (Test-Path $path) {
            Write-Info "Removendo: $item"
            Remove-Item -Path $path -Recurse -Force -ErrorAction SilentlyContinue | Out-Null
        }
    }

    npm cache clean --force 2>&1 | Out-Null
    Write-Success "Ambiente limpo"
}

# INSTALAÇÃO
function Install-Dependencies {
    if ($SkipInstall) {
        Write-Warning "Instalação pulada"
        return
    }

    Write-Step "Instalando Dependências"

    if ((Test-Path $nodeModules) -and ((Get-ChildItem $nodeModules -ErrorAction SilentlyContinue | Measure-Object).Count -gt 0)) {
        Write-Warning "node_modules já existe"
        return
    }

    Push-Location $projectRoot
    npm install --legacy-peer-deps
    Write-Success "Dependências instaladas"
    Pop-Location
}

# BUILD
function Build-Project {
    Write-Step "Compilando Projeto"

    Push-Location $projectRoot
    npm run build
    Write-Success "Projeto compilado"
    Pop-Location
}

# SERVIDOR
function Start-Server {
    Write-Step "Iniciando Servidor"

    if (-not (Test-Path $logDir)) {
        New-Item -ItemType Directory -Path $logDir -Force | Out-Null
    }

    $logFile = Join-Path $logDir "server.log"

    Push-Location $projectRoot
    $process = Start-Process -FilePath "npm" `
        -ArgumentList "run dev" `
        -RedirectStandardOutput $logFile `
        -RedirectStandardError "$logDir\error.log" `
        -PassThru `
        -NoNewWindow

    Start-Sleep -Seconds 3

    if ($null -eq $process -or $process.HasExited) {
        Write-Error "Falha ao iniciar servidor"
        Get-Content $logFile -Tail 20 | Write-Host
        exit 1
    }

    $process.Id | Out-File -FilePath "$projectRoot\.server.pid" -NoNewline
    Write-Success "Servidor iniciado (PID: $($process.Id))"
    Pop-Location
}

# HEALTH CHECK
function Wait-ForServer {
    Write-Step "Aguardando Servidor"

    for ($i = 1; $i -le 30; $i++) {
        try {
            $response = Invoke-WebRequest -Uri $appUrl -UseBasicParsing -TimeoutSec 2 -ErrorAction SilentlyContinue
            if ($response.StatusCode -eq 200) {
                Write-Success "Servidor respondendo"
                return
            }
        } catch { }

        Write-Host -NoNewline "`rTentativa $i/30..."
        Start-Sleep -Seconds 2
    }

    Write-Host ""
    Write-Warning "Timeout, mas continuando..."
}

# NAVEGADOR
function Open-Browser {
    if ($NoBrowser) {
        Write-Warning "Navegador desabilitado"
        return
    }

    Write-Step "Abrindo Navegador"

    try {
        Write-Info "Abrindo $appUrl..."
        Start-Process $appUrl
        Start-Sleep -Seconds 1
        Write-Success "Navegador aberto"
    } catch {
        Write-Warning "Abra manualmente: $appUrl"
    }
}

# RESUMO
function Show-Summary {
    $pidFile = "$projectRoot\.server.pid"
    $pid = if (Test-Path $pidFile) { Get-Content $pidFile } else { "N/A" }

    Write-Host @"

╔════════════════════════════════════════════════════════════╗
║             ✅ INICIALIZAÇÃO COMPLETA                      ║
╚════════════════════════════════════════════════════════════╝

CVfacil.NG está rodando!

Informações:
  URL:       $appUrl
  PID:       $pid
  Node.js:   $(node -v)
  npm:       $(npm -v)
  Logs:      $logDir\server.log

Comandos Úteis:
  npm run dev      - Reinicia servidor
  npm test         - Executa testes
  npm run build    - Build para produção

"@ -ForegroundColor Green
}

# MAIN
Print-Header

try {
    Check-Prerequisites
    Cleanup-Environment
    Install-Dependencies
    Build-Project
    Start-Server
    Wait-ForServer
    Open-Browser
    Show-Summary
} catch {
    Write-Error "Erro: $_"
    exit 1
}
