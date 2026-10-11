@echo off
setlocal
cd /d "%~dp0"
chcp 65001 >nul

if not exist "bin" mkdir "bin"

powershell -NoProfile -Command "Set-Content '%TEMP%\indutwin_sources.txt' (Get-ChildItem -Path src -Recurse -Filter *.java | Resolve-Path -Relative) -Encoding Ascii; javac -encoding UTF-8 -cp 'lib/*;bin' -d bin '@%TEMP%\indutwin_sources.txt'; $ec = $LASTEXITCODE; Remove-Item '%TEMP%\indutwin_sources.txt' -ErrorAction SilentlyContinue; exit $ec"

if %ERRORLEVEL% equ 0 (
    echo [InduTwin] Compilation reussie avec succes dans bin/
) else (
    echo [InduTwin] Erreur de compilation : code %ERRORLEVEL%
)
exit /b %ERRORLEVEL%
