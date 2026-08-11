@echo off
:: ============================================================================
::  DIAGNOSTICO.bat — CVFacil.NG
::
::  Execute este script quando o INICIAR.bat falhar.
::  Ele verifica todos os pre-requisitos e gera um relatorio completo.
::
::  Uso: duplo-clique ou execute no Prompt de Comando.
::  Saida: tela + arquivo .runtime\logs\diagnostico_YYYYMMDD_HHMM.log
:: ============================================================================
setlocal enabledelayedexpansion
title CVFacil.NG — Diagnostico do Sistema

set "REPO=%~dp0"
if "%REPO:~-1%"=="\" set "REPO=%REPO:~0,-1%"

set "LOG_DIR=%REPO%\.runtime\logs"
if not exist "%LOG_DIR%\" mkdir "%LOG_DIR%" 2>nul

:: Timestamp
for /f "tokens=2-4 delims=/ " %%A in ("%DATE%") do set "D=%%C%%A%%B"
for /f "tokens=1-2 delims=:." %%H in ("%TIME: =0%") do set "H=%%H%%I"
set "DIAG_LOG=%LOG_DIR%\diagnostico_%D%_%H%.log"

:: Funcao de log
call :LOG "=============================================="
call :LOG " CVFacil.NG — RELATORIO DE DIAGNOSTICO"
call :LOG " Data/Hora: %DATE% %TIME%"
call :LOG " Maquina: %COMPUTERNAME% | Usuario: %USERNAME%"
call :LOG "=============================================="
call :LOG ""

cls
echo.
echo  +=======================================================+
echo  ^|        CVFacil.NG — Diagnostico do Sistema           ^|
echo  +=======================================================+
echo.
echo  Arquivo de relatorio: %DIAG_LOG%
echo.

:: =============================================================================
:: BLOCO 1 — Privilegios de administrador
:: =============================================================================
call :TITULO "1. PRIVILEGIOS DE ADMINISTRADOR"

net session >nul 2>&1
if errorlevel 1 (
    call :WARN "NAO esta sendo executado como administrador."
    call :INFO "  Impacto: 'taskkill /F' pode falhar para processos de outros usuarios."
    call :INFO "  Solucao: clique com botao direito > 'Executar como administrador'."
) else (
    call :OK "Executando como administrador."
)

:: =============================================================================
:: BLOCO 2 — Java
:: =============================================================================
call :TITULO "2. JAVA"

where java >nul 2>&1
if errorlevel 1 (
    call :FALHA "Java NAO encontrado no PATH."
    call :INFO "  PATH atual: %PATH%"
    call :INFO "  Instale: https://adoptium.net/temurin/releases/?version=21"
    call :INFO "  Apos instalar: reinicie o computador."
    goto VERIFICA_NODE
)

for /f "tokens=*" %%L in ('java -version 2^>^&1') do (
    call :INFO "  %%L"
)

:: Versao numerica
for /f "tokens=3" %%V in ('java -version 2^>^&1 ^| findstr /i "version"') do set "JVER_RAW=%%V"
set "JVER=%JVER_RAW:"=%"
for /f "tokens=1 delims=." %%M in ("%JVER%") do set "JMAJOR=%%M"
if "%JMAJOR%"=="1" for /f "tokens=2 delims=." %%M in ("%JVER%") do set "JMAJOR=%%M"

if %JMAJOR% LSS 17 (
    call :FALHA "Java %JVER% e insuficiente. CVFacil.NG requer Java 17+."
    call :INFO "  Instale Java 21: https://adoptium.net/temurin/releases/?version=21"
) else (
    call :OK "Java %JVER% (major=%JMAJOR%) — compativel."
)

:: Verifica JAVA_HOME
if "%JAVA_HOME%"=="" (
    call :WARN "JAVA_HOME nao esta definido. Pode causar problemas em alguns casos."
) else (
    call :OK "JAVA_HOME=%JAVA_HOME%"
    if not exist "%JAVA_HOME%\bin\java.exe" (
        call :WARN "JAVA_HOME aponta para diretorio sem java.exe: %JAVA_HOME%\bin\java.exe"
    )
)

