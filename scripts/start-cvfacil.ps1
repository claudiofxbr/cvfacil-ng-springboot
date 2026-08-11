# =============================================================================
# start-cvfacil.ps1
# -----------------------------------------------------------------------------
# Limpa caches, valida pre-requisitos, sobe o backend Spring Boot,
# aguarda o healthcheck e em seguida sobe o frontend Next.js.
#
# Uso:
#   powershell -ExecutionPolicy Bypass -File scripts\start-cvfacil.ps1
#   (ou duplo-clique em start-cvfacil.bat)
#
# Flags:
#   -SkipClean        Nao remove caches (.next, target, node_modules/.cache)
#   -SkipInstall      Nao executa npm ci mesmo se node_modules faltar
#   -NoBrowser        Nao abre o navegador ao final
#   -BackendPort      Porta do backend (default 8080)
#   -FrontendPort     Porta do frontend (default 3000)
# =============================================================================

[CmdletBinding()]
param(
    [switch]$SkipClean,
    [switch]$SkipInstall,
    [switch]$NoBrowser,
    [int]$BackendPort = 8080,
    [int]$FrontendPort = 3000,
    [int]$HealthTimeoutSec = 180
)

$ErrorActionPreference = "Stop"
$ScriptDir  = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot   = Split-Path -Parent $ScriptDir
$BackendDir = Join-Path $RepoRoot "backend"
$FrontendDir= Join-Path $RepoRoot "frontend"
$LogDir     = Join-Path $RepoRoot ".runtime\logs"
$PidDir     = Join-Path $RepoRoot ".runtime\pids"

# -----------------------------------------------------------------------------
# UI helpers
# -----------------------------------------------------------------------------
function Write-Section($title) {
    Write-Host ""
    Write-Host ("==> " + $title) -ForegroundColor Cyan
}
function Write-Ok($msg)   { Write-Host ("  [OK] " + $msg) -ForegroundColor Green }
function Write-Info($msg) { Write-Host ("  [..] " + $msg) -ForegroundColor DarkGray }
function Write-Warn($msg) { Write-Host ("  [!!] " + $msg) -ForegroundColor Yellow }
function Write-Err($msg)  { Write-Host ("  [XX] " + $msg) -ForegroundColor Red }

function Stop-ProcessIfAlive($proc) {
    if ($null -ne $proc -and -not $proc.HasExited) {
        try {
            Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
            Write-Info "Processo $($proc.Id) encerrado."
        } catch { }
    }
}

function Stop-ProcessOnPort($port) {
    # Encontra o PID do processo ouvindo na porta TCP informada e o encerra.
    try {
        $lines = & netstat -ano 2>$null | Where-Object { $_ -match "TCP\s+[^\s]+:$port\s+[^\s]+\s+LISTENING" }
        foreach ($line in $lines) {
            $pid_ = ($line -split '\s+') | Where-Object { $_ -match '^\d+$' } | Select-Object -Last 1
            if ($pid_ -and [int]$pid_ -gt 0) {
                Stop-Process -Id ([int]$pid_) -Force -ErrorAction SilentlyContinue
                Write-Info "Processo na porta $port (PID $pid_) encerrado."
            }
        }
    } catch { }
}

# -----------------------------------------------------------------------------
# Banner
# -----------------------------------------------------------------------------
Write-Host ""
Write-Host "  +---------------------------------------------+" -ForegroundColor Magenta
Write-Host "  |         CVFacil.NG - launcher local         |" -ForegroundColor Magenta
Write-Host "  +---------------------------------------------+" -ForegroundColor Magenta
Write-Host ("  Repo:      " + $RepoRoot)
Write-Host ("  Backend:   http://localhost:" + $BackendPort)
Write-Host ("  Frontend:  http://localhost:" + $FrontendPort)

# -----------------------------------------------------------------------------
# Pre-requisitos
# -----------------------------------------------------------------------------
Write-Section "Verificando pre-requisitos"

