@echo off
:: ============================================================================
::  INICIAR.bat -- CVFacil.NG  |  Versao 2.1  (revisado 2026-04-27)
::
::  Duplo-clique neste arquivo para abrir o aplicativo.
::
::  Pre-requisitos:
::    - Java 21+    https://adoptium.net/temurin/releases/?version=21
::    - Node.js 20+ https://nodejs.org/
::    - JAR compilado (execute COMPILAR.bat uma vez antes)
::
::  Uso avancado:
::    INICIAR.bat --debug     Exibe stdout/stderr do backend direto na janela
::    INICIAR.bat --limpar    Remove node_modules e reinstala dependencias
::
::  Bugs corrigidos nesta versao (vs v2.0):
::    #1  Timestamp incorreto em Windows pt-BR (locale com prefixo de dia)
::    #2  Comando 'tee' inexistente no Windows -- modo debug ficava em branco
::    #3  %CD% expandido em tempo de parse dentro de bloco if (devia ser !CD!)
::    #4  Barra de progresso com ^M nao produzia carriage return real
::    #5  Comparacao numerica de JAVA_MAJOR falhava quando variavel estava vazia
::    #6  Comparacao numerica de NODE_MAJOR falhava quando variavel estava vazia
:: ============================================================================
setlocal enabledelayedexpansion
title CVFacil.NG -- Inicializador

:: -- Argumentos de linha de comando -------------------------------------------
set "DEBUG_MODE=0"
set "LIMPAR_MODE=0"
if /i "%~1"=="--debug"  set "DEBUG_MODE=1"
if /i "%~1"=="--limpar" set "LIMPAR_MODE=1"

:: -- Caminhos absolutos (sem barra final) -------------------------------------
:: %~dp0 retorna o diretorio do .bat com barra final -- removemos a ultima barra.
set "REPO=%~dp0"
if "%REPO:~-1%"=="\" set "REPO=%REPO:~0,-1%"

set "BACKEND_DIR=%REPO%\backend"
set "FRONTEND_DIR=%REPO%\frontend"
set "JAR=%BACKEND_DIR%\target\cvfacil-backend-1.0.0-SNAPSHOT.jar"
set "LOG_DIR=%REPO%\.runtime\logs"
set "BACKEND_LOG=%LOG_DIR%\backend.log"
set "INICIAR_LOG=%LOG_DIR%\iniciar.log"

:: -- Cria estrutura de logs ---------------------------------------------------
if not exist "%LOG_DIR%\" mkdir "%LOG_DIR%" 2>nul

:: -- Timestamp confiavel em qualquer locale do Windows ------------------------
:: CORRECAO BUG #1: %DATE% varia por locale (pt-BR: "seg 27/04/2026").
:: Parsear com delims=/ quebra quando existe prefixo de dia da semana.
:: PowerShell produz formato ISO independente do idioma configurado.
set "TS="
for /f "usebackq tokens=*" %%T in (
    `powershell -NoProfile -Command "Get-Date -Format 'yyyy-MM-dd_HHmm'" 2^>nul`
) do set "TS=%%T"
if "!TS!"=="" (
    for /f "tokens=1,2 delims=:." %%H in ("%TIME: =0%") do set "TS=DESCONHECIDO_%%H%%I"
)

:: -- Registra inicio no log ---------------------------------------------------
echo [!TS!] INICIAR.bat v2.1 iniciado > "%INICIAR_LOG%"
echo [!TS!] REPO=%REPO% >> "%INICIAR_LOG%"
echo [!TS!] DEBUG=%DEBUG_MODE% LIMPAR=%LIMPAR_MODE% >> "%INICIAR_LOG%"

cls
echo.
echo  +====================================================+
echo  ^|          CVFacil.NG  --  Inicializando...         ^|
echo  +====================================================+
echo.
if "%DEBUG_MODE%"=="1" (
    echo  [MODO DEBUG ATIVO] Log: %INICIAR_LOG%
    echo.
)

