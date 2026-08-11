#requires -version 7.0
<#
.SYNOPSIS
    Pipeline completo CVFacil.NG: push GitHub -> build/push da imagem (GitHub Actions)
    -> redeploy na VPS Hostinger (Docker Swarm / EasyPanel) -> abre o app no navegador.

.DESCRIPTION
    Automatiza tudo que HOJE é automatizável na arquitetura real do projeto:
      1) commit + push para o GitHub (dispara o workflow "Build e publica imagem Docker")
      2) acompanha o workflow até build+push da imagem terminarem no GHCR
      3) via SSH, força o serviço Docker Swarm do app a puxar a imagem nova
      4) espera o novo container ficar saudável
      5) valida DNS/HTTP e abre https://cvfacil.ng no navegador

    NÃO tenta criar o app no EasyPanel nem configurar DNS — isso hoje só existe
    via UI (não há API do EasyPanel disponível/confirmada neste ambiente).
    Se esses pré-requisitos não estiverem prontos, o script para com uma
    mensagem clara em vez de fingir que funcionou.

.PARAMETER CommitMessage
    Mensagem do commit. Se omitida e não houver alterações pendentes, pula o commit.

.PARAMETER VpsHost
    IP ou hostname da VPS Hostinger. Default: 69.62.87.38

.PARAMETER VpsUser
    Usuário SSH. Default: root

.PARAMETER SshKeyPath
    Caminho da chave privada SSH. Default: ~/.ssh/cvfacil_deploy_key

.PARAMETER SwarmServiceName
    Nome exato do serviço Docker Swarm criado pelo EasyPanel para o app.
    Descubra com: ssh <vps> "docker service ls --filter name=cvfacil"
    Se omitido, o script tenta detectar automaticamente (falha com erro claro
    se encontrar 0 ou mais de 1 candidato).

.PARAMETER GhcrUser
    Usuário do GitHub Container Registry (para login na VPS antes do pull).

.PARAMETER GhcrToken
    Personal Access Token com escopo read:packages. NUNCA hardcode este valor
    no script — passe via parâmetro ou variável de ambiente $env:GHCR_TOKEN.

.PARAMETER AppUrl
    URL pública final do app. Default: https://cvfacil.ng

.EXAMPLE
    $env:GHCR_TOKEN = "ghp_xxx"
    ./scripts/deploy-full-auto.ps1 -CommitMessage "fix: ajusta checkout" `
        -SwarmServiceName "cvfacil_ng_cvfacil_ng" -GhcrUser claudiofxbr

.EXAMPLE
    # Só redeploy (sem novo commit), reusando a última imagem já publicada
    ./scripts/deploy-full-auto.ps1 -SkipGit -SwarmServiceName "cvfacil_ng_cvfacil_ng" -GhcrUser claudiofxbr
#>

[CmdletBinding()]
param(
    [string]$CommitMessage,
    [switch]$SkipGit,
    [switch]$SkipDeploy,
    [switch]$SkipBrowser,

    [string]$VpsHost = "69.62.87.38",
    [string]$VpsUser = "root",
    [string]$SshKeyPath = "$HOME/.ssh/cvfacil_deploy_key",

    [string]$SwarmServiceName,

    [string]$GhcrUser,
    [string]$GhcrToken = $env:GHCR_TOKEN,

    [string]$ImageRef = "ghcr.io/claudiofxbr/cvfacil.ng:latest",
    [string]$Branch = "main",
    [string]$WorkflowName = "Build e publica imagem Docker do CVFacil.NG",
    [string]$AppUrl = "https://cvfacil.ng",

    [int]$WorkflowTimeoutSec = 900,
    [int]$ServiceHealthTimeoutSec = 300,
    [int]$HttpRetries = 10,
    [int]$HttpRetryDelaySec = 5
)

$ErrorActionPreference = "Stop"
$script:StepNumber = 0

function Write-Step($msg) {
    $script:StepNumber++
    Write-Host "`n[$script:StepNumber] $msg" -ForegroundColor Cyan
}
function Write-Ok($msg)   { Write-Host "    OK: $msg" -ForegroundColor Green }
function Write-Warn($msg) { Write-Host "    AVISO: $msg" -ForegroundColor Yellow }
function Write-Err($msg)  { Write-Host "    ERRO: $msg" -ForegroundColor Red }

function Fail($msg) {
    Write-Err $msg
    Write-Host "`nPipeline interrompido. Nenhuma etapa seguinte foi executada." -ForegroundColor Red
    exit 1
}

function Invoke-Ssh {
    param([string]$Command, [int]$TimeoutSec = 60)
    $sshArgs = @(
        "-i", $SshKeyPath,
        "-o", "ConnectTimeout=10",
        "-o", "BatchMode=yes",
        "-o", "StrictHostKeyChecking=accept-new",
        "$VpsUser@$VpsHost",
        $Command
    )
    $result = & ssh @sshArgs 2>&1
    $exitCode = $LASTEXITCODE
    return [pscustomobject]@{ Output = ($result -join "`n"); ExitCode = $exitCode }
}