function Get-JavaMajor {
    $cmd = Get-Command java -ErrorAction SilentlyContinue
    if (-not $cmd) { return -1 }
    # java -version escreve em stderr; --version em stdout. Cobrimos os dois.
    try { $out = (& java -version 2>&1 | Out-String) } catch { $out = "" }
    if (-not $out) { try { $out = (& java --version 2>&1 | Out-String) } catch { $out = "" } }
    # Formato classico: openjdk version "21.0.1" ou java version "1.8.0_391"
    if ($out -match 'version "(\d+)(?:\.(\d+))?') {
        $m1 = [int]$Matches[1]
        if ($m1 -eq 1 -and $Matches[2]) { return [int]$Matches[2] }  # "1.8" -> 8
        return $m1
    }
    # Formato novo (Java 9+): "openjdk 21.0.1 2023-10-17"
    if ($out -match '\b(\d+)\.\d+\.\d+\b') { return [int]$Matches[1] }
    return 0
}

function Get-NodeMajor {
    $cmd = Get-Command node -ErrorAction SilentlyContinue
    if (-not $cmd) { return -1 }
    try { $out = (& node -v 2>&1 | Out-String).Trim() } catch { $out = "" }
    if ($out -match 'v(\d+)') { return [int]$Matches[1] }
    return 0
}

function Get-NpmMajor {
    $cmd = Get-Command npm -ErrorAction SilentlyContinue
    if (-not $cmd) { return -1 }
    try { $out = (& npm -v 2>&1 | Out-String).Trim() } catch { $out = "" }
    if ($out -match '^(\d+)') { return [int]$Matches[1] }
    return 0
}

function Assert-Major($name, $actual, $minMajor, $hint) {
    if ($actual -lt 0) {
        Write-Err "$name nao encontrado no PATH. Instale: $hint"
        exit 1
    }
    if ($actual -eq 0) {
        Write-Warn "$name encontrado mas versao ilegivel; seguindo."
        return
    }
    if ($actual -lt $minMajor) {
        Write-Err "$name versao $actual detectada, requer >= $minMajor."
        exit 1
    }
    Write-Ok "$name OK (major $actual)"
}

function Ensure-Java21 {
    $toolsDir = Join-Path $RepoRoot ".runtime\tools"

    # Tem JDK 21 local ja provisionado em .runtime/tools?
    $local = $null
    if (Test-Path $toolsDir) {
        $local = Get-ChildItem $toolsDir -Directory -Filter "jdk-21*" -ErrorAction SilentlyContinue |
                 Select-Object -First 1
    }

    if ($local) {
        $env:JAVA_HOME = $local.FullName
        $env:PATH = (Join-Path $local.FullName "bin") + ";" + $env:PATH
        $m = Get-JavaMajor
        if ($m -ge 21) {
            Write-Ok ("JDK 21 local: " + $local.FullName + " (major $m)")
            return
        }
        Write-Warn "JDK local em $($local.FullName) nao respondeu como 21; ignorando."
        $env:JAVA_HOME = ""
    }

    # Tenta o Java do sistema primeiro
    $sysMajor = Get-JavaMajor
    if ($sysMajor -ge 21) {
        Write-Ok "java OK (major $sysMajor)"
        return
    }

    if ($sysMajor -lt 0) {
        Write-Warn "Java nao encontrado no PATH; provisionando Temurin 21 local."
    } else {
        Write-Warn "Java $sysMajor detectado, CVFacil.NG requer 21. Provisionando Temurin 21 local."
    }

    # Download Adoptium Temurin 21 (Windows x64)
    New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null
    $zip = Join-Path $toolsDir "temurin21.zip"
    $url = "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse"
    Write-Info "Baixando Eclipse Temurin JDK 21 (~200 MB)..."
    try {
        $prev = $ProgressPreference; $ProgressPreference = 'SilentlyContinue'
        Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $zip
        $ProgressPreference = $prev
    } catch {
        Write-Err "Falha ao baixar JDK 21: $($_.Exception.Message)"
        Write-Err "Instale manualmente: https://adoptium.net/temurin/releases/?version=21"
        exit 1
    }
    Write-Info "Extraindo JDK..."
    try {
        Expand-Archive -Path $zip -DestinationPath $toolsDir -Force
        Remove-Item $zip -Force
    } catch {
        Write-Err "Falha ao extrair JDK: $($_.Exception.Message)"
        exit 1
    }

    $local = Get-ChildItem $toolsDir -Directory -Filter "jdk-21*" -ErrorAction SilentlyContinue |
             Select-Object -First 1
    if (-not $local) {
        Write-Err "Nao achei pasta jdk-21* em $toolsDir apos extracao."
        exit 1
    }
    $env:JAVA_HOME = $local.FullName
    $env:PATH = (Join-Path $local.FullName "bin") + ";" + $env:PATH
    $m = Get-JavaMajor
    if ($m -lt 21) {
        Write-Err "JDK 21 extraido mas 'java -version' ainda reporta major=$m."
        exit 1
    }
    Write-Ok ("JDK 21 instalado em " + $local.FullName + " (major $m)")
}

