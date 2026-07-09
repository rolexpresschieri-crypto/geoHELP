@echo off
setlocal
cd /d "%~dp0"

echo Building geoHELP Dev (flavor dev, debug)...
call gradlew.bat :app:assembleDevDebug
if errorlevel 1 (
    echo.
    echo BUILD FAILED.
    pause
    exit /b 1
)

echo.
echo OK. APK in:
echo   app\build\outputs\apk\dev\debug\app-dev-debug_*.apk
echo   package: it.geohelp.dev
echo.
explorer "app\build\outputs\apk\dev\debug"
pause