:VERIFICA_NODE
:: =============================================================================
:: BLOCO 3 — Node.js e npm
:: =============================================================================
call :TITULO "3. NODE.JS E NPM"

where node >nul 2>&1
if errorlevel 1 (
    call :FALHA "Node.js NAO encontrado no PATH."
    call :INFO "  Instale: https://nodejs.org/ (versao 20 LTS)"
    goto VERIFICA_PORTAS
)

for /f "tokens=1" %%V in ('node -v 2^>^&1') do set "NVER=%%V"
for /f "tokens=1 delims=." %%M in ("%NVER:v=%") do set "NMAJOR=%%M"

if %NMAJOR% LSS 18 (
    call :WARN "Node.js %NVER% — recomendado 20+."
) else (
    call :OK "Node.js %NVER% (major=%NMAJOR%) — compativel."
)

where npm >nul 2>&1
if errorlevel 1 (
    call :FALHA "npm NAO encontrado."
) else (
    for /f "tokens=1" %%V in ('npm -v 2^>^&1') do call :OK "npm %%V encontrado."
)

:VERIFICA_PORTAS
:: =============================================================================
:: BLOCO 4 — Portas 8080 e 3000
:: =============================================================================
call :TITULO "4. PORTAS DE REDE (8080 e 3000)"

call :CHECAR_PORTA 8080
call :CHECAR_PORTA 3000

:: =============================================================================
:: BLOCO 5 — curl e PowerShell
:: =============================================================================
call :TITULO "5. FERRAMENTAS DE HTTP (healthcheck)"

where curl >nul 2>&1
if errorlevel 1 (
    call :WARN "curl nao encontrado. INICIAR.bat usara PowerShell como fallback."
) else (
    for /f "tokens=*" %%V in ('curl --version 2^>^&1 ^| findstr "curl"') do (
        call :OK "curl: %%V"
        goto CURL_OK
    )
    :CURL_OK
)

where powershell >nul 2>&1
if errorlevel 1 (
    call :WARN "PowerShell nao encontrado. Healthcheck sera desativado."
) else (
    for /f "tokens=*" %%V in ('powershell -Command "$PSVersionTable.PSVersion.ToString()" 2^>^&1') do (
        call :OK "PowerShell %%V"
    )
)

:: =============================================================================
:: BLOCO 6 — Arquivos do projeto
:: =============================================================================
call :TITULO "6. ARQUIVOS DO PROJETO"

set "JAR=%REPO%\backend\target\cvfacil-backend-1.0.0-SNAPSHOT.jar"
if exist "%JAR%" (
    for %%F in ("%JAR%") do (
        call :OK "JAR encontrado: %%~nxF (%%~zF bytes)"
        if %%~zF LSS 1048576 call :WARN "JAR suspeito: tamanho menor que 1MB. Recompile."
    )
) else (
    call :FALHA "JAR nao encontrado: %JAR%"
    call :INFO "  Execute COMPILAR.bat para gerar o JAR."
)

if exist "%REPO%\frontend\package.json" (
    call :OK "frontend\package.json presente."
) else (
    call :FALHA "frontend\package.json NAO encontrado."
)

if exist "%REPO%\frontend\node_modules\" (
    call :OK "frontend\node_modules presente."
) else (
    call :WARN "frontend\node_modules ausente. INICIAR.bat executara 'npm install'."
)

if exist "%REPO%\backend\src\main\resources\application-local.yml" (
    call :OK "application-local.yml presente."
) else (
    call :WARN "application-local.yml ausente."
    call :INFO "  Copie: backend\src\main\resources\application-local.yml.example"
    call :INFO "  Renomeie para: application-local.yml"
)

:: =============================================================================
:: BLOCO 7 — Firewall e antivirus
:: =============================================================================
call :TITULO "7. FIREWALL DO WINDOWS"

