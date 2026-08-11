@echo off
setlocal EnableDelayedExpansion

set "TESSDATA_DIR=%~dp0backend\tessdata"
set "APP_YML=%~dp0backend\src\main\resources\application-local.yml"
set "ERROS=0"

echo.
echo ============================================================
echo   CVFacil.NG - Verificacao da Configuracao OCR
echo ============================================================
echo.

:: ── 1. Arquivos tessdata ─────────────────────────────────────────────────────
echo [1/4] Verificando arquivos tessdata...

if not exist "!TESSDATA_DIR!" (
    echo       [ERRO] Diretorio nao encontrado: backend\tessdata\
    echo              Execute SETUP_OCR.bat primeiro.
    set "ERROS=1"
    goto :CHECK_YML
)

if exist "!TESSDATA_DIR!\por.traineddata" (
    for %%F in ("!TESSDATA_DIR!\por.traineddata") do set "SZ=%%~zF"
    echo       [OK] por.traineddata encontrado (!SZ! bytes^)
) else (
    echo       [ERRO] por.traineddata NAO encontrado em backend\tessdata\
    echo              Execute SETUP_OCR.bat para baixar.
    set "ERROS=1"
)

if exist "!TESSDATA_DIR!\eng.traineddata" (
    for %%F in ("!TESSDATA_DIR!\eng.traineddata") do set "SZ=%%~zF"
    echo       [OK] eng.traineddata encontrado (!SZ! bytes^)
) else (
    echo       [ERRO] eng.traineddata NAO encontrado em backend\tessdata\
    echo              Execute SETUP_OCR.bat para baixar.
    set "ERROS=1"
)

:: ── 2. application-local.yml ─────────────────────────────────────────────────
:CHECK_YML
echo.
echo [2/4] Verificando application-local.yml...

if not exist "!APP_YML!" (
    echo       [ERRO] Arquivo nao encontrado:
    echo              !APP_YML!
    set "ERROS=1"
    goto :CHECK_JAVA
)

:: Verificar se OCR esta habilitado (busca "enabled: true" na secao ocr)
findstr /C:"enabled: true" "!APP_YML!" >nul 2>&1
if !errorlevel! equ 0 (
    echo       [OK] OCR habilitado ^(enabled: true^)
) else (
    echo       [AVISO] OCR ainda desabilitado ^(enabled: false^)
    echo              Edite application-local.yml e mude para: enabled: true
)

:: Verificar tessdata-path
findstr /C:"tessdata-path" "!APP_YML!" >nul 2>&1
if !errorlevel! equ 0 (
    findstr /C:"tessdata" "!APP_YML!" | findstr /V "#" | findstr /C:"./" >nul 2>&1
    if !errorlevel! equ 0 (
        echo       [OK] tessdata-path: "./tessdata" configurado
    ) else (
        echo       [INFO] tessdata-path encontrado no YAML
        echo              Verifique se aponta para backend\tessdata\
    )
) else (
    echo       [AVISO] tessdata-path nao encontrado no YAML
)

:: ── 3. Java ──────────────────────────────────────────────────────────────────
:CHECK_JAVA
echo.
echo [3/4] Verificando Java...

java -version >"%TEMP%\javacheck.tmp" 2>&1
if !errorlevel! neq 0 (
    echo       [ERRO] Java nao encontrado no PATH.
    echo              Instale o JDK 21: https://adoptium.net/
    set "ERROS=1"
) else (
    set "JVER="
    for /f "tokens=3" %%V in ('type "%TEMP%\javacheck.tmp" ^| findstr /i "version"') do (
        if not defined JVER set "JVER=%%V"
    )
    if defined JVER (
        echo       [OK] Java encontrado: !JVER!
    ) else (
        echo       [OK] Java encontrado ^(versao nao detectada automaticamente^)
    )
)
if exist "%TEMP%\javacheck.tmp" del "%TEMP%\javacheck.tmp" >nul 2>&1

:: ── 4. Maven / Wrapper ───────────────────────────────────────────────────────
echo.
echo [4/4] Verificando Maven...

mvn -version >nul 2>&1
if !errorlevel! equ 0 (
    for /f "tokens=3" %%V in ('mvn -version 2^>^&1 ^| findstr /i "Apache Maven"') do (
        set "MVER=%%V"
    )
    echo       [OK] Maven encontrado: !MVER!
    goto :RESULTADO
)

if exist "%~dp0backend\mvnw.cmd" (
    echo       [OK] Maven Wrapper disponivel: backend\mvnw.cmd
    echo              Para iniciar: cd backend  depois  mvnw.cmd spring-boot:run
) else (
    echo       [AVISO] Maven nao encontrado no PATH e mvnw.cmd nao existe.
    echo              Instale o Maven ou use o IDE para rodar o backend.
)

:: ── Resultado ────────────────────────────────────────────────────────────────
:RESULTADO
echo.
echo ============================================================

if "!ERROS!"=="0" (
    echo   RESULTADO: Configuracao OK - o OCR esta pronto para uso.
    echo.
    echo   Proximo passo: habilitar no YAML ^(se ainda nao fez^):
    echo     Abra: backend\src\main\resources\application-local.yml
    echo     Altere: enabled: false  para  enabled: true
    echo.
    echo   Depois inicie o backend:
    echo     cd backend
    echo     mvn spring-boot:run   ^(ou mvnw.cmd spring-boot:run^)
    echo.
    echo   Log esperado ao importar PDF escaneado:
    echo     [AI Import] PDFBox retornou 0 chars - PDF parece escaneado.
    echo     [AI Import] Acionando OCR ^(Tesseract, idioma=por+eng, dpi=300^)
    echo     [AI Import] OCR concluido: XXXX chars extraidos de N pagina(s^)
) else (
    echo   RESULTADO: Ha problemas a corrigir ^(veja [ERRO] acima^).
    echo.
    echo   Passos recomendados:
    echo     1. Execute SETUP_OCR.bat para baixar os arquivos tessdata
    echo     2. Edite application-local.yml: mude enabled: false para enabled: true
    echo     3. Execute este script novamente para confirmar
)

echo ============================================================
echo.
pause
exit /b !ERROS!
