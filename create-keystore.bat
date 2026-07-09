@echo off
cd /d "C:\Users\rronc\geoHELP"
echo Crea la keystore per firmare l'APK release (una sola volta).
echo Ti verranno chieste: password keystore, nome/cognome, password chiave.
echo.
set KEYSTORE=geohelp-release.keystore
if exist "%KEYSTORE%" (
    echo File %KEYSTORE% esiste gia'. Salvalo e non eliminarlo.
    pause
    exit /b 0
)
keytool -genkey -v -keystore %KEYSTORE% -alias geohelp -keyalg RSA -keysize 2048 -validity 10000
if %ERRORLEVEL% EQU 0 (
    echo.
    echo Keystore creata: %KEYSTORE%
    echo Ora crea il file keystore.properties (copia da keystore.properties.example)
    echo e inserisci le stesse password e: keyAlias=geohelp, storeFile=geohelp-release.keystore
    echo.
)
pause
