@echo off
setlocal
cd /d "%~dp0"
chcp 65001 >nul

set TARGET_CLASS=%1
if "%TARGET_CLASS%"=="" set TARGET_CLASS=Main

echo [InduTwin] Lancement de %TARGET_CLASS%...
java -cp "lib/*;bin" %TARGET_CLASS% %2 %3 %4 %5
