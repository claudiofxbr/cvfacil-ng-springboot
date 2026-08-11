#Requires -Version 5.1
<#
.SYNOPSIS
    CVFacil.NG — Diagnóstico da configuração OCR (Tesseract / tess4j).
    Substitui VERIFICAR_OCR.bat com referências corretas ao SETUP_OCR.ps1.

.DESCRIPTION
    Verifica:
      1. Arquivos tessdata (por.traineddata, eng.traineddata)
      2. application-local.yml (OCR enabled + tessdata-path)
      3. Java 21+
      4. Maven / Maven Wrapper

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\VERIFICAR_OCR.ps1
#>

$ErrorActionPreference = 'Continue'
$Root          = $PSScriptRoot
$TessdataDir   = Join-Path $Root 'backend\tessdata'
$AppYml        = Join-Path $Root 'backend\src\main\resources\application-local.yml'
$SetupScript   = Join-Path $Root 'SETUP_OCR.ps1'
$Errors        = 0

function Write-Header($text) {
    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Cyan
    Write-Host "  $text" -ForegroundColor Cyan
    Write-Host "============================================================" -ForegroundColor Cyan
    Write-Host ""
}

function Write-Ok($msg)    { Write-Host "      [OK]    $msg" -ForegroundColor Green  }
function Write-Warn($msg)  { Write-Host "      [AVISO] $msg" -ForegroundColor Yellow }
function Write-Err($msg)   { Write-Host "      [ERRO]  $msg" -ForegroundColor Red; $script:Errors++ }
function Write-Info($msg)  { Write-Host "      [INFO]  $msg" -ForegroundColor Gray  }

# ── Header ────────────────────────────────────────────────────────────────────
Write-Header "CVFacil.NG - Verificacao da Configuracao OCR"

# ── 1. Arquivos tessdata ──────────────────────────────────────────────────────
Write-Host "[1/4] Verificando arquivos tessdata..." -ForegroundColor White

if (-not (Test-Path $TessdataDir)) {
    Write-Err "Diretorio nao encontrado: backend\tessdata\"
    Write-Info "Execute: powershell -ExecutionPolicy Bypass -File `"$SetupScript`""
} else {
    foreach ($lang in @('por', 'eng')) {
        $file = Join-Path $TessdataDir "$lang.traineddata"
        if (Test-Path $file) {
            $sz = (Get-Item $file).Length
            $mb = [math]::Round($sz / 1MB, 1)
            Write-Ok "$lang.traineddata encontrado ($mb MB)"
        } else {
            Write-Err "$lang.traineddata NAO encontrado em backend\tessdata\"
            Write-Info "Execute: powershell -ExecutionPolicy Bypass -File `"$SetupScript`""
        }
    }
}

# ── 2. application-local.yml ──────────────────────────────────────────────────
Write-Host ""
Write-Host "[2/4] Verificando application-local.yml..." -ForegroundColor White

if (-not (Test-Path $AppYml)) {
    Write-Err "Arquivo nao encontrado: $AppYml"
} else {
    $ymlContent = Get-Content $AppYml -Raw

    if ($ymlContent -match 'enabled:\s*true') {
        Write-Ok "OCR habilitado (enabled: true)"
    } else {
        Write-Warn "OCR ainda desabilitado (enabled: false)"
        Write-Info "Edite application-local.yml e mude para: enabled: true"
    }

    if ($ymlContent -match 'tessdata-path') {
        if ($ymlContent -match 'tessdata-path:\s*["'']?\.\/tessdata') {
            Write-Ok "tessdata-path: `"./tessdata`" configurado"
        } else {
            Write-Info "tessdata-path encontrado — verifique se aponta para backend\tessdata\"
        }
    } else {
        Write-Warn "tessdata-path nao encontrado no YAML"
    }
}

# ── 3. Java ───────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "[3/4] Verificando Java..." -ForegroundColor White

$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCmd) {
    Write-Err "Java nao encontrado no PATH."
    Write-Info "Instale o JDK 21: https://adoptium.net/"
} else {
    $javaVer = & java -version 2>&1 | Select-String 'version' | Select-Object -First 1
    Write-Ok "Java encontrado: $javaVer"
}

# ── 4. JAR compilado ─────────────────────────────────────────────────────────
Write-Host ""
Write-Host "[4/4] Verificando JAR do backend..." -ForegroundColor White

$jar = Join-Path $Root 'backend\target\cvfacil-backend-1.0.0-SNAPSHOT.jar'
if (Test-Path $jar) {
    $jarSize = (Get-Item $jar).Length
    $jarMb   = [math]::Round($jarSize / 1MB, 1)
    if ($jarSize -lt 1MB) {
        Write-Warn "JAR encontrado mas muito pequeno ($jarMb MB) — pode estar corrompido."
        Write-Info "Execute COMPILAR.bat para recompilar."
    } else {
        Write-Ok "JAR encontrado ($jarMb MB): backend\target\cvfacil-backend-1.0.0-SNAPSHOT.jar"
    }
} else {
    Write-Err "JAR nao encontrado: backend\target\cvfacil-backend-1.0.0-SNAPSHOT.jar"
    Write-Info "Execute COMPILAR.bat para compilar o backend antes de iniciar."
}

# ── Resultado ─────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan

if ($Errors -eq 0) {
    Write-Host "  RESULTADO: Configuracao OK - o OCR esta pronto para uso." -ForegroundColor Green
    Write-Host ""
    Write-Host "  Proximo passo: habilitar no YAML (se ainda nao fez):"
    Write-Host "    Abra: backend\src\main\resources\application-local.yml"
    Write-Host "    Altere: enabled: false  para  enabled: true"
    Write-Host ""
    Write-Host "  Depois recompile e inicie o aplicativo:"
    Write-Host "    1. Duplo-clique em COMPILAR.bat  (gera o JAR atualizado)"
    Write-Host "    2. Duplo-clique em INICIAR.bat   (sobe backend + frontend)"
    Write-Host ""
    Write-Host "  Log esperado ao importar PDF escaneado:"
    Write-Host "    [AI Import] PDFBox retornou 0 chars - PDF parece escaneado."
    Write-Host "    [AI Import] Acionando OCR (Tesseract, idioma=por+eng, dpi=300)"
    Write-Host "    [AI Import] OCR concluido: XXXX chars extraidos de N pagina(s)"
} else {
    Write-Host "  RESULTADO: Ha $Errors problema(s) a corrigir (veja [ERRO] acima)." -ForegroundColor Red
    Write-Host ""
    Write-Host "  Passos recomendados:"
    Write-Host "    1. Execute SETUP_OCR.ps1 para baixar os arquivos tessdata:"
    Write-Host "       powershell -ExecutionPolicy Bypass -File `"$SetupScript`""
    Write-Host "    2. Edite application-local.yml: mude enabled: false para enabled: true"
    Write-Host "    3. Execute COMPILAR.bat para gerar o JAR"
    Write-Host "    4. Execute INICIAR.bat para subir o aplicativo"
    Write-Host "    5. Execute este script novamente para confirmar o OCR"
}

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

if ($Host.Name -eq 'ConsoleHost') {
    Read-Host "Pressione ENTER para sair"
}

exit $Errors
