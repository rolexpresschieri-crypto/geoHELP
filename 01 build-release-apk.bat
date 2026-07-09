@echo off
setlocal
cd /d "%~dp0"

if not exist "keystore.properties" (
    echo File keystore.properties non trovato.
    echo 1. Esegui create-keystore.bat per creare la keystore
    echo 2. Copia keystore.properties.example in keystore.properties
    echo 3. In keystore.properties inserisci le password e salva
    echo.
    pause
    exit /b 1
)

echo Building prod release APK (it.geohelp)...
call gradlew.bat renameReleaseApkOnly
if errorlevel 1 (
    echo.
    echo BUILD FAILED.
    pause
    exit /b 1
)

echo.
echo OK. Installa questo APK sul telefono:
echo   app\build\outputs\apk\prod\release\geoHELP-*-release.apk
echo.
echo Per APK + AAB Play usa build-release-geohelp.bat
echo.
explorer "app\build\outputs\apk\prod\release"
pause
