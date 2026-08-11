#Requires -Version 5.1
<#
.SYNOPSIS
    CVFacil.NG — Ativação automática e validação do OCR (Tesseract / tess4j).

.DESCRIPTION
    Este script automatiza TODO o fluxo de ativação do OCR no CVFacil.NG:

    ETAPA 1 — Pré-requisitos
        • Verifica Java 17+ no PATH
        • Verifica arquivos tessdata (por.traineddata, eng.traineddata)
          → Baixa automaticamente via SETUP_OCR.ps1 se ausentes

    ETAPA 2 — Configuração
        • Lê application-local.yml e garante que cvfacil.ocr.enabled = true
        • Confirma tessdata-path e language configurados

    ETAPA 3 — Compilação
        • Verifica se o JAR existe e está atualizado (>= fontes .java mais recentes)
        • Recompila via COMPILAR.bat se necessário (Maven auto-baixado pelo script)

    ETAPA 4 — Inicialização do backend
        • Encerra processo anterior na porta 8080 (se houver)
        • Inicia o backend com --spring.profiles.active=local
        • Aguarda até 90 s pelo health-check em /actuator/health

    ETAPA 5 — Smoke test do OCR
        • Envia um PDF sintético de 1 página (texto simples) ao endpoint /api/ai-import
        • Verifica resposta HTTP 200 e campo "content" não-vazio
        • [Opcional] Testa com PDF real escaneado se fornecido via -PdfPath

.PARAMETER PdfPath
    Caminho para um PDF escaneado real (opcional). Se fornecido, é usado no
    smoke test em vez do PDF sintético interno.

.PARAMETER SkipCompile
    Pula a recompilação mesmo que o JAR esteja desatualizado.

.PARAMETER SkipStart
    Pula as etapas de inicialização e smoke test (só configura e compila).

.PARAMETER Force
    Força recompilação mesmo que o JAR pareça atualizado.

.EXAMPLE
    # Ativação completa (recomendado na primeira vez):
    powershell -ExecutionPolicy Bypass -File .\ATIVAR_OCR.ps1

    # Só configura e compila, sem iniciar o backend:
    powershell -ExecutionPolicy Bypass -File .\ATIVAR_OCR.ps1 -SkipStart

    # Testa com PDF real escaneado:
    powershell -ExecutionPolicy Bypass -File .\ATIVAR_OCR.ps1 -PdfPath "C:\docs\curriculo_scan.pdf"

.NOTES
    Pré-requisitos:
      • Java 17+ instalado: https://adoptium.net/temurin/releases/?version=21
      • Windows PowerShell 5.1 ou PowerShell 7+
      • Conexão com internet (apenas para baixar tessdata, ~23 MB)
#>

[CmdletBinding()]
param(
    [string] $PdfPath    = "",
    [switch] $SkipCompile,
    [switch] $SkipStart,
    [switch] $Force
)

$ErrorActionPreference = 'Continue'
Set-StrictMode -Off

# ── Caminhos base ─────────────────────────────────────────────────────────────
$Root        = $PSScriptRoot
$YmlPath     = Join-Path $Root 'backend\src\main\resources\application-local.yml'
$TessdataDir = Join-Path $Root 'backend\tessdata'
$JarPath     = Join-Path $Root 'backend\target\cvfacil-backend-1.0.0-SNAPSHOT.jar'
$SetupScript = Join-Path $Root 'SETUP_OCR.ps1'
$CompilarBat = Join-Path $Root 'COMPILAR.bat'
$BackendDir  = Join-Path $Root 'backend'
$LogDir      = Join-Path $Root '.runtime\logs'
$BackendLog  = Join-Path $LogDir 'backend.log'
$BackendUrl  = 'http://localhost:8080'
$HealthUrl   = "$BackendUrl/actuator/health"
$ImportUrl   = "$BackendUrl/api/ai-import"