:: =============================================================================
:: ETAPA 1 -- Verificar privilegios de administrador
::   taskkill /F em processos de outros usuarios exige elevacao.
::   Nao bloqueamos a execucao -- apenas avisamos se necessario.
:: =============================================================================
net session >nul 2>&1
if errorlevel 1 (
    echo  [AVISO] NAO esta sendo executado como Administrador.
    echo          Se houver erro ao liberar as portas 8080/3000,
    echo          clique com o botao direito em INICIAR.bat e escolha
    echo          "Executar como administrador".
    echo.
    echo [!TS!] AVISO: sem privilegios de administrador >> "%INICIAR_LOG%"
) else (
    echo  [OK] Privilegios de administrador confirmados.
    echo [!TS!] OK: privilegios de administrador >> "%INICIAR_LOG%"
)

:: =============================================================================
:: ETAPA 2 -- Verificar Java (obrigatorio: versao 17+)
:: =============================================================================
where java >nul 2>&1
if errorlevel 1 (
    call :ERRO "Java nao foi encontrado no PATH do sistema." ^
         "Instale o Java 21: https://adoptium.net/temurin/releases/?version=21" ^
         "Apos instalar, REINICIE o computador e tente novamente."
    exit /b 1
)

:: Extrai versao principal do Java (ex: "21.0.3" -> 21)
:: CORRECAO BUG #5: se o formato de 'java -version' for incomum, JAVA_MAJOR fica
:: vazia. 'if %VAR% LSS 17' com variavel vazia gera "operador ausente" e aborta.
set "JAVA_RAW="
for /f "tokens=3" %%V in ('java -version 2^>^&1 ^| findstr /i "version"') do set "JAVA_RAW=%%V"
set "JAVA_VER=%JAVA_RAW:"=%"
set "JAVA_MAJOR="
for /f "tokens=1 delims=." %%M in ("%JAVA_VER%") do set "JAVA_MAJOR=%%M"
if "!JAVA_MAJOR!"=="1" (
    for /f "tokens=2 delims=." %%M in ("%JAVA_VER%") do set "JAVA_MAJOR=%%M"
)

if "!JAVA_MAJOR!"=="" (
    echo  [AVISO] Versao do Java nao detectada. Continuando sem verificacao.
    echo          Se o backend nao iniciar, instale Java 21: https://adoptium.net/
    echo [!TS!] AVISO: JAVA_MAJOR nao detectado >> "%INICIAR_LOG%"
    goto JAVA_OK
)

echo  [OK] Java encontrado: versao %JAVA_VER% ^(major=!JAVA_MAJOR!^)
echo [!TS!] OK: Java %JAVA_VER% major=!JAVA_MAJOR! >> "%INICIAR_LOG%"

if !JAVA_MAJOR! LSS 17 (
    call :ERRO "Java %JAVA_VER% encontrado, mas CVFacil.NG requer Java 17+." ^
         "Instale o Java 21: https://adoptium.net/temurin/releases/?version=21" ^
         "Apos instalar, reinicie o computador."
    exit /b 1
)

:JAVA_OK

:: =============================================================================
:: ETAPA 3 -- Verificar Node.js (obrigatorio: versao 18+, recomendado 20+)
:: =============================================================================
where node >nul 2>&1
if errorlevel 1 (
    call :ERRO "Node.js nao foi encontrado no PATH do sistema." ^
         "Instale o Node.js 20 LTS: https://nodejs.org/" ^
         "Apos instalar, REINICIE o computador e tente novamente."
    exit /b 1
)

set "NODE_VER="
for /f "tokens=1" %%V in ('node -v 2^>^&1') do set "NODE_VER=%%V"
set "NODE_VER_NUM=%NODE_VER:v=%"
set "NODE_MAJOR="
for /f "tokens=1 delims=." %%M in ("%NODE_VER_NUM%") do set "NODE_MAJOR=%%M"

:: CORRECAO BUG #6: NODE_MAJOR pode ficar vazia -- mesma logica do Bug #5.
if "!NODE_MAJOR!"=="" (
    echo  [AVISO] Versao do Node.js nao detectada. Continuando sem verificacao.
    echo [!TS!] AVISO: NODE_MAJOR nao detectado >> "%INICIAR_LOG%"
    goto NODE_OK
)