# ── 0. Pré-checagens de ferramentas locais ─────────────────────────────────
Write-Step "Verificando ferramentas necessárias no ambiente local"

foreach ($tool in @("git", "gh", "ssh")) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
        Fail "'$tool' não encontrado no PATH. Instale antes de continuar."
    }
}
Write-Ok "git, gh, ssh disponíveis"

$ghAuth = gh auth status 2>&1
if ($LASTEXITCODE -ne 0) {
    Fail "gh CLI não autenticado. Rode 'gh auth login' antes de usar este script."
}
Write-Ok "gh CLI autenticado"

if (-not $SkipDeploy) {
    if (-not (Test-Path $SshKeyPath)) {
        Fail "Chave SSH não encontrada em '$SshKeyPath'. Ajuste -SshKeyPath ou gere a chave."
    }
    if (-not $GhcrUser -or -not $GhcrToken) {
        Fail "Para o redeploy na VPS, informe -GhcrUser e -GhcrToken (ou defina `$env:GHCR_TOKEN). " +
             "O token precisa do escopo 'read:packages' e é usado para autenticar o pull na VPS."
    }
}

# ── 1. Git: commit + push ───────────────────────────────────────────────────
if ($SkipGit) {
    Write-Step "Etapa Git pulada (-SkipGit)"
    $commitSha = (git rev-parse HEAD).Trim()
    Write-Ok "Usando HEAD atual: $commitSha"
}
else {
    Write-Step "Verificando alterações pendentes no repositório"
    $status = git status --porcelain
    if ($status) {
        if (-not $CommitMessage) {
            Fail "Há alterações não commitadas mas nenhuma -CommitMessage foi informada. " +
                 "Passe -CommitMessage 'texto' ou faça o commit manualmente antes de rodar o script."
        }
        Write-Host "    Arquivos alterados:" -ForegroundColor DarkGray
        $status | ForEach-Object { Write-Host "      $_" -ForegroundColor DarkGray }

        git add -A
        if ($LASTEXITCODE -ne 0) { Fail "Falha ao executar 'git add -A'." }

        git commit -m $CommitMessage
        if ($LASTEXITCODE -ne 0) { Fail "Falha ao criar o commit." }
        Write-Ok "Commit criado: $CommitMessage"
    }
    else {
        Write-Ok "Sem alterações pendentes — usando o HEAD atual"
    }

    Write-Step "Enviando para o GitHub (branch $Branch)"
    git push origin $Branch
    if ($LASTEXITCODE -ne 0) {
        Fail "git push falhou. Verifique conflitos, permissões ou conectividade e tente novamente."
    }
    $commitSha = (git rev-parse HEAD).Trim()
    Write-Ok "Push concluído — commit $commitSha"
}

