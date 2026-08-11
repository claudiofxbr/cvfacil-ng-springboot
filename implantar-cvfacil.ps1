#requires -version 5.1
<#
    implantar-cvfacil.ps1
    ------------------------------------------------------------------------
    Script criado a pedido do usuario neste subdiretorio especifico
    (C:\Users\VeKTI-01\Documents\Claude\Projects\CVFacil.NG). IMPORTANTE:
    esta pasta NAO e uma copia Git do projeto CVFacil.NG (nao tem .git, e a
    estrutura de arquivos aqui e diferente do codigo real). Por isso, este
    script aponta para o repositorio de trabalho verdadeiro atraves da
    variavel $CaminhoDoProjeto abaixo, em vez de operar sobre esta pasta.

    Fluxo:
      Fase 1 - envia (commit + push) o projeto real para o GitHub
      Fase 2 - aguarda o GitHub Actions terminar de buildar/publicar a imagem
      Fase 3 - conecta na VPS Hostinger via SSH e atualiza o servico Docker
               Swarm do app cvfacil.NG para a imagem publicada

    Exemplo:
        .\implantar-cvfacil.ps1 -MensagemCommit "fix: ajusta X" `
            -NomeServicoSwarm "cvfacil_ng_cvfacil_ng" -UsuarioRegistry claudiofxbr
#>

param(
    [string]$CaminhoDoProjeto = "C:\Users\VeKTI-01\Downloads\cvfacil-ng-dev",
    [string]$RepositorioGitHub = "claudiofxbr/CVFacil.NG-00",
    [string]$Branch = "main",

    [string]$MensagemCommit,
    [switch]$NaoEnviarGit,
    [switch]$NaoImplantarVps,

    [string]$NomeWorkflow = "Build e publica imagem Docker do CVFacil.NG",
    [string]$ImagemDocker = "ghcr.io/claudiofxbr/cvfacil.ng:latest",

    [string]$EnderecoVps = "69.62.87.38",
    [string]$UsuarioVps  = "root",
    [string]$ChaveSshVps = "$HOME\.ssh\cvfacil_deploy_key",
    [string]$NomeServicoSwarm,

    [string]$UsuarioRegistry,
    [string]$TokenRegistry = $env:GHCR_TOKEN,

    [int]$EsperaMaximaBuildMinutos = 15,
    [int]$EsperaMaximaServicoMinutos = 5
)

$erro = $false

function Log-Fase([string]$texto) {
    Write-Host ""
    Write-Host ">>> $texto" -ForegroundColor Magenta
}
function Log-Ok([string]$texto)    { Write-Host "    [ok] $texto" -ForegroundColor Green }
function Log-Info([string]$texto)  { Write-Host "    $texto" -ForegroundColor Gray }
function Log-Falha([string]$texto) {
    Write-Host "    [falha] $texto" -ForegroundColor Red
    $script:erro = $true
}

Write-Host "========================================================" -ForegroundColor DarkGray
Write-Host " Implantacao CVFacil.NG — GitHub + VPS Hostinger" -ForegroundColor White
Write-Host " Script fisicamente em: $PSScriptRoot" -ForegroundColor DarkGray
Write-Host " Operando sobre o projeto em: $CaminhoDoProjeto" -ForegroundColor DarkGray
Write-Host "========================================================" -ForegroundColor DarkGray

# ── Verificação de sanidade: o caminho apontado é mesmo o projeto certo? ────
Log-Fase "Verificacao inicial"

if (-not (Test-Path (Join-Path $CaminhoDoProjeto ".git"))) {
    Log-Falha "'$CaminhoDoProjeto' nao contem uma pasta .git. Ajuste -CaminhoDoProjeto para o clone real do repositorio."
}
else {
    $origemAtual = git -C $CaminhoDoProjeto remote get-url origin 2>$null
    if ($origemAtual -notmatch [regex]::Escape($RepositorioGitHub)) {
        Log-Falha "O remote de '$CaminhoDoProjeto' e '$origemAtual', nao corresponde a '$RepositorioGitHub'."
    }
    else {
        Log-Ok "Projeto confirmado em '$CaminhoDoProjeto' (origin: $origemAtual)"
    }
}

foreach ($ferramenta in @("git", "gh", "ssh")) {
    if (-not (Get-Command $ferramenta -ErrorAction SilentlyContinue)) {
        Log-Falha "Ferramenta '$ferramenta' nao encontrada no PATH."
    }
}

if ($erro) {
    Write-Host ""
    Write-Host "Verificacao inicial falhou. Corrija os pontos acima antes de continuar." -ForegroundColor Red
    exit 1
}
Log-Ok "Ferramentas necessarias (git, gh, ssh) disponiveis"

# ── Fase 1: enviar para o GitHub ─────────────────────────────────────────────
$commitPublicado = $null

if ($NaoEnviarGit) {
    Log-Fase "Fase 1 pulada (-NaoEnviarGit)"
    $commitPublicado = (git -C $CaminhoDoProjeto rev-parse HEAD).Trim()
    Log-Info "Usando HEAD atual: $commitPublicado"
}
else {
    Log-Fase "Fase 1 — Enviando o projeto para o GitHub"

    $pendencias = git -C $CaminhoDoProjeto status --porcelain
    if ($pendencias) {
        if (-not $MensagemCommit) {
            Log-Falha "Ha alteracoes pendentes e nenhuma -MensagemCommit foi informada."
        }
        else {
            git -C $CaminhoDoProjeto add -A
            git -C $CaminhoDoProjeto commit -m $MensagemCommit 2>&1 | Out-Null
            if ($LASTEXITCODE -eq 0) { Log-Ok "Commit criado: $MensagemCommit" }
            else { Log-Falha "Falha ao criar o commit." }
        }
    }
    else {
        Log-Info "Nenhuma alteracao pendente no projeto — reaproveitando o HEAD atual"
    }

    if (-not $erro) {
        git -C $CaminhoDoProjeto push origin $Branch 2>&1 | Out-Null
        if ($LASTEXITCODE -eq 0) {
            $commitPublicado = (git -C $CaminhoDoProjeto rev-parse HEAD).Trim()
            Log-Ok "Push concluido. Commit publicado: $commitPublicado"
        }
        else {
            Log-Falha "'git push' falhou — verifique credenciais, conflitos ou conectividade."
        }
    }
}

if ($erro) {
    Write-Host "`nPipeline interrompido na Fase 1.`n" -ForegroundColor Red
    exit 1
}

# ── Fase 2: aguardar build no GitHub Actions ────────────────────────────────
if ($NaoImplantarVps -and $NaoEnviarGit) {
    Log-Fase "Fase 2 pulada"
}
else {
    Log-Fase "Fase 2 — Aguardando build/publicacao da imagem Docker"

    $prazo = (Get-Date).AddMinutes($EsperaMaximaBuildMinutos)
    $idExecucao = $null

    while (-not $idExecucao -and (Get-Date) -lt $prazo) {
        $execucoes = gh run list --repo $RepositorioGitHub --workflow="$NomeWorkflow" --branch=$Branch --limit=15 --json databaseId,headSha 2>$null
        if ($execucoes) {
            $achada = ($execucoes | ConvertFrom-Json) | Where-Object { $_.headSha -eq $commitPublicado } | Select-Object -First 1
            if ($achada) { $idExecucao = $achada.databaseId }
        }
        if (-not $idExecucao) { Start-Sleep -Seconds 6; Log-Info "aguardando o GitHub registrar a execucao..." }
    }

    if (-not $idExecucao) {
        Log-Falha "Nenhuma execucao encontrada para o commit $commitPublicado dentro de $EsperaMaximaBuildMinutos min."
    }
    else {
        Log-Info "Execucao $idExecucao localizada, acompanhando..."
        gh run watch $idExecucao --repo $RepositorioGitHub --exit-status
        if ($LASTEXITCODE -eq 0) { Log-Ok "Imagem publicada em $ImagemDocker" }
        else { Log-Falha "O workflow falhou. Detalhes: gh run view $idExecucao --repo $RepositorioGitHub --log-failed" }
    }
}

if ($erro) {
    Write-Host "`nPipeline interrompido na Fase 2.`n" -ForegroundColor Red
    exit 1
}

# ── Fase 3: implantar na VPS via SSH ────────────────────────────────────────
if ($NaoImplantarVps) {
    Log-Fase "Fase 3 pulada (-NaoImplantarVps)"
}
else {
    Log-Fase "Fase 3 — Implantando na VPS Hostinger ($EnderecoVps) via SSH"

    if (-not (Test-Path $ChaveSshVps)) {
        Log-Falha "Chave SSH nao encontrada em '$ChaveSshVps'."
    }
    if (-not $UsuarioRegistry -or -not $TokenRegistry) {
        Log-Falha "Informe -UsuarioRegistry e -TokenRegistry (ou `$env:GHCR_TOKEN) para autenticar o pull da imagem privada."
    }

    if (-not $erro) {
        $sshBase = @("-i", $ChaveSshVps, "-o", "ConnectTimeout=10", "-o", "BatchMode=yes", "-o", "StrictHostKeyChecking=accept-new", "$UsuarioVps@$EnderecoVps")

        $servico = $NomeServicoSwarm
        if (-not $servico) {
            Log-Info "Nome do servico Swarm nao informado, tentando localizar..."
            $achados = & ssh @sshBase "docker service ls --filter name=cvfacil --format '{{.Name}}'" 2>&1
            $lista = $achados -split "`n" | Where-Object { $_.Trim() -ne "" }
            if ($lista.Count -eq 1) { $servico = $lista[0].Trim() }
            elseif ($lista.Count -eq 0) {
                Log-Falha "Nenhum servico 'cvfacil*' encontrado na VPS. O app precisa ser criado uma vez na UI do EasyPanel antes disso funcionar."
            }
            else {
                Log-Falha "Mais de um servico candidato ($($lista -join ', ')). Informe -NomeServicoSwarm explicitamente."
            }
        }

        if (-not $erro) {
            Log-Info "Servico alvo: $servico"

            & ssh @sshBase "echo '$TokenRegistry' | docker login ghcr.io -u $UsuarioRegistry --password-stdin" 2>&1 | Out-Null
            if ($LASTEXITCODE -ne 0) { Log-Falha "Login no GHCR falhou na VPS." }
            else {
                Log-Ok "Autenticado no GHCR"

                & ssh @sshBase "docker service update --with-registry-auth --force --image $ImagemDocker $servico" 2>&1 | Out-Null
                if ($LASTEXITCODE -ne 0) { Log-Falha "Falha ao atualizar o servico Swarm '$servico'." }
                else {
                    Log-Ok "Atualizacao do servico disparada"

                    $prazoServico = (Get-Date).AddMinutes($EsperaMaximaServicoMinutos)
                    $saudavel = $false
                    while (-not $saudavel -and (Get-Date) -lt $prazoServico) {
                        $estado = & ssh @sshBase "docker service ps $servico --filter 'desired-state=running' --format '{{.CurrentState}}'" 2>&1
                        if ($estado -match "^Running") { $saudavel = $true; break }
                        Start-Sleep -Seconds 5
                    }
                    if ($saudavel) { Log-Ok "Novo container em execucao na VPS" }
                    else { Log-Falha "O container nao ficou 'Running' dentro do prazo. Verifique: docker service logs $servico" }
                }
            }
        }
    }
}

# ── Resultado final ──────────────────────────────────────────────────────────
Write-Host ""
Write-Host "========================================================" -ForegroundColor DarkGray
if ($erro) {
    Write-Host " RESULTADO: houve falha em uma ou mais fases (ver acima)" -ForegroundColor Red
    Write-Host "========================================================" -ForegroundColor DarkGray
    exit 1
}
else {
    Write-Host " RESULTADO: concluido com sucesso" -ForegroundColor Green
    Write-Host "========================================================" -ForegroundColor DarkGray
    if ($commitPublicado) { Log-Info "Commit: $commitPublicado" }
    exit 0
}
