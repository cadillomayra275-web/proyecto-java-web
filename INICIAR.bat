@echo off
cd /d "%~dp0"
where java >nul 2>nul
if errorlevel 1 (
 echo Instale Java 8 o superior y vuelva a ejecutar
 pause
 exit /b 1
)
start "SISCOM navegador" /b powershell -NoProfile -ExecutionPolicy Bypass -File scripts\abrir_navegador.ps1
java -cp "bin\siscom.jar;lib\*" pe.edu.siscom.Inicio
pause
