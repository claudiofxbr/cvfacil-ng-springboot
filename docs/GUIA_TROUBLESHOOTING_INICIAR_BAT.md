# Guia de Troubleshooting — INICIAR.bat (CVFacil.NG)

## 1. Diagnóstico: Causas comuns de falha em arquivos .bat

### 1.1 Mapa de problemas por sintoma

| Sintoma | Causa mais provável | Seção |
|---|---|---|
| Janela fecha imediatamente | Erro antes do `pause` / `exit /b` prematuro | 2.1 |
| "Java não encontrado" | Java não instalado ou não está no PATH | 3.1 |
| "Node.js não encontrado" | Node não instalado ou reinicialização pendente | 3.2 |
| "JAR não encontrado" | Backend nunca foi compilado | 3.3 |
| Backend não responde em 90s | Porta bloqueada / Java errado / crash interno | 3.4 |
| Erro de permissão no `taskkill` | Falta de privilégios de administrador | 3.5 |
| curl retorna erro mas backend está rodando | curl não instalado ou versão incompatível | 3.6 |
| Antivírus bloqueia o JAR | Falso positivo de segurança | 3.7 |

---

## 2. Bugs identificados e corrigidos no INICIAR.bat original

### Bug #1 — Quoting quebrado no `start cmd /k` com caminhos com espaços
**Severidade:** Alta — impede inicialização se o projeto estiver em `C:\Meus Projetos\`

```bat
:: CÓDIGO ORIGINAL (quebrado)
start "Título" /D "%BACKEND_DIR%" cmd /k ^
    "java -jar "%JAR%" --spring.profiles.active=local"
::            ↑ fecha a string do /k aqui — o resto é interpretado como argumentos

:: CÓDIGO CORRIGIDO
start "Título" /D "%BACKEND_DIR%" cmd /k ^
    "%JAVA_CMD% ""%JAR%"" %JAVA_ARGS%"
::                  ↑↑ dupla aspa = aspas literais dentro da string cmd /k
```

### Bug #2 — BACKEND_LOG declarado mas nunca preenchido
**Severidade:** Alta — mensagem de "consulte o log" enganosa; arquivo estava sempre vazio

```bat
:: ORIGINAL: backend rodava em janela separada sem redirecionar saída
start "Backend" cmd /k "java -jar app.jar"
:: O arquivo BACKEND_LOG nunca era criado/escrito

:: CORRIGIDO: redireciona stdout+stderr para arquivo E exibe na janela
start "Backend" cmd /k "java -jar ""app.jar"" > ""backend.log"" 2>&1 & type ""backend.log"""
```

### Bug #3 — Sem verificação de versão do Java
**Severidade:** Alta — Java 8 ou 11 causa `UnsupportedClassVersionError` no Spring Boot 3

```bat
:: ORIGINAL: apenas verificava existência
where java >nul 2>&1