# ── 2. Acompanhar o workflow do GitHub Actions ──────────────────────────────
if ($SkipDeploy) {
    Write-Warn "SkipDeploy ativo — pulando também o acompanhamento do build (nada a puxar na VPS)"
}
else {
    Write-Step "Localizando a execução do workflow para o commit $commitSha"

    $runId = $null
    $deadline = (Get-Date).AddSeconds(60)
    while (-not $runId -and (Get-Date) -lt $deadline) {
        $runsJson = gh run list --workflow="$WorkflowName" --branch=$Branch --limit=10 --json databaseId,headSha,status 2>$null
        if ($runsJson) {
            $runs = $runsJson | ConvertFrom-Json
            $match = $runs | Where-Object { $_.headSha -eq $commitSha } | Select-Object -First 1
            if ($match) { $runId = $match.databaseId }
        }
        if (-not $runId) { Start-Sleep -Seconds 5 }
    }

    if (-not $runId) {
        Fail "Não encontrei uma execução do workflow '$WorkflowName' para o commit $commitSha em 60s. " +
             "Confirme manualmente com: gh run list --workflow=`"$WorkflowName`""
    }
    Write-Ok "Run encontrado: $runId"

    Write-Step "Acompanhando o build/push da imagem (timeout ${WorkflowTimeoutSec}s)"
    $watchJob = Start-Job -ScriptBlock {
        param($id, $timeout)
        $env:GH_TOKEN = $using:env:GH_TOKEN
        & gh run watch $id --exit-status
        return $LASTEXITCODE
    } -ArgumentList $runId, $WorkflowTimeoutSec

    if (-not (Wait-Job $watchJob -Timeout $WorkflowTimeoutSec)) {
        Stop-Job $watchJob | Out-Null
        Fail "Timeout de ${WorkflowTimeoutSec}s esperando o workflow. Verifique manualmente: gh run view $runId"
    }
    $watchExit = Receive-Job $watchJob
    Remove-Job $watchJob | Out-Null

    if ($watchExit -ne 0) {
        Fail "O workflow falhou. Veja os logs com: gh run view $runId --log-failed"
    }
    Write-Ok "Imagem publicada em $ImageRef"
}

# ── 3. Redeploy na VPS via SSH (Docker Swarm) ───────────────────────────────
if ($SkipDeploy) {
    Write-Step "Redeploy na VPS pulado (-SkipDeploy)"
}
else {
    Write-Step "Conectando na VPS ($VpsHost) para localizar o serviço Swarm do app"

    if (-not $SwarmServiceName) {
        $find = Invoke-Ssh "docker service ls --filter name=cvfacil --format '{{.Name}}'"
        if ($find.ExitCode -ne 0) {
            Fail "Não consegui conectar via SSH na VPS. Saída: $($find.Output)"
        }
        $candidates = $find.Output -split "`n" | Where-Object { $_.Trim() -ne "" }
        if ($candidates.Count -eq 0) {
            Fail "Nenhum serviço Docker Swarm com 'cvfacil' no nome foi encontrado na VPS. " +
                 "Isso normalmente significa que o app 'cvfacil_ng' ainda NÃO foi criado no EasyPanel " +
                 "(pré-requisito manual, único passo que não é automatizável: criar o App na UI do " +
                 "EasyPanel apontando para $ImageRef, com credencial de registry e domínio configurados)."
        }
        if ($candidates.Count -gt 1) {
            Fail "Mais de um serviço candidato encontrado: $($candidates -join ', '). " +
                 "Informe explicitamente com -SwarmServiceName."
        }
        $SwarmServiceName = $candidates[0].Trim()
    }
    Write-Ok "Serviço alvo: $SwarmServiceName"

    Write-Step "Autenticando no GHCR na VPS e forçando novo deploy da imagem"
    $loginCmd = "echo '$GhcrToken' | docker login ghcr.io -u $GhcrUser --password-stdin"
    $login = Invoke-Ssh $loginCmd
    if ($login.ExitCode -ne 0) {
        Fail "Login no GHCR falhou na VPS. Verifique usuário/token (escopo read:packages). Saída: $($login.Output)"
    }
    Write-Ok "Login no GHCR realizado na VPS"

    $updateCmd = "docker service update --with-registry-auth --force --image $ImageRef $SwarmServiceName"
    $update = Invoke-Ssh $updateCmd
    if ($update.ExitCode -ne 0) {
        Fail "Falha ao atualizar o serviço Swarm. Saída: $($update.Output)"
    }
    Write-Ok "Atualização do serviço disparada"

    Write-Step "Esperando o novo container ficar saudável (timeout ${ServiceHealthTimeoutSec}s)"
    $healthy = $false
    $deadline = (Get-Date).AddSeconds($ServiceHealthTimeoutSec)
    while (-not $healthy -and (Get-Date) -lt $deadline) {
        $ps = Invoke-Ssh "docker service ps $SwarmServiceName --filter 'desired-state=running' --format '{{.CurrentState}}' | head -1"
        if ($ps.Output -match "^Running") { $healthy = $true; break }
        Start-Sleep -Seconds 5
    }
    if (-not $healthy) {
        Fail "O novo container não ficou 'Running' dentro do timeout. " +
             "Verifique manualmente: ssh $VpsUser@$VpsHost `"docker service ps $SwarmServiceName`" " +
             "e `"docker service logs $SwarmServiceName --tail 100`""
    }
    Write-Ok "Novo container rodando"
}

# ── 4. Validação HTTP + abrir no navegador ──────────────────────────────────
if ($SkipBrowser) {
    Write-Step "Abertura do navegador pulada (-SkipBrowser)"
}
else {
    Write-Step "Validando resolução DNS de $AppUrl"
    $uri = [Uri]$AppUrl
    $dnsOk = $true
    try { [System.Net.Dns]::GetHostEntry($uri.Host) | Out-Null }
    catch { $dnsOk = $false }

    if (-not $dnsOk) {
        Write-Warn "$($uri.Host) não resolve em DNS ainda. Isso é um pré-requisito manual " +
                    "(registro A no seu provedor de domínio apontando para $VpsHost) e não pode " +
                    "ser automatizado por este script. Abrindo pelo IP como alternativa de verificação."
        $targetUrl = "http://${VpsHost}:3000"
    }
    else {
        $targetUrl = $AppUrl
    }

    Write-Step "Validando resposta HTTP de $targetUrl (até $HttpRetries tentativas)"
    $ok = $false
    for ($i = 1; $i -le $HttpRetries; $i++) {
        try {
            $resp = Invoke-WebRequest -Uri $targetUrl -Method Head -TimeoutSec 10 -SkipCertificateCheck -ErrorAction Stop
            if ($resp.StatusCode -lt 500) { $ok = $true; break }
        }
        catch {
            Write-Host "    tentativa $i/$HttpRetries falhou, aguardando ${HttpRetryDelaySec}s..." -ForegroundColor DarkGray
        }
        Start-Sleep -Seconds $HttpRetryDelaySec
    }

    if (-not $ok) {
        Write-Warn "Não obtive resposta HTTP saudável em $targetUrl após $HttpRetries tentativas. " +
                    "Vou abrir o navegador mesmo assim para você inspecionar visualmente."
    }
    else {
        Write-Ok "App respondendo em $targetUrl"
    }

    Write-Step "Abrindo $targetUrl no navegador padrão"
    Start-Process $targetUrl
    Write-Ok "Navegador acionado"
}

Write-Host "`n=== Pipeline concluído ===" -ForegroundColor Green
