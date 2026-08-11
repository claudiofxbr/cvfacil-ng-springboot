# ============================================================
#   CVFacil.NG - Setup OCR (Tesseract tessdata) v3.0
# ============================================================
# Baixa por.traineddata e eng.traineddata para backend\tessdata\.
# Uso: Clique direito -> "Executar com PowerShell"
#   ou: powershell -ExecutionPolicy Bypass -File SETUP_OCR.ps1
# ============================================================

$ErrorActionPreference = 'Stop'
$ProgressPreference    = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$root       = Split-Path $MyInvocation.MyCommand.Path
$tessDir    = Join-Path $root "backend\tessdata"
$appYml     = Join-Path $root "backend\src\main\resources\application-local.yml"

Write-Host ""
Write-Host "============================================================"
Write-Host "  CVFacil.NG - Setup OCR (Tesseract tessdata)"
Write-Host "============================================================"
Write-Host ""

# Cria diretorio se nao existir
if (-not (Test-Path $tessDir)) {
    New-Item -ItemType Directory -Path $tessDir | Out-Null
    Write-Host "[OK] Diretorio criado: backend\tessdata\"
}

# Funcao de download com fallback de URLs
function Download-Tessdata($name, $urls, $dest) {
    if (Test-Path $dest) {
        $sz = (Get-Item $dest).Length
        if ($sz -gt 1000000) {
            Write-Host "[OK] $name ja existe ($sz bytes)"
            return $true
        }
        Write-Host "[INFO] $name incompleto ($sz bytes) - baixando novamente"
        Remove-Item $dest -Force
    }
    Write-Host "Baixando $name..."
    foreach ($url in $urls) {
        try {
            Write-Host "  Fonte: $url"
            Invoke-WebRequest -Uri $url -OutFile $dest -UseBasicParsing -TimeoutSec 180
            $sz = (Get-Item $dest).Length
            if ($sz -lt 100000) { throw "Arquivo incompleto ($sz bytes)" }
            Write-Host "[OK] $name baixado ($sz bytes)"
            return $true
        } catch {
            Write-Host "  [AVISO] Falha: $_"
            if (Test-Path $dest) { Remove-Item $dest -Force }
        }
    }
    return $false
}

$urls_por = @(
    "https://github.com/tesseract-ocr/tessdata_fast/raw/main/por.traineddata",
    "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/por.traineddata"
)
$urls_eng = @(
    "https://github.com/tesseract-ocr/tessdata_fast/raw/main/eng.traineddata",
    "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/eng.traineddata"
)

$ok = $true
if (-not (Download-Tessdata "por.traineddata" $urls_por "$tessDir\por.traineddata")) {
    Write-Host "[ERRO] Nao foi possivel baixar por.traineddata"
    $ok = $false
}
if (-not (Download-Tessdata "eng.traineddata" $urls_eng "$tessDir\eng.traineddata")) {
    Write-Host "[ERRO] Nao foi possivel baixar eng.traineddata"
    $ok = $false
}

if (-not $ok) {
    Write-Host ""
    Write-Host "========================================================"
    Write-Host "  DOWNLOAD MANUAL NECESSARIO"
    Write-Host "========================================================"
    Write-Host ""
    Write-Host "Acesse e baixe os dois arquivos:"
    Write-Host "  https://github.com/tesseract-ocr/tessdata_fast/tree/main"
    Write-Host ""
    Write-Host "Copie para:"
    Write-Host "  $tessDir\"
    Write-Host ""
    Read-Host "Pressione ENTER para sair"
    exit 1
}

Write-Host ""
Write-Host "[OK] Arquivos tessdata prontos."
Write-Host ""

# Verifica se OCR ja esta habilitado
$ocrHabilitado = $false
if (Test-Path $appYml) {
    $content = Get-Content $appYml -Raw
    if ($content -match "enabled:\s*true") {
        $ocrHabilitado = $true
        Write-Host "[OK] OCR ja habilitado em application-local.yml."
        Write-Host "     Apenas reinicie o backend para ativar."
    }
}

if (-not $ocrHabilitado) {
    Write-Host "============================================================"
    Write-Host "  PROXIMO PASSO: Habilitar OCR em application-local.yml"
    Write-Host "============================================================"
    Write-Host ""
    Write-Host "Abra: backend\src\main\resources\application-local.yml"
    Write-Host "Altere:  enabled: false"
    Write-Host "Para:    enabled: true"
    Write-Host ""
    $resp = Read-Host "Abrir o arquivo agora no Notepad? (S/N)"
    if ($resp -ieq "S") {
        Start-Process notepad $appYml
    }
}

Write-Host ""
Write-Host "[OK] Setup concluido! Execute VERIFICAR_OCR.bat para confirmar."
Write-Host ""
Read-Host "Pressione ENTER para sair"