:: Verifica se firewall esta ativo
netsh advfirewall show allprofiles state 2>nul | findstr /i "ON" >nul
if not errorlevel 1 (
    call :WARN "Firewall do Windows ATIVO. Se o backend nao responder,"
    call :INFO "  adicione excecao para a porta 8080 ou desative temporariamente para testar."
    call :INFO "  Comando admin para excecao: netsh advfirewall firewall add rule"
    call :INFO "    name=""CVFacil Backend"" dir=in action=allow protocol=TCP localport=8080"
) else (
    call :OK "Firewall do Windows parece inativo (ou sem permissao para verificar)."
)

:: =============================================================================
:: BLOCO 8 — Logs existentes
:: =============================================================================
call :TITULO "8. LOGS EXISTENTES"

if exist "%REPO%\.runtime\logs\backend.log" (
    call :INFO "  --- ULTIMAS 30 LINHAS DO BACKEND LOG ---"
    powershell -Command "Get-Content '%REPO%\.runtime\logs\backend.log' -Tail 30 -ErrorAction SilentlyContinue" 2>nul >> "%DIAG_LOG%"
    powershell -Command "Get-Content '%REPO%\.runtime\logs\backend.log' -Tail 30 -ErrorAction SilentlyContinue" 2>nul
    call :INFO "  -----------------------------------------"
) else (
    call :INFO "  Nenhum log de backend encontrado (backend ainda nao foi iniciado)."
)

if exist "%REPO%\.runtime\logs\iniciar.log" (
    call :INFO "  --- LOG DO ULTIMO INICIAR.bat ---"
    type "%REPO%\.runtime\logs\iniciar.log" >> "%DIAG_LOG%"
    type "%REPO%\.runtime\logs\iniciar.log"
    call :INFO "  ---------------------------------"
)

:: =============================================================================
:: RESULTADO FINAL
:: =============================================================================
call :TITULO "RESULTADO"
echo.
echo  Relatorio completo salvo em:
echo  %DIAG_LOG%
echo.
echo  Se ainda houver problemas:
echo   1. Envie o arquivo acima para o suporte.
echo   2. Tente executar INICIAR.bat com botao direito > "Executar como administrador".
echo   3. Desative temporariamente o antivirus e tente novamente.
echo   4. Execute: INICIAR.bat --debug  (exibe log completo do backend)
echo.
pause
exit /b 0

:: =============================================================================
:: SUB-ROTINAS DE LOG E FORMATACAO
:: =============================================================================

:TITULO
echo.
echo  ── %~1
call :LOG ""
call :LOG "── %~1"
goto :eof

:OK
echo   [OK]  %~1
call :LOG "[OK]  %~1"
goto :eof

:WARN
echo   [!!]  %~1
call :LOG "[!!]  %~1"
goto :eof

:FALHA
echo   [XX]  %~1
call :LOG "[XX]  %~1"
goto :eof

:INFO
echo         %~1
call :LOG "      %~1"
goto :eof

:LOG
echo %~1 >> "%DIAG_LOG%"
goto :eof

:CHECAR_PORTA
set "P=%~1"
netstat -ano 2>nul | findstr ":%P% " | findstr "LISTENING" >nul 2>&1
if errorlevel 1 (
    call :OK "Porta %P% livre."
) else (
    for /f "tokens=5" %%X in ('netstat -ano 2^>nul ^| findstr ":%P% " ^| findstr "LISTENING"') do (
        call :WARN "Porta %P% em uso pelo PID %%X."
        :: Tenta descobrir o nome do processo
        for /f "tokens=1" %%N in ('tasklist /FI "PID eq %%X" /NH 2^>nul ^| findstr /v "INFO:"') do (
            call :INFO "  Processo: %%N (PID %%X)"
        )
        call :INFO "  INICIAR.bat tentara encerrar este processo automaticamente."
        call :INFO "  Se falhar: execute como administrador ou encerre manualmente."
    )
)
goto :eof
