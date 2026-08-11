@echo off
REM ============================================================================
REM  start-cvfacil.bat
REM  Launcher rapido (duplo-clique) para start-cvfacil.ps1
REM ============================================================================
setlocal

set "SCRIPT_DIR=%~dp0"
set "PS1=%SCRIPT_DIR%start-cvfacil.ps1"

if not exist "%PS1%" (
    echo [XX] start-cvfacil.ps1 nao encontrado em "%SCRIPT_DIR%"
    pause
    exit /b 1
)

echo.
echo  +---------------------------------------------+
echo  ^|   CVFacil.NG - iniciando via PowerShell     ^|
echo  +---------------------------------------------+
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%PS1%" %*
set EXITCODE=%ERRORLEVEL%

echo.
echo [i] Script finalizado (exit=%EXITCODE%). Pressione qualquer tecla para fechar.
pause >nul
exit /b %EXITCODE%