if !NODE_MAJOR! LSS 18 (
    echo  [AVISO] Node.js %NODE_VER% pode causar incompatibilidades.
    echo          Recomendado: Node.js 20 LTS ^(https://nodejs.org/^).
    echo.
)

:NODE_OK
echo  [OK] Node.js %NODE_VER% encontrado.
echo [!TS!] OK: Node.js %NODE_VER% >> "%INICIAR_LOG%"

where npm >nul 2>&1
if errorlevel 1 (
    call :ERRO "npm nao encontrado. Reinstale o Node.js em https://nodejs.org/"
    exit /b 1
)
for /f "tokens=1" %%V in ('npm -v 2^>^&1') do echo  [OK] npm %%V encontrado.

:: =============================================================================
:: ETAPA 4 -- Detectar ferramenta de healthcheck (curl ou PowerShell)
::   curl pode nao existir em versoes antigas do Windows.
::   Fallback automatico para PowerShell Invoke-WebRequest.
:: =============================================================================
set "USE_CURL=0"
set "USE_PWSH=0"
where curl >nul 2>&1
if not errorlevel 1 (
    set "USE_CURL=1"
    echo  [OK] curl disponivel para healthcheck.
    echo [!TS!] OK: curl disponivel >> "%INICIAR_LOG%"
) else (
    where powershell >nul 2>&1
    if not errorlevel 1 (
        set "USE_PWSH=1"
        echo  [OK] PowerShell disponivel ^(fallback para healthcheck^).
        echo [!TS!] OK: PowerShell como fallback >> "%INICIAR_LOG%"
    ) else (
        echo  [AVISO] Sem curl nem PowerShell. Healthcheck desativado.
        echo          O backend iniciara sem confirmacao de disponibilidade.
        echo [!TS!] AVISO: healthcheck desativado >> "%INICIAR_LOG%"
    )
)

:: =============================================================================
:: ETAPA 5 -- Verificar JAR do backend
:: =============================================================================
echo.
if not exist "%JAR%" (
    call :ERRO "JAR do backend nao encontrado em:" "%JAR%" ^
         "Execute COMPILAR.bat para compilar o backend antes de iniciar."
    exit /b 1
)
echo  [OK] Backend JAR encontrado.
echo [!TS!] OK: JAR encontrado >> "%INICIAR_LOG%"

for %%F in ("%JAR%") do set "JAR_SIZE=%%~zF"
if defined JAR_SIZE (
    if !JAR_SIZE! LSS 1048576 (
        echo.
        echo  [AVISO] JAR muito pequeno ^(!JAR_SIZE! bytes, esperado ^> 1MB^).
        echo          Pode estar corrompido. Recompile com COMPILAR.bat.
        echo.
        echo [!TS!] AVISO: JAR suspeito - !JAR_SIZE! bytes >> "%INICIAR_LOG%"
    )
)

:: =============================================================================
:: ETAPA 6 -- Instalar dependencias do frontend (se necessario)
:: =============================================================================
echo.
if "%LIMPAR_MODE%"=="1" (
    echo  [..] Modo --limpar: removendo node_modules...
    if exist "%FRONTEND_DIR%\node_modules\" (
        rmdir /s /q "%FRONTEND_DIR%\node_modules\" 2>nul
        echo  [OK] node_modules removido.
    ) else (
        echo  [OK] node_modules ja estava ausente.
    )
    echo [!TS!] LIMPAR: node_modules removido >> "%INICIAR_LOG%"
)

