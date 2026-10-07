@echo off
rem Doble clic: baja lo último de GitHub (rama main) y abre el juego.
rem Necesita Git y Java 21. Para jugar sin nada instalado, usa el zip (README, «Empaquetar»).
chcp 65001 >nul
setlocal

rem «git pull» puede cambiar este mismo archivo mientras se ejecuta, y Windows lee los .bat
rem a trozos: se copia a la carpeta temporal y sigue desde la copia, con la carpeta del juego.
if not "%~1"=="" goto start
copy /y "%~f0" "%TEMP%\java-wave-defense-jugar.bat" >nul
"%TEMP%\java-wave-defense-jugar.bat" "%~dp0."

:start
cd /d "%~1"
title Java Wave Defense

where git >nul 2>nul
if errorlevel 1 goto nogit

echo Buscando novedades en GitHub...
git switch main >nul 2>nul
git pull --ff-only
if errorlevel 1 goto nopull
goto java

:nogit
echo No encuentro Git: se juega con la versión que ya tienes.
echo Para que se actualice solo, instala Git desde https://git-scm.com
goto java

:nopull
echo.
echo No se ha podido actualizar: sin internet, o hay cambios tuyos en la carpeta.
echo Se juega con la versión que ya tienes.

:java
if defined JAVA_HOME goto run
where java >nul 2>nul
if errorlevel 1 goto nojava

:run
echo.
echo Abriendo el juego. La primera vez tarda un par de minutos en compilar...
call gradlew.bat -q :lwjgl3:run
if errorlevel 1 goto failed
exit /b 0

:nojava
echo.
echo No encuentro Java. Instala el JDK 21 de https://adoptium.net
echo marcando la opción «Set JAVA_HOME variable» y vuelve a probar.
pause
exit /b 1

:failed
echo.
echo Algo ha fallado. Copia lo que pone arriba y pásaselo a Claude.
pause
exit /b 1
