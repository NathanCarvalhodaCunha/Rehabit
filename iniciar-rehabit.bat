@echo off
setlocal
cd /d "%~dp0"

echo ============================================
echo   Rehabit - iniciando backend + site
echo ============================================
echo.

REM 1) Confere se ha um JDK (nao so um JRE) disponivel no PATH.
REM    Precisa do JDK para compilar (javac), nao so para rodar.
where java >nul 2>&1
if errorlevel 1 goto nojava
where javac >nul 2>&1
if errorlevel 1 goto nojava
goto javaok

:nojava
echo ERRO: nao encontrei um JDK (Java 17 ou superior) instalado nesta maquina.
echo.
echo Baixe e instale o JDK 17 gratuito em:
echo   https://adoptium.net/temurin/releases/?version=17
echo.
echo Depois de instalar, feche esta janela e rode o iniciar-rehabit.bat de novo.
pause
exit /b 1

:javaok
echo Java encontrado.
echo.

REM 2) Compila o backend (Maven Wrapper - nao precisa de IDE nem Maven instalado)
echo Compilando o backend (pode demorar um pouco na primeira vez)...
call "%~dp0rehabit-api\rehabit-api\mvnw.cmd" -q -f "%~dp0rehabit-api\rehabit-api\pom.xml" package -DskipTests
if errorlevel 1 (
    echo.
    echo ERRO ao compilar o backend. Veja as mensagens acima.
    pause
    exit /b 1
)
echo.

REM 3) Roda o backend (jar ja compilado, com banco de dados embutido) em uma janela separada
echo Iniciando o backend (Spring Boot)...
start "Rehabit - Backend" cmd /k "cd /d "%~dp0rehabit-api\rehabit-api" && java -jar target\rehabit-api-1.0.0.jar"

echo Aguardando o backend responder na porta 8080...
:waitbackend
timeout /t 2 /nobreak >nul
powershell -NoProfile -Command "if (Test-NetConnection -ComputerName localhost -Port 8080 -InformationLevel Quiet -WarningAction SilentlyContinue) { exit 0 } else { exit 1 }" >nul 2>&1
if errorlevel 1 goto waitbackend

echo Backend no ar!
echo.

REM 4) Abre a tela de login no navegador padrao
start "" "%~dp0Login\login.html"

echo ============================================
echo Tudo pronto!
echo O backend continua rodando na janela "Rehabit - Backend".
echo Feche aquela janela quando quiser desligar o servidor.
echo ============================================
endlocal
