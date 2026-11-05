@echo off
cd /d "%~dp0"
if not exist datos\siscom.mv.db (
 echo La base local no existe
 pause
 exit /b 1
)
echo Cierre el servidor con Ctrl C antes de continuar
pause
java -cp "bin\siscom.jar;lib\*" pe.edu.siscom.Inicio --respaldo
pause