if not exist "%FRONTEND_DIR%\node_modules\" (
    echo  [..] Instalando dependencias do frontend ^(pode demorar na 1a vez^)...
    echo [!TS!] Instalando npm dependencies... >> "%INICIAR_LOG%"
    pushd "%FRONTEND_DIR%"
    :: CORRECAO BUG #3: %CD% e expandido em tempo de PARSE do bloco if, nao
    :: apos o pushd executar. Usando !CD! (delayed expansion) para ler o valor
    :: real do diretorio corrente depois que o pushd foi executado.
    if not "!CD!"=="%FRONTEND_DIR%" (
        echo  [ERRO] Nao foi possivel acessar: %FRONTEND_DIR%
        echo         Verifique se o caminho existe e se ha permissao de leitura.
        popd
        pause
        exit /b 1
    )
    call npm install --loglevel warn --no-fund --no-audit
    set "NPM_ERR=!errorlevel!"
    popd
    if !NPM_ERR! neq 0 (
        call :ERRO "Falha ao instalar dependencias do frontend ^(npm error !NPM_ERR!^)." ^
             "Verifique sua conexao com a internet e tente novamente." ^
             "Ou execute: INICIAR.bat --limpar"
        exit /b 1
    )
    echo  [OK] Dependencias instaladas com sucesso.
    echo [!TS!] OK: npm install concluido >> "%INICIAR_LOG%"
) else (
    echo  [OK] Dependencias do frontend ja instaladas.
)

:: =============================================================================
:: ETAPA 7 -- Liberar portas 8080 e 3000
::   Se um processo anterior nao foi encerrado, esta etapa libera as portas.
:: =============================================================================
echo.
echo  [..] Verificando disponibilidade das portas 8080 e 3000...
echo [!TS!] Verificando portas >> "%INICIAR_LOG%"
call :LIBERAR_PORTA 8080
call :LIBERAR_PORTA 3000
timeout /t 2 /nobreak >nul

:: =============================================================================
:: ETAPA 8 -- Iniciar backend Spring Boot
::
:: CORRECAO BUG #2: o modo --debug original usava 'tee', que e um comando Unix
:: nao disponivel no Windows nativo. A janela do backend ficava em branco.
::
:: Solucao:
::   Modo DEBUG : Java roda com saida DIRETA na janela -- o usuario ve tudo.
::   Modo NORMAL: saida redirecionada para BACKEND_LOG. A janela exibe o caminho
::                do log. Se o backend encerrar com erro, pausa para leitura.
:: =============================================================================
echo.
echo  [..] Iniciando o backend Spring Boot...
echo [!TS!] Iniciando backend >> "%INICIAR_LOG%"

set "JAVA_ARGS=--spring.profiles.active=local --server.port=8080"

:: O Spring Boot valida a registration OAuth2 'google' na inicializacao e falha
:: o startup com "Client id of registration 'google' must not be empty" se as
:: variaveis ficarem vazias -- mesmo sem uso real de login Google em dev local.
:: So define um valor fake se a variavel ainda nao existir (nao sobrescreve
:: credenciais reais que o usuario ja tenha configurado no ambiente).
if not defined GOOGLE_OAUTH_CLIENT_ID set "GOOGLE_OAUTH_CLIENT_ID=local-dev-client-id"
if not defined GOOGLE_OAUTH_CLIENT_SECRET set "GOOGLE_OAUTH_CLIENT_SECRET=local-dev-client-secret"

if "%DEBUG_MODE%"=="1" (
    start "CVFacil -- Backend [DEBUG nao feche]" /D "%BACKEND_DIR%" cmd /k ^
        "java -jar ""%JAR%"" %JAVA_ARGS%"
) else (
    start "CVFacil -- Backend (nao feche)" /D "%BACKEND_DIR%" cmd /k ^
        "echo Log: %BACKEND_LOG% & echo. & java -jar ""%JAR%"" %JAVA_ARGS% > ""%BACKEND_LOG%"" 2>&1 & if errorlevel 1 (echo. & echo [ERRO: backend encerrou com falha] & pause) else echo [Backend encerrado normalmente]"
)

:: =============================================================================
:: ETAPA 9 -- Aguardar backend ficar pronto (healthcheck com fallback)
::
:: CORRECAO BUG #4: a barra de progresso original usava "^M" ao fim da linha
:: esperando que produzisse carriage return (0x0D). Mas "^M" em texto de arquivo
:: .bat sao dois caracteres: ^ e M -- nao o byte 0x0D. A barra rolava.
::
:: Solucao: acumulacao de pontos com "<NUL set /p" (nao emite newline).
:: Cada iteracao imprime um ponto na mesma linha. Simples, confiavel,
:: zero overhead, compativel com todas as versoes do Windows.
:: =============================================================================
echo.
echo  [..] Aguardando o backend iniciar ^(ate 90 segundos^)...
echo       Log em: %BACKEND_LOG%
echo       Progresso ^(um ponto a cada 2s^):
<NUL set /p "=       "

