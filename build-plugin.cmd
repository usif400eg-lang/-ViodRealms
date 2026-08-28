@echo off
setlocal EnableExtensions EnableDelayedExpansion
title VoxelPanel - Plugin Builder

set "PROJECT_DIR=%~dp0"
set "OUTPUT_DIR=%PROJECT_DIR%creat plagin"
set "TARGET_DIR=%PROJECT_DIR%target"
set "TOOLS_DIR=%PROJECT_DIR%.tools"
set "MAVEN_VERSION=3.9.9"
set "LOCAL_MAVEN=%TOOLS_DIR%\apache-maven-%MAVEN_VERSION%\bin\mvn.cmd"
set "MAVEN_REPOSITORY=-Dmaven.repo.local=%PROJECT_DIR%.maven-repository"

echo.
echo ============================================
echo   Building VoxelPanel plugin...
echo ============================================
echo.

if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"

where mvn >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    call mvn "%MAVEN_REPOSITORY%" clean package -DskipTests
) else if exist "%PROJECT_DIR%mvnw.cmd" (
    call "%PROJECT_DIR%mvnw.cmd" "%MAVEN_REPOSITORY%" clean package -DskipTests
) else (
    if not exist "%LOCAL_MAVEN%" (
        echo Maven was not found. Downloading a local build tool one time...
        if not exist "%TOOLS_DIR%" mkdir "%TOOLS_DIR%"
        powershell -NoProfile -ExecutionPolicy Bypass -Command "$zip=Join-Path '%TOOLS_DIR%' 'maven.zip'; Invoke-WebRequest -UseBasicParsing 'https://archive.apache.org/dist/maven/maven-3/%MAVEN_VERSION%/binaries/apache-maven-%MAVEN_VERSION%-bin.zip' -OutFile $zip; Expand-Archive -LiteralPath $zip -DestinationPath '%TOOLS_DIR%' -Force; Remove-Item -LiteralPath $zip -Force"
        if errorlevel 1 (
            echo.
            echo ERROR: Maven could not be downloaded. Check your internet connection and run this file again.
            pause
            exit /b 1
        )
    )
    if not exist "%LOCAL_MAVEN%" (
        echo ERROR: The local Maven installation is incomplete.
        pause
        exit /b 1
    )
    call "%LOCAL_MAVEN%" "%MAVEN_REPOSITORY%" clean package -DskipTests
)

if errorlevel 1 (
    echo.
    echo Build failed. No plugin file was copied.
    pause
    exit /b 1
)

set "PLUGIN_JAR="
for %%F in ("%TARGET_DIR%\VoxelPanel-*.jar") do (
    if /I not "%%~nxF"=="original-VoxelPanel-1.0.0.jar" set "PLUGIN_JAR=%%~fF"
)

if not defined PLUGIN_JAR (
    echo.
    echo ERROR: The built plugin JAR was not found in target.
    pause
    exit /b 1
)

copy /Y "%PLUGIN_JAR%" "%OUTPUT_DIR%\" >nul
if errorlevel 1 (
    echo ERROR: Could not copy the plugin JAR.
    pause
    exit /b 1
)

echo.
echo Success! New plugin created here:
echo %OUTPUT_DIR%
echo.
pause
