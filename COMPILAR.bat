@echo off
:: ============================================================================
::  COMPILAR.bat — Compila o backend do CVFacil.NG (gera o JAR)
::
::  Execute este arquivo quando:
::   - For a primeira vez e o JAR nao existir
::   - Apos fazer mudancas no codigo do backend
::
::  Pre-requisitos: Java 17+ e Maven 3.9+ instalados no PATH
::  Ou: use "scripts\start-cvfacil.bat" que baixa o Maven automaticamente.
:: ============================================================================
setlocal enabledelayedexpansion
title CVFacil.NG — Compilando Backend

set "REPO=%~dp0"
if "%REPO:~-1%"=="\" set "REPO=%REPO:~0,-1%"
set "BACKEND_DIR=%REPO%\backend"

echo.
echo  +------------------------------------------------+
echo  ^|    CVFacil.NG — Compilando Backend            ^|
echo  +------------------------------------------------+
echo.

:: ACHADO REAL: em maquinas com mais de um JDK instalado (ex: Java 17 do
:: Amazon Corretto registrado no PATH DE SISTEMA + Java 21 do Adoptium so no
:: PATH DE USUARIO), o Windows resolve o PATH de Sistema ANTES do de Usuario
:: -- o "java" bare pode nao ser o 21 mesmo com JAVA_HOME/PATH do usuario
:: corretos. JAVA_HOME, quando definido, e mais confiavel: usamos ele
:: explicitamente aqui. O "mvn" abaixo ja respeita JAVA_HOME sozinho para
:: compilar -- esta checagem e so para confirmar a versao ANTES de compilar,
:: com uma mensagem clara em vez do backend compilar com Java 17 por engano.
set "JAVA_EXE=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" (
    set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
    echo [OK] JAVA_HOME definido -- usando "%JAVA_HOME%\bin\java.exe" explicitamente.
)

:: Verifica Java
where "%JAVA_EXE%" >nul 2>&1
if errorlevel 1 (
    echo [ERRO] Java nao encontrado ^(nem via JAVA_HOME, nem no PATH^). Instale: https://adoptium.net/temurin/releases/?version=21
    pause & exit /b 1
)
for /f "tokens=*" %%L in ('"%JAVA_EXE%" -version 2^>^&1 ^| findstr /i "version"') do echo [OK] %%L

set "JAVA_RAW="
for /f "tokens=3" %%V in ('"%JAVA_EXE%" -version 2^>^&1 ^| findstr /i "version"') do set "JAVA_RAW=%%V"
set "JAVA_VER=%JAVA_RAW:"=%"
set "JAVA_MAJOR="
for /f "tokens=1 delims=." %%M in ("%JAVA_VER%") do set "JAVA_MAJOR=%%M"
if "!JAVA_MAJOR!"=="1" (
    for /f "tokens=2 delims=." %%M in ("%JAVA_VER%") do set "JAVA_MAJOR=%%M"
)
if defined JAVA_MAJOR if !JAVA_MAJOR! LSS 21 (
    echo.
    echo [ERRO] Java %JAVA_VER% encontrado, mas CVFacil.NG requer Java 21+ ^(backend/pom.xml^).
    echo        Instale o Java 21: https://adoptium.net/temurin/releases/?version=21
    echo        Se ja tiver o Java 21 instalado, defina a variavel JAVA_HOME apontando
    echo        para ele ^(ex: C:\...\jdk-21.0.12.101-hotspot^) e abra um novo terminal.
    pause & exit /b 1
)

:: Escolhe Maven: global -> scripts auto-download
set "MVN="
where mvn >nul 2>&1 && set "MVN=mvn"
if "%MVN%"=="" (
    echo [..] Maven nao encontrado no PATH.
    echo      Usando o script completo que baixa o Maven automaticamente...
    echo.
    call "%REPO%\scripts\start-cvfacil.bat" -SkipInstall
    exit /b %errorlevel%
)

echo [OK] Maven: %MVN%
echo.
echo [..] Compilando backend ^(pode levar 2-3 minutos na 1a vez^)...
echo.

pushd "%BACKEND_DIR%"
call %MVN% package -DskipTests -Dspotless.skip=true
if errorlevel 1 (
    echo.
    echo [ERRO] Compilacao falhou. Veja os erros acima.
    popd
    pause
    exit /b 1
)
popd

echo.
echo [OK] Compilacao concluida com sucesso!
echo      JAR gerado em: backend\target\cvfacil-backend-1.0.0-SNAPSHOT.jar
echo.
echo  Agora execute INICIAR.bat para iniciar a aplicacao.
echo.
pause
exit /b 0