set /a "TENTATIVAS=0"
set /a "MAX_TENT=45"

:AGUARDA_BACKEND
set /a "TENTATIVAS+=1"

if !TENTATIVAS! GTR %MAX_TENT% (
    echo.
    echo.
    echo  +--------------------------------------------------+
    echo  ^|  [ERRO] Backend nao respondeu em 90 segundos.  ^|
    echo  +--------------------------------------------------+
    echo.
    echo  Causas mais comuns:
    echo   1. Java desatualizado ^(requer versao 17+^)
    echo   2. Porta 8080 bloqueada por firewall ou antivirus
    echo   3. JAR corrompido -- execute COMPILAR.bat
    echo   4. Erro interno -- veja o log:
    echo.
    echo  --- LOG DO BACKEND ^(ultimas 20 linhas^) ---
    if exist "%BACKEND_LOG%" (
        powershell -NoProfile -Command ^
            "Get-Content '%BACKEND_LOG%' -Tail 20 -ErrorAction SilentlyContinue" 2>nul
        if errorlevel 1 type "%BACKEND_LOG%" 2>nul
    ) else (
        echo  [Log nao encontrado -- o processo pode nao ter iniciado]
        echo  Execute: INICIAR.bat --debug  para ver a saida do backend.
    )
    echo  -------------------------------------------
    echo.
    echo  Diagnostico completo: DIAGNOSTICO.bat
    echo [!TS!] ERRO: backend timeout apos %MAX_TENT% tentativas >> "%INICIAR_LOG%"
    echo.
    pause
    exit /b 1
)

timeout /t 2 /nobreak >nul
<NUL set /p "=."

set "HC_OK=0"
if "%USE_CURL%"=="1" (
    curl -fsS --max-time 3 "http://localhost:8080/api/health" 2>nul ^
        | findstr /i "UP" >nul 2>&1
    if not errorlevel 1 set "HC_OK=1"
) else if "%USE_PWSH%"=="1" (
    powershell -NoProfile -Command ^
        "try{$r=(Invoke-WebRequest 'http://localhost:8080/api/health' -TimeoutSec 3 -UseBasicParsing).Content;if($r -match 'UP'){exit 0}else{exit 1}}catch{exit 1}" >nul 2>&1
    if not errorlevel 1 set "HC_OK=1"
) else (
    if !TENTATIVAS! GEQ 15 set "HC_OK=1"
)

if "!HC_OK!"=="0" goto AGUARDA_BACKEND

echo.
echo.
echo  [OK] Backend pronto e respondendo!
echo [!TS!] OK: backend respondeu na tentativa !TENTATIVAS! >> "%INICIAR_LOG%"

:: =============================================================================
:: ETAPA 10 -- Iniciar frontend Next.js
:: =============================================================================
echo.
echo  [..] Iniciando o frontend Next.js...
echo [!TS!] Iniciando frontend >> "%INICIAR_LOG%"
start "CVFacil -- Frontend (nao feche)" /D "%FRONTEND_DIR%" cmd /k "npm run dev"

:: =============================================================================
:: ETAPA 11 -- Aguardar frontend ficar pronto
:: =============================================================================
echo  [..] Aguardando o frontend iniciar ^(ate 60 segundos^)...
<NUL set /p "=       "
set /a "TENTATIVAS=0"

:AGUARDA_FRONTEND
set /a "TENTATIVAS+=1"
if !TENTATIVAS! GTR 30 (
    echo.
    echo  [AVISO] Frontend demorou para responder.
    echo          Abra manualmente: http://localhost:3000
    echo [!TS!] AVISO: frontend timeout >> "%INICIAR_LOG%"
    goto ABRIR_NAVEGADOR
)