Ensure-Java21
Assert-Major "node" (Get-NodeMajor) 20 "Node.js 20.x LTS ou superior"
Assert-Major "npm"  (Get-NpmMajor)   9 "npm >= 9 (acompanha Node 20+)"

# -----------------------------------------------------------------------------
# Maven: wrapper -> global -> auto-download para .runtime/tools/
# -----------------------------------------------------------------------------
$MvnCmd = $null
$MvnwBat = Join-Path $BackendDir "mvnw.cmd"
if (Test-Path $MvnwBat) {
    $MvnCmd = $MvnwBat
    Write-Ok "Usando Maven wrapper (mvnw.cmd)"
} elseif (Get-Command mvn -ErrorAction SilentlyContinue) {
    $MvnCmd = (Get-Command mvn).Source
    Write-Ok "Usando Maven global (mvn)"
} else {
    $mvnVer = "3.9.6"
    $toolsDir = Join-Path $RepoRoot ".runtime\tools"
    $mvnRoot  = Join-Path $toolsDir  "apache-maven-$mvnVer"
    $mvnBin   = Join-Path $mvnRoot   "bin\mvn.cmd"
    if (-not (Test-Path $mvnBin)) {
        Write-Warn "Maven nao encontrado (mvnw.cmd ausente e mvn fora do PATH)."
        Write-Info "Baixando Apache Maven $mvnVer para $toolsDir ..."
        New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null
        $zip = Join-Path $toolsDir "maven.zip"
        $url = "https://archive.apache.org/dist/maven/maven-3/$mvnVer/binaries/apache-maven-$mvnVer-bin.zip"
        try {
            $prev = $ProgressPreference; $ProgressPreference = 'SilentlyContinue'
            Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $zip
            $ProgressPreference = $prev
            Expand-Archive -Path $zip -DestinationPath $toolsDir -Force
            Remove-Item $zip -Force
        } catch {
            Write-Err "Falha ao baixar Maven: $($_.Exception.Message)"
            Write-Err "Opcoes: (a) instalar Maven 3.9+ no PATH; (b) rodar 'mvn -N wrapper:wrapper' no backend."
            exit 1
        }
    }
    if (-not (Test-Path $mvnBin)) {
        Write-Err "Instalacao local de Maven falhou em $mvnRoot."
        exit 1
    }
    $MvnCmd = $mvnBin
    Write-Ok "Maven local: $mvnBin"
}

# -----------------------------------------------------------------------------
# Env files
# -----------------------------------------------------------------------------
Write-Section "Checando arquivos de ambiente"

function Ensure-EnvFile($target, $example) {
    if (Test-Path $target) {
        Write-Ok ("Encontrado: " + (Split-Path $target -Leaf))
        return
    }
    if (Test-Path $example) {
        Copy-Item $example $target
        Write-Warn ("Copiado de exemplo: " + (Split-Path $target -Leaf) + " (revise os valores!)")
    } else {
        Write-Warn ("Nenhum exemplo em " + (Split-Path $example -Leaf) + "; seguindo.")
    }
}