:: CORRIGIDO: extrai versão principal e compara
for /f "tokens=3" %%V in ('java -version 2^>^&1 ^| findstr /i "version"') do set "JVER=%%V"
set "JVER=%JVER:"=%"
for /f "tokens=1 delims=." %%M in ("%JVER%") do set "JMAJOR=%%M"
if %JMAJOR% LSS 17 (
    echo [ERRO] Java %JVER% insuficiente. Requer Java 17+.
)
```

### Bug #4 — curl usado sem verificar disponibilidade
**Severidade:** Alta — em Windows antigos ou corporativos, curl pode não existir. Resultado: healthcheck sempre falha, script aguarda 120 segundos e termina com erro enganoso.

```bat
:: CORRIGIDO: detecta curl; fallback para PowerShell; fallback para espera fixa
where curl >nul 2>&1
if not errorlevel 1 set "USE_CURL=1"
:: se não tiver curl, usa PowerShell Invoke-WebRequest
```

### Bug #5 — Barra de progresso empilhava linhas
**Severidade:** Baixa — visual ruim, scroll desnecessário

```bat
:: ORIGINAL: cada iteração adicionava nova linha
echo      [##........] 15%%

:: CORRIGIDO: carriage return (\r) sobrescreve a mesma linha
<NUL set /p "=     [##........] 15%%"^M
```

### Bug #6 — `taskkill` falha silenciosamente sem administrador
**Severidade:** Média — porta 8080 permanece ocupada, backend não sobe

```bat
:: ORIGINAL: erro suprimido
taskkill /PID %%P /F >nul 2>&1

:: CORRIGIDO: verifica errorlevel e avisa o usuário
taskkill /PID %%P /F >nul 2>&1
if errorlevel 1 (
    echo [AVISO] Não foi possível encerrar PID %%P.
    echo         Execute como Administrador se necessário.
)
```

### Bug #7 — Sem verificação de administrador
**Severidade:** Média — operações de rede e processo exigem elevação

```bat
:: CORRIGIDO: detecta elevação via net session
net session >nul 2>&1
if errorlevel 1 echo [AVISO] Sem privilégios de administrador.
```

### Bug #8 — Inconsistência no timeout (mensagem diz 60s, loop aguarda 120s)
**Severidade:** Baixa — confusão para o usuário

```bat
:: ORIGINAL: MAX=60 iterações × 2s = 120 segundos
:: Mensagem dizia "60 segundos" — incorreto

:: CORRIGIDO: MAX=45 iterações × 2s = 90 segundos, mensagem consistente
set /a "MAX_TENT=45"
echo [..] Aguardando o backend iniciar (ate 90 segundos)...
```

### Bug #9 — Sem verificação de integridade do JAR
**Severidade:** Média — JAR corrompido gera `java.util.zip.ZipException`, não "arquivo não encontrado"

```bat
:: CORRIGIDO: verifica tamanho mínimo esperado (> 1MB)
for %%F in ("%JAR%") do set "JAR_SIZE=%%~zF"
if %JAR_SIZE% LSS 1048576 (
    echo [AVISO] JAR suspeito — tamanho menor que 1MB. Recompile.
)
```

### Bug #10 — `npm install` sem confirmação de diretório ativo
**Severidade:** Baixa — se `pushd` falhar, `npm install` roda no diretório errado

```bat
:: CORRIGIDO: verifica se pushd funcionou antes de rodar npm
pushd "%FRONTEND_DIR%"
if not "%CD%"=="%FRONTEND_DIR%" (
    echo [ERRO] Não foi possível acessar %FRONTEND_DIR%
    popd & exit /b 1
)
```

### Bug #11 — Sem suporte a modo debug/diagnóstico
**Severidade:** Média — impossível ver o que o backend está imprimindo sem abrir a janela manualmente

```bat
:: CORRIGIDO: argumento --debug habilita log verbose
INICIAR.bat --debug     → exibe stdout/stderr do backend em tempo real
```

### Bug #12 — Sem log de execução do próprio script
**Severidade:** Baixa — impossível diagnosticar falhas intermitentes

```bat
:: CORRIGIDO: cada etapa registrada em .runtime\logs\iniciar.log
echo [%TS%] OK: Java %JAVA_VER% >> "%INICIAR_LOG%"
```

---

## 3. Guia de troubleshooting passo a passo

### 3.1 Java não encontrado

**Passo 1** — Verifique se o Java está instalado:
```bat
:: Abra o Prompt de Comando (Win+R → cmd) e digite:
java -version
```

**Passo 2** — Se aparecer "não é reconhecido como comando interno": Java não está no PATH.

**Passo 3** — Verifique se o Java está instalado mas não no PATH:
```bat
dir "C:\Program Files\Eclipse Adoptium\" /B
dir "C:\Program Files\Java\" /B
```

**Passo 4** — Se encontrado, adicione ao PATH manualmente:
```bat
:: Substitua pela pasta correta do seu Java 21
setx JAVA_HOME "C:\Program Files\Eclipse Adoptium\jdk-21.0.3.9-hotspot"
setx PATH "%JAVA_HOME%\bin;%PATH%"
:: Feche e reabra o Prompt de Comando depois
```

**Passo 5** — Reinstale se necessário: https://adoptium.net/temurin/releases/?version=21

---

### 3.2 Node.js não encontrado

```bat
node -v    :: deve retornar v20.x.x ou superior
npm -v     :: deve retornar 10.x.x ou superior
```

Se não funcionar após instalar: **reinicie o computador** (o PATH do Windows só é atualizado na sessão nova).

---

### 3.3 JAR não encontrado

O JAR precisa ser compilado uma vez antes de iniciar. Execute:

```bat
COMPILAR.bat
```

Se o COMPILAR.bat também falhar, verifique se o Maven funciona:
```bat
cd backend
mvnw.cmd --version
mvnw.cmd package -DskipTests -Dspotless.skip=true
```

---

### 3.4 Backend não responde (timeout de 90 segundos)

**Passo 1** — Execute com modo debug para ver o erro exato:
```bat
INICIAR.bat --debug
```

**Passo 2** — Leia o log do backend:
```bat
type .runtime\logs\backend.log
```

Erros comuns e soluções:

| Erro no log | Causa | Solução |
|---|---|---|
| `UnsupportedClassVersionError` | Java muito antigo | Instalar Java 21 |
| `Address already in use: 8080` | Porta ocupada | Execute como admin ou encerre o processo |
| `ZipException: error in opening zip file` | JAR corrompido | Recompilar com COMPILAR.bat |
| `NoSuchMethodError` | Dependência incorreta | `mvnw.cmd clean package -DskipTests` |
| `Unable to create JwtDecoder` | Configuração de produção sem chave | Verificar se profile=local está ativo |
| `Could not find class 'io.zonky...'` | Binário do Postgres embarcado ausente | Recompilar no SO correto |

**Passo 3** — Verifique se a porta 8080 está livre:
```bat
netstat -ano | findstr ":8080"
:: Se aparecer LISTENING, o processo com aquele PID está bloqueando
tasklist /FI "PID eq <PID_AQUI>"
```

---

### 3.5 Erro de permissão / taskkill falha

Sintoma: `[AVISO] Não foi possível encerrar PID XXX na porta 8080`

**Solução A (recomendada):** Execute o INICIAR.bat como administrador:
1. Clique com botão direito em `INICIAR.bat`
2. Selecione **"Executar como administrador"**

**Solução B:** Encerre o processo manualmente:
```bat
:: Descobrir qual processo usa a porta 8080
netstat -ano | findstr ":8080"
:: Anota o PID da última coluna, então:
taskkill /PID <numero_do_PID> /F
```

**Solução C:** Reiniciar o computador para liberar todas as portas.

---

### 3.6 curl não funciona / healthcheck sempre falha

**Verifique:**
```bat
where curl
curl --version
```

No Windows 10 1803+ o `curl.exe` vem em `C:\Windows\System32\curl.exe`. Se não existir:

**Opção A:** Instale o curl: https://curl.se/windows/

**Opção B:** O INICIAR.bat corrigido já usa PowerShell automaticamente como fallback — não é necessário instalar o curl.

**Teste do PowerShell:**
```powershell
Invoke-WebRequest http://localhost:8080/api/health -UseBasicParsing
```

---

### 3.7 Antivírus / Windows Defender bloqueando

Sintomas:
- O backend desaparece segundos após iniciar
- Log mostra iniciando mas depois some
- Windows Defender exibe alerta

**Passo 1** — Verifique o Central de Segurança do Windows:
`Win + I → Privacidade e Segurança → Segurança do Windows → Proteção contra vírus e ameaças → Histórico de proteção`

**Passo 2** — Se o JAR foi quarentenado, restaure-o e adicione uma exclusão:
`Proteção contra vírus e ameaças → Gerenciar configurações → Exclusões → Adicionar exclusão → Pasta`
Adicione a pasta completa do projeto CVFacil.NG.

**Passo 3** — Verifique se o SmartScreen bloqueou o arquivo `.bat`:
Clique com botão direito em `INICIAR.bat → Propriedades → Desbloquear → OK`

**Passo 4** — Firewall bloqueando porta 8080:
```bat
:: Execute como administrador:
netsh advfirewall firewall add rule name="CVFacil Backend" ^
  dir=in action=allow protocol=TCP localport=8080
netsh advfirewall firewall add rule name="CVFacil Frontend" ^
  dir=in action=allow protocol=TCP localport=3000
```

---

### 3.8 Como adicionar `pause` para ver o erro (depuração manual)

Se a janela fechar antes de você ler a mensagem, edite temporariamente o INICIAR.bat:

```bat
:: Abra o INICIAR.bat com o Bloco de Notas.
:: Procure por:   exit /b 1
:: Adicione ANTES de cada um:
pause

:: Exemplo:
pause
exit /b 1
```

Ou adicione `echo` intermediários para rastrear onde para:
```bat
echo [DEBUG] Chegou na etapa X
:: seu código
echo [DEBUG] Passou pela etapa X com errorlevel %errorlevel%
```

Para depurar linha a linha, adicione no início do script:
```bat
@echo on    :: em vez de @echo off — exibe cada comando executado
```

---

## 4. Scripts disponíveis

| Script | Uso |
|---|---|
| `INICIAR.bat` | Inicia o aplicativo normalmente |
| `INICIAR.bat --debug` | Inicia com log detalhado do backend |
| `INICIAR.bat --limpar` | Remove e reinstala node_modules antes de iniciar |
| `COMPILAR.bat` | Compila o backend (necessário apenas uma vez) |
| `DIAGNOSTICO.bat` | Verifica todos os pré-requisitos e gera relatório |

---

## 5. Checklist de verificação rápida

Execute esta sequência em caso de falha:

```bat
:: 1. Verificar Java
java -version

:: 2. Verificar Node
node -v && npm -v

:: 3. Verificar JAR
dir backend\target\*.jar

:: 4. Verificar portas
netstat -ano | findstr ":8080 \|:3000 "

:: 5. Executar diagnóstico completo
DIAGNOSTICO.bat

:: 6. Tentar iniciar com debug
INICIAR.bat --debug
```

Se todos os itens acima estiverem OK e o problema persistir, envie o arquivo `.runtime\logs\diagnostico_*.log` para o suporte.
