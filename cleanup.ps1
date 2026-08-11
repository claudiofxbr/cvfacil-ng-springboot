# CVfacil.NG - Cleanup Script (PowerShell/Windows)
# Limpa todos os arquivos temporários, caches e processos

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

function Write-Info { param([string]$msg) Write-Host "ℹ️  INFO: $msg" -ForegroundColor Cyan }
function Write-Success { param([string]$msg) Write-Host "✅ SUCCESS: $msg" -ForegroundColor Green }
function Write-Warning { param([string]$msg) Write-Host "⚠️  WARNING: $msg" -ForegroundColor Yellow }

Write-Host @"
╔════════════════════════════════════════════════════════════╗
║             CVfacil.NG CLEANUP                             ║
║        Limpando arquivos temporários e caches...         ║
╚════════════════════════════════════════════════════════════╝
"@ -ForegroundColor Cyan

# Parar servidor
$pidFile = "$projectRoot\.server.pid"
if (Test-Path $pidFile) {
    $pid = Get-Content $pidFile
    try {
        if (Get-Process -Id $pid -ErrorAction SilentlyContinue) {
            Write-Info "Parando servidor (PID: $pid)..."
            Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue
            Remove-Item $pidFile
            Write-Success "Servidor parado"
        }
    } catch { }
}

# Limpeza de diretórios
$dirs = @(".next", ".turbo", "dist", "build", "coverage", "logs", ".webpack")
foreach ($dir in $dirs) {
    $path = Join-Path $projectRoot $dir
    if (Test-Path $path) {
        Write-Info "Removendo: $dir"
        Remove-Item -Path $path -Recurse -Force -ErrorAction SilentlyContinue
    }
}

# Limpeza de arquivos
if (Test-Path "$projectRoot\.server.pid") {
    Remove-Item "$projectRoot\.server.pid" -Force
}

Get-ChildItem $projectRoot -Filter "*.log" -Recurse | Remove-Item -Force -ErrorAction SilentlyContinue

# Limpar cache npm
Write-Info "Limpando cache npm..."
npm cache clean --force 2>&1 | Out-Null

Write-Host ""
Write-Success "Limpeza completa!"
Write-Host @"

Resumo:
  ✓ Servidor parado
  ✓ Diretórios de build removidos
  ✓ Logs removidos
  ✓ Cache npm limpo

Para reiniciar: .\start.ps1
"@