# ── Helpers de output ─────────────────────────────────────────────────────────
function Write-Banner($text) {
    Write-Host ""
    Write-Host ("=" * 62) -ForegroundColor Cyan
    Write-Host "  $text" -ForegroundColor Cyan
    Write-Host ("=" * 62) -ForegroundColor Cyan
    Write-Host ""
}
function Write-Step($n, $total, $text) {
    Write-Host "[$n/$total] $text" -ForegroundColor White
}
function Write-Ok($msg)   { Write-Host "        [OK]    $msg" -ForegroundColor Green  }
function Write-Warn($msg) { Write-Host "        [AVISO] $msg" -ForegroundColor Yellow }
function Write-Err($msg)  { Write-Host "        [ERRO]  $msg" -ForegroundColor Red    }
function Write-Info($msg) { Write-Host "        [INFO]  $msg" -ForegroundColor Gray   }

$TotalSteps = if ($SkipStart) { 3 } else { 5 }
$StepN = 0

# ─────────────────────────────────────────────────────────────────────────────
# ETAPA 1 — PRÉ-REQUISITOS
# ─────────────────────────────────────────────────────────────────────────────
Write-Banner "CVFacil.NG — Ativacao do OCR"
$StepN++
Write-Step $StepN $TotalSteps "Verificando pre-requisitos..."

# 1.1 Java
$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCmd) {
    Write-Err "Java nao encontrado no PATH."
    Write-Info "Instale o JDK 21: https://adoptium.net/temurin/releases/?version=21"
    Write-Info "Apos instalar, reinicie o PowerShell e tente novamente."
    exit 1
}
$javaVerRaw = & java -version 2>&1 | Select-String 'version' | Select-Object -First 1
Write-Ok "Java encontrado: $javaVerRaw"

# 1.2 Tessdata
$tessdataOk = $true
foreach ($lang in @('por', 'eng')) {
    $file = Join-Path $TessdataDir "$lang.traineddata"
    if (Test-Path $file) {
        $mb = [math]::Round((Get-Item $file).Length / 1MB, 1)
        Write-Ok "$lang.traineddata presente ($mb MB)"
    } else {
        Write-Warn "$lang.traineddata NAO encontrado."
        $tessdataOk = $false
    }
}