Ensure-EnvFile (Join-Path $BackendDir "src\main\resources\application-local.yml") `
               (Join-Path $BackendDir "src\main\resources\application-local.yml.example")
Ensure-EnvFile (Join-Path $FrontendDir ".env.local") (Join-Path $RepoRoot ".env.example")

# -----------------------------------------------------------------------------
# Limpeza de caches
# -----------------------------------------------------------------------------
if (-not $SkipClean) {
    Write-Section "Limpando caches"

    # Encerra processos que possam estar segurando o JAR antes de deletar/limpar
    Write-Info "Encerrando processos nas portas $BackendPort e $FrontendPort antes da limpeza..."
    Stop-ProcessOnPort $BackendPort
    Stop-ProcessOnPort $FrontendPort
    Start-Sleep -Seconds 2  # aguarda o SO liberar os file handles

    $nextDir    = Join-Path $FrontendDir ".next"
    $nodeCache  = Join-Path $FrontendDir "node_modules\.cache"
    $targetDir  = Join-Path $BackendDir "target"

    foreach ($p in @($nextDir, $nodeCache, $targetDir)) {
        if (Test-Path $p) {
            Write-Info ("Removendo " + $p)
            try {
                Remove-Item $p -Recurse -Force -ErrorAction Stop
                Write-Ok ("Removido " + (Split-Path $p -Leaf))
            } catch {
                Write-Warn ("Falha ao remover " + $p + ": " + $_.Exception.Message)
            }
        }
    }

    # mvn clean garante limpeza consistente (classes, generated-sources)
    Write-Info "Executando Maven clean..."
    Push-Location $BackendDir
    try {
        & $MvnCmd -q clean
        if ($LASTEXITCODE -ne 0) { throw "Maven clean falhou (exit $LASTEXITCODE)" }
        Write-Ok "Maven clean OK"
    } finally {
        Pop-Location
    }
} else {
    Write-Section "Limpeza ignorada (-SkipClean)"
}

# -----------------------------------------------------------------------------
# Dependencias do frontend
# -----------------------------------------------------------------------------
Write-Section "Dependencias do frontend"
$nmDir = Join-Path $FrontendDir "node_modules"
if ((-not (Test-Path $nmDir)) -and (-not $SkipInstall)) {
    Write-Info "node_modules ausente; executando npm ci..."
    Push-Location $FrontendDir
    try {
        if (Test-Path (Join-Path $FrontendDir "package-lock.json")) {
            & npm ci
        } else {
            & npm install
        }
        if ($LASTEXITCODE -ne 0) { throw "npm install falhou (exit $LASTEXITCODE)" }
        Write-Ok "Dependencias instaladas"
    } finally {
        Pop-Location
    }
} else {
    if ($SkipInstall) { Write-Info "npm ci ignorado (-SkipInstall)" }
    else              { Write-Ok "node_modules ja existe" }
}

# -----------------------------------------------------------------------------
# Liberando portas (mata processos remanescentes de execucoes anteriores)
# -----------------------------------------------------------------------------
Write-Section "Liberando portas"
Stop-ProcessOnPort $BackendPort
Stop-ProcessOnPort $FrontendPort
Start-Sleep -Seconds 1   # aguarda o SO liberar as portas
Write-Ok "Portas $BackendPort e $FrontendPort liberadas"

# -----------------------------------------------------------------------------
# Preparacao de diretorios de runtime
# -----------------------------------------------------------------------------
New-Item -ItemType Directory -Path $LogDir -Force | Out-Null
New-Item -ItemType Directory -Path $PidDir -Force | Out-Null
# Windows PowerShell 5.x nao permite -RedirectStandardOutput == -RedirectStandardError,
# entao mantemos logs separados e tambem gravamos um .log consolidado para display.
$BackendOutLog  = Join-Path $LogDir "backend.out.log"
$BackendErrLog  = Join-Path $LogDir "backend.err.log"
$FrontendOutLog = Join-Path $LogDir "frontend.out.log"
$FrontendErrLog = Join-Path $LogDir "frontend.err.log"
$BackendLog     = Join-Path $LogDir "backend.log"   # soft link "amigavel"
$FrontendLog    = Join-Path $LogDir "frontend.log"
Remove-Item $BackendOutLog, $BackendErrLog, $FrontendOutLog, $FrontendErrLog,
            $BackendLog, $FrontendLog -ErrorAction SilentlyContinue

# -----------------------------------------------------------------------------
# Backend — build JAR + run java -jar
# -----------------------------------------------------------------------------
# Rationale: 'mvn spring-boot:run' no Windows usa mvn.cmd (batch) que spawnea
# um JVM filho. O Start-Process do PowerShell marca o batch como encerrado
# assim que ele entrega o controle pro Java, mesmo o Java continuando vivo.
# Rodar o JAR diretamente (java.exe) evita esse reparenting.
Write-Section "Subindo backend (Spring Boot)"
$backendProc  = $null
$frontendProc = $null
try {
    # 1) Build do JAR fat (apenas classes + deps; sem testes).
    $targetDir = Join-Path $BackendDir "target"
    $jar = $null
    if (Test-Path $targetDir) {
        $jar = Get-ChildItem $targetDir -Filter "cvfacil-backend-*.jar" -ErrorAction SilentlyContinue |
               Where-Object { $_.Name -notlike "*-plain*" -and $_.Name -notlike "*-sources*" } |
               Sort-Object LastWriteTime -Descending | Select-Object -First 1
    }
    if (-not $jar) {
        Write-Info "Gerando jar do backend (mvn package -DskipTests)..."
        Push-Location $BackendDir
        try {
            & $MvnCmd -q "-DskipTests" "-Dspotless.check.skip=true" package 2>&1 |
                Tee-Object -FilePath (Join-Path $LogDir "backend.build.log") | Out-Null
            if ($LASTEXITCODE -ne 0) {
                throw "mvn package falhou (exit $LASTEXITCODE). Veja $LogDir\backend.build.log"
            }
        } finally { Pop-Location }
        $jar = Get-ChildItem $targetDir -Filter "cvfacil-backend-*.jar" |
               Where-Object { $_.Name -notlike "*-plain*" -and $_.Name -notlike "*-sources*" } |
               Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if (-not $jar) { throw "JAR nao encontrado em $targetDir apos build." }
        Write-Ok ("JAR criado: " + $jar.FullName)
    } else {
        Write-Info ("Reutilizando JAR: " + $jar.FullName)
    }

    # 2) Resolve java.exe (priorizando JDK 21 local que foi provisionado).
    $javaExe = $null
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
        $javaExe = Join-Path $env:JAVA_HOME "bin\java.exe"
    } else {
        $javaExe = (Get-Command java.exe -ErrorAction SilentlyContinue).Source
    }
    if (-not $javaExe) { throw "java.exe nao resolvido." }

    # 3) Launch direto do JVM (sem wrapper batch).
    $env:SPRING_PROFILES_ACTIVE = "local"
    $javaArgs = @(
        "-jar", $jar.FullName,
        "--spring.profiles.active=local",
        ("--server.port=" + $BackendPort)
    )
    Write-Info ("Comando: " + $javaExe + " " + ($javaArgs -join ' '))
    $backendProc = Start-Process -FilePath $javaExe `
                                 -ArgumentList $javaArgs `
                                 -WorkingDirectory $BackendDir `
                                 -RedirectStandardOutput $BackendOutLog `
                                 -RedirectStandardError  $BackendErrLog `
                                 -WindowStyle Hidden `
                                 -PassThru
    $backendProc.Id | Out-File (Join-Path $PidDir "backend.pid") -Encoding ascii
    Write-Ok ("Backend PID " + $backendProc.Id + " (logs: " + $BackendOutLog + " / " + $BackendErrLog + ")")

    # Poll do healthcheck
    $healthUrl = "http://localhost:$BackendPort/api/health"
    Write-Info ("Aguardando " + $healthUrl + " responder UP (ate " + $HealthTimeoutSec + "s)...")
    $start = Get-Date
    $ready = $false
    while (((Get-Date) - $start).TotalSeconds -lt $HealthTimeoutSec) {
        if ($backendProc.HasExited) {
            throw ("Backend encerrou sozinho (exit " + $backendProc.ExitCode + "). Verifique " + $BackendOutLog + " e " + $BackendErrLog)
        }
        try {
            $resp = Invoke-WebRequest -Uri $healthUrl -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
            if ($resp.StatusCode -eq 200 -and ($resp.Content -match '"UP"')) {
                $ready = $true
                break
            }
        } catch { }
        Start-Sleep -Seconds 2
        Write-Host "." -NoNewline -ForegroundColor DarkGray
    }
    Write-Host ""
    if (-not $ready) {
        throw "Backend nao respondeu UP dentro do timeout. Veja $BackendOutLog e $BackendErrLog"
    }
    Write-Ok "Backend healthcheck UP"

    # -------------------------------------------------------------------------
    # Frontend
    # -------------------------------------------------------------------------
    Write-Section "Subindo frontend (Next.js)"
    $env:PORT = "$FrontendPort"
    # npm no Windows e na verdade npm.cmd; resolver para evitar 'spawn npm ENOENT'
    $NpmExe = (Get-Command npm.cmd -ErrorAction SilentlyContinue).Source
    if (-not $NpmExe) { $NpmExe = (Get-Command npm -ErrorAction SilentlyContinue).Source }
    if (-not $NpmExe) { throw "npm nao encontrado no PATH para subir o frontend." }
    $frontendProc = Start-Process -FilePath $NpmExe `
                                  -ArgumentList @("run","dev","--","-p","$FrontendPort") `
                                  -WorkingDirectory $FrontendDir `
                                  -RedirectStandardOutput $FrontendOutLog `
                                  -RedirectStandardError  $FrontendErrLog `
                                  -WindowStyle Hidden `
                                  -PassThru
    $frontendProc.Id | Out-File (Join-Path $PidDir "frontend.pid") -Encoding ascii
    Write-Ok ("Frontend PID " + $frontendProc.Id + " (logs: " + $FrontendOutLog + " / " + $FrontendErrLog + ")")

    # Poll do home
    $homeUrl = "http://localhost:$FrontendPort/"
    Write-Info ("Aguardando Next em " + $homeUrl + "...")
    $start = Get-Date
    $ready = $false
    while (((Get-Date) - $start).TotalSeconds -lt 120) {
        if ($frontendProc.HasExited) {
            throw ("Frontend encerrou sozinho (exit " + $frontendProc.ExitCode + "). Veja " + $FrontendOutLog + " e " + $FrontendErrLog)
        }
        try {
            $resp = Invoke-WebRequest -Uri $homeUrl -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
            if ($resp.StatusCode -ge 200 -and $resp.StatusCode -lt 500) { $ready = $true; break }
        } catch { }
        Start-Sleep -Seconds 2
        Write-Host "." -NoNewline -ForegroundColor DarkGray
    }
    Write-Host ""
    if (-not $ready) {
        throw "Frontend nao respondeu. Veja $FrontendOutLog e $FrontendErrLog"
    }
    Write-Ok "Frontend respondendo"

    # -------------------------------------------------------------------------
    # Sucesso
    # -------------------------------------------------------------------------
    Write-Host ""
    Write-Host "  +---------------------------------------------+" -ForegroundColor Green
    Write-Host "  |   CVFacil.NG operacional - pressione Ctrl+C |" -ForegroundColor Green
    Write-Host "  |   para encerrar backend e frontend juntos.  |" -ForegroundColor Green
    Write-Host "  +---------------------------------------------+" -ForegroundColor Green
    Write-Host ""
    Write-Host ("  Frontend: http://localhost:" + $FrontendPort) -ForegroundColor White
    Write-Host ("  Backend:  http://localhost:" + $BackendPort + "/api/health") -ForegroundColor White
    Write-Host ("  Logs:     " + $LogDir) -ForegroundColor DarkGray
    Write-Host ""

    if (-not $NoBrowser) {
        Start-Process ("http://localhost:" + $FrontendPort) | Out-Null
    }

    # Loop ate um dos dois morrer ou o usuario enviar Ctrl+C
    while (-not $backendProc.HasExited -and -not $frontendProc.HasExited) {
        Start-Sleep -Seconds 2
    }
    if ($backendProc.HasExited)  { Write-Warn ("Backend encerrou (exit "  + $backendProc.ExitCode  + ")") }
    if ($frontendProc.HasExited) { Write-Warn ("Frontend encerrou (exit " + $frontendProc.ExitCode + ")") }
}
catch {
    Write-Err $_.Exception.Message
    exit 1
}
finally {
    Write-Section "Encerrando processos"
    Stop-ProcessIfAlive $frontendProc
    Stop-ProcessIfAlive $backendProc
    # Best-effort: mata qualquer processo remanescente nas portas usadas
    Stop-ProcessOnPort $BackendPort
    Stop-ProcessOnPort $FrontendPort
    Write-Ok "Bye."
}
