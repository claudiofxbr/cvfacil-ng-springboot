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
setlocal
title CVFacil.NG — Compilando Backend

set "REPO=%~dp0"
if "%REPO:~-1%"=="\" set "REPO=%REPO:~0,-1%"
set "BACKEND_DIR=%REPO%\backend"

echo.
echo  +------------------------------------------------+
echo  ^|    CVFacil.NG — Compilando Backend            ^|
echo  +------------------------------------------------+
echo.

:: Verifica Java
where java >nul 2>&1
if errorlevel 1 (
    echo [ERRO] Java nao encontrado. Instale: https://adoptium.net/temurin/releases/?version=21
    pause & exit /b 1
)
for /f "tokens=*" %%L in ('java -version 2^>^&1 ^| findstr /i "version"') do echo [OK] %%L

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
