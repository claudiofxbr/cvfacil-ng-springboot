@echo off
REM CVfacil.NG - Startup Script (Batch/CMD)
REM Alternativa simples para Windows

setlocal enabledelayedexpansion

set "PROJECT_ROOT=%CD%"
set "APP_PORT=3000"
set "APP_URL=http://localhost:%APP_PORT%"
set "LOG_DIR=%PROJECT_ROOT%\logs"

cls
echo.
echo ====================================================
echo          CVfacil.NG STARTUP
echo       Inicializando aplicativo...
echo ====================================================
echo.

REM Verificar Node.js
echo [*] Verificando Node.js...
where node >/dev/null 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [ERRO] Node.js nao encontrado!
    echo Faça download em https://nodejs.org/
    pause
    exit /b 1
)
for /f "tokens=*" %%i in ('node -v') do set NODE_VERSION=%%i
echo [OK] Node.js: %NODE_VERSION%

REM Verificar npm
echo [*] Verificando npm...
where npm >/dev/null 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [ERRO] npm nao encontrado!
    pause
    exit /b 1
)
for /f "tokens=*" %%i in ('npm -v') do set NPM_VERSION=%%i
echo [OK] npm: %NPM_VERSION%

REM Verificar package.json
if not exist "package.json" (
    echo [ERRO] package.json nao encontrado!
    pause
    exit /b 1
)
echo [OK] package.json encontrado
echo.

REM Limpeza
echo [*] Limpando ambiente...
if exist .next rmdir /s /q .next >/dev/null 2>&1
if exist dist rmdir /s /q dist >/dev/null 2>&1
if exist logs rmdir /s /q logs >/dev/null 2>&1
call npm cache clean --force >/dev/null 2>&1
echo [OK] Ambiente limpo
echo.

REM Instalar dependências
echo [*] Instalando dependencias...
if exist node_modules (
    echo [AVISO] node_modules ja existe
) else (
    call npm install --legacy-peer-deps
    if %ERRORLEVEL% NEQ 0 (
        echo [ERRO] Falha ao instalar dependencias
        pause
        exit /b 1
    )
)
echo [OK] Dependencias ok
echo.

REM Build
echo [*] Compilando projeto...
call npm run build
if %ERRORLEVEL% NEQ 0 (
    echo [ERRO] Falha ao compilar
    pause
    exit /b 1
)
echo [OK] Projeto compilado
echo.

REM Criar logs
if not exist logs mkdir logs >/dev/null 2>&1

REM Iniciar servidor em nova janela
echo [*] Iniciando servidor...
start "CVfacil.NG Server" cmd /k "cd /d "%PROJECT_ROOT%" && npm run dev"

REM Aguardar servidor
echo [*] Aguardando servidor (max 60 segundos)...
set /a count=0
:wait_loop
timeout /t 2 /nobreak >/dev/null
set /a count=!count!+1
if %count% lss 30 (
    powershell -Command "try{(New-Object Net.WebClient).DownloadString('http://localhost:%APP_PORT%')}catch{exit 1}" >/dev/null 2>&1
    if %ERRORLEVEL% EQU 0 (
        echo [OK] Servidor respondendo!
        goto success
    )
    goto wait_loop
)

:success
echo.
echo ====================================================
echo       INICIALIZACAO COMPLETA!
echo ====================================================
echo.
echo CVfacil.NG esta rodando!
echo.
echo URL:        %APP_URL%
echo Node.js:    %NODE_VERSION%
echo npm:        %NPM_VERSION%
echo Logs:       %LOG_DIR%\server.log
echo.
echo [*] Abrindo navegador...
start %APP_URL%
echo.
echo Para parar o servidor, feche a janela aberta
echo.
pause