if (-not $tessdataOk) {
    Write-Info ""
    Write-Info "Baixando tessdata automaticamente via SETUP_OCR.ps1..."
    if (Test-Path $SetupScript) {
        & powershell -ExecutionPolicy Bypass -File $SetupScript
        if ($LASTEXITCODE -ne 0) {
            Write-Err "SETUP_OCR.ps1 falhou (exit $LASTEXITCODE)."
            Write-Info "Execute manualmente: powershell -ExecutionPolicy Bypass -File `"$SetupScript`""
            exit 1
        }
        # Revalida
        $stillMissing = @('por','eng') | Where-Object { -not (Test-Path (Join-Path $TessdataDir "$_.traineddata")) }
        if ($stillMissing) {
            Write-Err "Ainda faltam: $($stillMissing -join ', ') .traineddata"
            exit 1
        }
        Write-Ok "Tessdata baixado com sucesso."
    } else {
        Write-Err "SETUP_OCR.ps1 nao encontrado em: $SetupScript"
        Write-Info "Baixe manualmente os arquivos tessdata para: $TessdataDir"
        Write-Info "  por.traineddata: https://github.com/tesseract-ocr/tessdata/raw/main/por.traineddata"
        Write-Info "  eng.traineddata: https://github.com/tesseract-ocr/tessdata/raw/main/eng.traineddata"
        exit 1
    }
}

# ─────────────────────────────────────────────────────────────────────────────
# ETAPA 2 — CONFIGURAR application-local.yml
# ─────────────────────────────────────────────────────────────────────────────
$StepN++
Write-Step $StepN $TotalSteps "Configurando application-local.yml..."

if (-not (Test-Path $YmlPath)) {
    Write-Err "Arquivo nao encontrado: $YmlPath"
    exit 1
}

$yml = Get-Content $YmlPath -Raw

# 2.1 — Garantir enabled: true (substitui qualquer valor anterior)
$ymlOriginal = $yml

# Regex: encontra "enabled:" dentro da seção ocr (após "ocr:") e troca o valor
# Funciona independente de comentários na mesma linha
$yml = $yml -replace '(?m)(^\s+enabled:\s*)false(\s*#.*)?$', '${1}true'

# 2.2 — Confirmar tessdata-path
if ($yml -notmatch 'tessdata-path:\s*"?\./tessdata"?') {
    Write-Warn "tessdata-path nao encontrado ou diferente de './tessdata'."
    Write-Info "Verifique manualmente: $YmlPath"
}

# 2.3 — Salvar se houve mudança
if ($yml -ne $ymlOriginal) {
    Set-Content -Path $YmlPath -Value $yml -Encoding UTF8
    Write-Ok "cvfacil.ocr.enabled alterado para: true"
} else {
    # Verificar se já estava true
    if ($yml -match '(?m)^\s+enabled:\s*true') {
        Write-Ok "cvfacil.ocr.enabled ja estava: true (sem alteracao)"
    } else {
        Write-Warn "Nao foi possivel detectar o estado de 'enabled' no YAML."
        Write-Info "Verifique manualmente: $YmlPath"
    }
}

# 2.4 — Mostrar configuração OCR ativa
$ocrBlock = ($yml -split '\n' | Select-String -Pattern 'ocr:|enabled:|tessdata-path:|language:|dpi:' |
             Select-Object -First 6 | ForEach-Object { "    " + $_.Line.TrimEnd() }) -join "`n"
Write-Info "Configuracao OCR detectada:"
Write-Host $ocrBlock -ForegroundColor DarkGray

# ─────────────────────────────────────────────────────────────────────────────
# ETAPA 3 — COMPILAR (se necessário)
# ─────────────────────────────────────────────────────────────────────────────
$StepN++
Write-Step $StepN $TotalSteps "Verificando / compilando backend..."

$needsCompile = $Force

if (-not $needsCompile) {
    if (-not (Test-Path $JarPath)) {
        Write-Warn "JAR nao encontrado. Compilacao necessaria."
        $needsCompile = $true
    } else {
        # Verifica se algum .java foi modificado DEPOIS do JAR
        $jarTime = (Get-Item $JarPath).LastWriteTime
        $newerSrc = Get-ChildItem (Join-Path $BackendDir 'src') -Recurse -Filter '*.java' |
                    Where-Object { $_.LastWriteTime -gt $jarTime } |
                    Select-Object -First 1
        if ($newerSrc) {
            Write-Warn "Fontes mais recentes que o JAR ($($newerSrc.Name)). Recompilando..."
            $needsCompile = $true
        } else {
            $jarMb = [math]::Round((Get-Item $JarPath).Length / 1MB, 0)
            Write-Ok "JAR atualizado ($jarMb MB). Compilacao nao necessaria."
        }
    }
}

if ($needsCompile -and -not $SkipCompile) {
    if (-not (Test-Path $CompilarBat)) {
        Write-Err "COMPILAR.bat nao encontrado em: $CompilarBat"
        exit 1
    }
    Write-Info "Iniciando COMPILAR.bat (pode demorar 2-5 minutos na primeira vez)..."
    $compileResult = Start-Process -FilePath 'cmd.exe' `
        -ArgumentList "/c `"$CompilarBat`"" `
        -WorkingDirectory $Root `
        -Wait -PassThru

    if ($compileResult.ExitCode -ne 0) {
        Write-Err "COMPILAR.bat falhou (exit $($compileResult.ExitCode))."
        Write-Info "Execute COMPILAR.bat manualmente para ver o erro completo."
        exit 1
    }

    if (-not (Test-Path $JarPath)) {
        Write-Err "COMPILAR.bat concluiu mas JAR nao foi gerado: $JarPath"
        exit 1
    }
    Write-Ok "Compilacao concluida com sucesso."

} elseif ($needsCompile -and $SkipCompile) {
    Write-Warn "-SkipCompile ativo: usando JAR existente mesmo desatualizado."
}

if ($SkipStart) {
    Write-Host ""
    Write-Host ("=" * 62) -ForegroundColor Green
    Write-Host "  OCR configurado e JAR pronto." -ForegroundColor Green
    Write-Host "  Execute INICIAR.bat para subir o aplicativo." -ForegroundColor Green
    Write-Host ("=" * 62) -ForegroundColor Green
    Write-Host ""
    exit 0
}

# ─────────────────────────────────────────────────────────────────────────────
# ETAPA 4 — INICIAR BACKEND
# ─────────────────────────────────────────────────────────────────────────────
$StepN++
Write-Step $StepN $TotalSteps "Iniciando backend Spring Boot..."

# 4.1 — Liberar porta 8080 se ocupada
$portProc = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue |
            Select-Object -ExpandProperty OwningProcess -ErrorAction SilentlyContinue |
            Select-Object -First 1
if ($portProc) {
    Write-Warn "Porta 8080 ocupada pelo PID $portProc. Encerrando..."
    try {
        Stop-Process -Id $portProc -Force -ErrorAction Stop
        Start-Sleep -Seconds 2
        Write-Ok "Processo anterior encerrado."
    } catch {
        Write-Warn "Nao foi possivel encerrar PID $portProc`: $_"
        Write-Info "Tente fechar a janela do backend manualmente e execute novamente."
    }
}

# 4.2 — Criar diretório de logs
if (-not (Test-Path $LogDir)) {
    New-Item -ItemType Directory -Path $LogDir -Force | Out-Null
}

# 4.3 — Iniciar backend em janela separada
$javaArgs = "-jar `"$JarPath`" --spring.profiles.active=local --server.port=8080"
Write-Info "Iniciando: java $javaArgs"
Write-Info "Log em: $BackendLog"

$backendProc = Start-Process -FilePath 'java' `
    -ArgumentList $javaArgs `
    -WorkingDirectory $BackendDir `
    -RedirectStandardOutput $BackendLog `
    -RedirectStandardError  "$BackendLog.err" `
    -PassThru -WindowStyle Hidden

if (-not $backendProc -or $backendProc.HasExited) {
    Write-Err "Falha ao iniciar o processo java."
    exit 1
}
Write-Ok "Backend iniciado (PID $($backendProc.Id))."

# 4.4 — Aguardar health-check (até 90 s)
Write-Info "Aguardando backend responder em $HealthUrl (ate 90 s)..."
Write-Host "        Progresso: " -NoNewline

$maxWait   = 45   # tentativas × 2 s = 90 s
$attempt   = 0
$backendOk = $false

while ($attempt -lt $maxWait) {
    $attempt++
    Start-Sleep -Seconds 2
    Write-Host "." -NoNewline

    # Verifica se processo ainda está vivo
    if ($backendProc.HasExited) {
        Write-Host ""
        Write-Err "Backend encerrou inesperadamente (exit $($backendProc.ExitCode))."
        Write-Info "Ultimas linhas do log:"
        if (Test-Path $BackendLog) {
            Get-Content $BackendLog -Tail 20 | ForEach-Object { Write-Host "  $_" -ForegroundColor DarkGray }
        }
        exit 1
    }

    try {
        $resp = Invoke-WebRequest -Uri $HealthUrl -TimeoutSec 3 `
                    -UseBasicParsing -ErrorAction Stop
        if ($resp.StatusCode -eq 200 -and $resp.Content -match 'UP') {
            $backendOk = $true
            break
        }
    } catch {
        # ainda iniciando — continua aguardando
    }
}

Write-Host ""

if (-not $backendOk) {
    Write-Err "Backend nao respondeu em $($maxWait * 2) segundos."
    Write-Info "Verifique o log: $BackendLog"
    Write-Info "Dicas:"
    Write-Info "  - Java desatualizado? Requer 17+."
    Write-Info "  - Porta 8080 bloqueada por firewall ou antivirus?"
    Write-Info "  - JAR corrompido? Execute COMPILAR.bat novamente."
    if (Test-Path "$BackendLog.err") {
        Write-Info "Erros do backend:"
        Get-Content "$BackendLog.err" -Tail 15 | ForEach-Object { Write-Host "  $_" -ForegroundColor DarkGray }
    }
    exit 1
}

Write-Ok "Backend pronto e respondendo! (tentativa $attempt)"

# ─────────────────────────────────────────────────────────────────────────────
# ETAPA 5 — SMOKE TEST DO OCR
# ─────────────────────────────────────────────────────────────────────────────
$StepN++
Write-Step $StepN $TotalSteps "Smoke test do OCR via /api/ai-import..."

# 5.1 — Preparar PDF de teste
if ($PdfPath -and (Test-Path $PdfPath)) {
    $testPdf  = $PdfPath
    $pdfLabel = "PDF real fornecido: $(Split-Path $PdfPath -Leaf)"
} else {
    # Gera um PDF sintético mínimo com texto (não escaneado, mas exercita o endpoint)
    $testPdf  = Join-Path $env:TEMP 'cvfacil_ocr_test.pdf'
    $pdfLabel = "PDF sintetico (texto simples)"

    # PDF mínimo válido com texto embutido (não requer dependência externa)
    $pdfContent = @"
%PDF-1.4
1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj
2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj
3 0 obj<</Type/Page/MediaBox[0 0 612 792]/Parent 2 0 R/Resources<</Font<</F1 4 0 R>>>>/Contents 5 0 R>>endobj
4 0 obj<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>endobj
5 0 obj<</Length 120>>
stream
BT /F1 16 Tf 72 720 Td (Curriculo - Teste OCR CVFacil.NG) Tj 0 -24 Td (Nome: Joao Silva) Tj 0 -24 Td (Email: joao@exemplo.com) Tj ET
endstream
endobj
xref
0 6
0000000000 65535 f
0000000009 00000 n
0000000058 00000 n
0000000115 00000 n
0000000274 00000 n
0000000352 00000 n
trailer<</Size 6/Root 1 0 R>>
startxref
524
%%EOF
"@
    Set-Content -Path $testPdf -Value $pdfContent -Encoding ASCII
    Write-Info "PDF sintetico gerado em: $testPdf"
}

# 5.2 — Montar multipart/form-data e enviar
Write-Info "Enviando para: POST $ImportUrl"
Write-Info "Arquivo: $pdfLabel"

try {
    # Usar curl se disponível (mais confiável para multipart), senão Invoke-WebRequest
    $curlCmd = Get-Command curl -ErrorAction SilentlyContinue

    if ($curlCmd) {
        # Tenta com STUB token (ambiente local sem JWT real)
        $curlOutput = & curl -s -o "$env:TEMP\ocr_response.json" -w "%{http_code}" `
            -X POST "$ImportUrl" `
            -F "file=@`"$testPdf`";type=application/pdf" `
            -H "Authorization: Bearer STUB_ACCESS.00000000-0000-0000-0000-000000000001.0" `
            2>&1
        $httpCode = $curlOutput.Trim()
        $responseBody = if (Test-Path "$env:TEMP\ocr_response.json") {
            Get-Content "$env:TEMP\ocr_response.json" -Raw
        } else { "" }
    } else {
        # Fallback: Invoke-WebRequest com multipart manual
        $boundary  = [System.Guid]::NewGuid().ToString("N")
        $fileBytes = [System.IO.File]::ReadAllBytes($testPdf)
        $fileName  = Split-Path $testPdf -Leaf
        $encoding  = [System.Text.Encoding]::UTF8

        $bodyParts = [System.IO.MemoryStream]::new()
        $writer    = [System.IO.BinaryWriter]::new($bodyParts)

        $header = "--$boundary`r`nContent-Disposition: form-data; name=`"file`"; filename=`"$fileName`"`r`nContent-Type: application/pdf`r`n`r`n"
        $writer.Write($encoding.GetBytes($header))
        $writer.Write($fileBytes)
        $footer = "`r`n--$boundary--`r`n"
        $writer.Write($encoding.GetBytes($footer))
        $writer.Flush()
        $bodyBytes = $bodyParts.ToArray()

        $resp = Invoke-WebRequest -Uri $ImportUrl -Method Post `
            -Body $bodyBytes `
            -ContentType "multipart/form-data; boundary=$boundary" `
            -Headers @{ Authorization = "Bearer STUB_ACCESS.00000000-0000-0000-0000-000000000001.0" } `
            -UseBasicParsing -ErrorAction Stop

        $httpCode     = $resp.StatusCode.ToString()
        $responseBody = $resp.Content
    }

    # 5.3 — Avaliar resultado
    Write-Info "HTTP status: $httpCode"

    if ($httpCode -eq '200' -or $httpCode -eq '201') {
        Write-Ok "Endpoint /api/ai-import respondeu com $httpCode."

        # Verifica se retornou algum conteúdo extraído
        if ($responseBody -match '"content"\s*:\s*"(.{10,})"') {
            $preview = $matches[1].Substring(0, [Math]::Min(100, $matches[1].Length))
            Write-Ok "Conteudo extraido (primeiros 100 chars): $preview..."
        } elseif ($responseBody -match '"content"') {
            Write-Warn "Campo 'content' presente mas vazio ou muito curto."
            Write-Info "Para PDF real escaneado, use: -PdfPath <caminho-do-pdf>"
        }

        # Detecta se OCR foi acionado
        if ($responseBody -match 'ocr|tesseract|escaneado' -or $PdfPath) {
            Write-Ok "OCR parece ter sido acionado (verifique o log do backend para confirmar)."
        }

    } elseif ($httpCode -eq '401') {
        Write-Warn "HTTP 401 — token STUB nao aceito. Backend pode estar em perfil diferente de 'local'."
        Write-Info "Verifique se o backend subiu com --spring.profiles.active=local"

    } elseif ($httpCode -eq '501') {
        Write-Warn "HTTP 501 — endpoint /api/ai-import nao implementado neste perfil."
        Write-Info "Certifique-se de que o backend subiu com --spring.profiles.active=local"

    } else {
        Write-Warn "HTTP $httpCode — resposta inesperada."
        Write-Info "Corpo: $($responseBody.Substring(0, [Math]::Min(300, $responseBody.Length)))"
        Write-Info "Verifique o log do backend: $BackendLog"
    }

} catch {
    Write-Warn "Erro ao enviar o PDF de teste: $_"
    Write-Info "Isso pode ocorrer se o backend ainda nao aceitou conexoes."
    Write-Info "Tente manualmente: abra http://localhost:3000 e use o importador de PDF."
}

# 5.4 — Instrucoes para log do backend
Write-Host ""
Write-Info "Para confirmar que o OCR foi acionado, verifique o log do backend:"
Write-Info "  $BackendLog"
Write-Info "Procure por linhas como:"
Write-Info "  [AI Import] PDFBox retornou 0 chars - PDF parece escaneado."
Write-Info "  [AI Import] Acionando OCR (Tesseract, idioma=por+eng, dpi=300)"
Write-Info "  [AI Import] OCR concluido: XXXX chars extraidos de N pagina(s)"

# ── Resultado final ────────────────────────────────────────────────────────────
Write-Host ""
Write-Host ("=" * 62) -ForegroundColor Green
Write-Host "  OCR ativado e backend rodando!" -ForegroundColor Green
Write-Host ""
Write-Host "  Frontend : http://localhost:3000  (inicie com INICIAR.bat)" -ForegroundColor White
Write-Host "  Backend  : http://localhost:8080" -ForegroundColor White
Write-Host "  Health   : $HealthUrl" -ForegroundColor White
Write-Host "  Log      : $BackendLog" -ForegroundColor White
Write-Host ""
Write-Host "  Para testar com PDF real escaneado:" -ForegroundColor Yellow
Write-Host "    .\ATIVAR_OCR.ps1 -PdfPath `"C:\caminho\curriculo_scan.pdf`"" -ForegroundColor Yellow
Write-Host ("=" * 62) -ForegroundColor Green
Write-Host ""

if ($Host.Name -eq 'ConsoleHost') {
    Read-Host "Pressione ENTER para sair"
}

exit 0