timeout /t 2 /nobreak >nul
<NUL set /p "=."

set "FE_OK=0"
if "%USE_CURL%"=="1" (
    curl -fsS --max-time 3 "http://localhost:3000/" >nul 2>&1
    if not errorlevel 1 set "FE_OK=1"
) else if "%USE_PWSH%"=="1" (
    powershell -NoProfile -Command ^
        "try{Invoke-WebRequest 'http://localhost:3000/' -TimeoutSec 3 -UseBasicParsing|Out-Null;exit 0}catch{exit 1}" >nul 2>&1
    if not errorlevel 1 set "FE_OK=1"
) else (
    if !TENTATIVAS! GEQ 20 set "FE_OK=1"
)

if "!FE_OK!"=="0" goto AGUARDA_FRONTEND
echo.
echo  [OK] Frontend pronto!
echo [!TS!] OK: frontend respondeu na tentativa !TENTATIVAS! >> "%INICIAR_LOG%"

:ABRIR_NAVEGADOR
echo.
echo  [..] Abrindo o navegador em http://localhost:3000 ...
timeout /t 1 /nobreak >nul
start http://localhost:3000

echo.
echo  +====================================================+
echo  ^|   CVFacil.NG esta rodando com sucesso!            ^|
echo  ^|                                                    ^|
echo  ^|   Frontend:   http://localhost:3000               ^|
echo  ^|   API health: http://localhost:8080/api/health    ^|
echo  ^|   Log:        .runtime\logs\                      ^|
echo  ^|                                                    ^|
echo  ^|   Para encerrar: feche as janelas Backend e       ^|
echo  ^|   Frontend ^(ou pressione Ctrl+C em cada uma^).    ^|
echo  +====================================================+
echo.
echo  Esta janela pode ser fechada com seguranca.
echo  Para diagnostico completo execute: DIAGNOSTICO.bat
echo.
echo [!TS!] Inicializacao concluida com sucesso >> "%INICIAR_LOG%"
pause
exit /b 0

:: =============================================================================
:: SUB-ROTINA: LIBERAR_PORTA
::   Parametro %~1 = numero da porta a verificar e liberar.
::   Requer privilégios de administrador para encerrar processos de outros usuarios.
:: =============================================================================
:LIBERAR_PORTA
set "PORTA=%~1"
for /f "tokens=5" %%P in (
    'netstat -ano 2^>nul ^| findstr ":%PORTA% " ^| findstr "LISTENING"'
) do (
    if not "%%P"=="" (
        echo  [..] Porta %PORTA% ocupada pelo PID %%P. Encerrando...
        taskkill /PID %%P /F >nul 2>&1
        if errorlevel 1 (
            echo  [AVISO] Nao foi possivel encerrar PID %%P na porta %PORTA%.
            echo          Execute como Administrador se o problema persistir.
            echo [!TS!] AVISO: falha ao encerrar PID %%P porta %PORTA% >> "%INICIAR_LOG%"
        ) else (
            echo  [OK] Porta %PORTA% liberada ^(PID %%P encerrado^).
            echo [!TS!] OK: porta %PORTA% liberada >> "%INICIAR_LOG%"
        )
    )
)
goto :eof

:: =============================================================================
:: SUB-ROTINA: ERRO
::   Exibe mensagem de erro padronizada e registra no log.
::   Uso: call :ERRO "linha1" "linha2" "linha3"  (ate 3 linhas de mensagem)
:: =============================================================================
:ERRO
echo.
echo  +--------------------------------------------------+
echo  ^|  ERRO -- CVFacil.NG nao pode ser iniciado       ^|
echo  +--------------------------------------------------+
if not "%~1"=="" echo  %~1
if not "%~2"=="" echo  %~2
if not "%~3"=="" echo  %~3
echo.
echo  Para diagnostico execute: DIAGNOSTICO.bat
echo  Log de execucao: %INICIAR_LOG%
echo.
echo [!TS!] ERRO: %~1 >> "%INICIAR_LOG%"
pause
goto :eof
