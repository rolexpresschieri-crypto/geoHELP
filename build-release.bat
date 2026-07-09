@echo off
cd /d "C:\Users\rronc\geoHELP"
if not exist "keystore.properties" (
    echo File keystore.properties non trovato.
    echo 1. Esegui create-keystore.bat per creare la keystore
    echo 2. Copia keystore.properties.example in keystore.properties
    echo 3. In keystore.properties inserisci le password usate e salva
    echo.
    pause
    exit /b 1
)
echo Building APK release...
call gradlew.bat :app:renameReleaseApk
if %ERRORLEVEL% EQU 0 (
    echo.
    echo APK release creato in:
    echo   app\build\outputs\apk\release\app-release-geohelp.apk
    echo.
    explorer "app\build\outputs\apk\release"
) else (
    echo Build fallita.
    pause
)
