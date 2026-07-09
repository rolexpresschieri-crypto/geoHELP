@echo off
setlocal
cd /d "%~dp0"

echo Building prod release APK + AAB (it.geohelp) and copying geoHELP-*-release.* ...
call gradlew.bat assembleProdRelease bundleProdRelease renameReleaseArtifacts
if errorlevel 1 (
    echo.
    echo BUILD FAILED.
    pause
    exit /b 1
)

echo.
echo OK. Output:
echo   app\build\outputs\apk\prod\release\geoHELP-*-release.apk
echo   app\build\outputs\bundle\prodRelease\geoHELP-*-release.aab  ^<-- Play
echo   (not bundle\release\app-release.aab — wrong versionCode)
echo.
pause
