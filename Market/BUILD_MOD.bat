@echo off
setlocal enabledelayedexpansion
title Ruasol Market v4.0.5 Final QA - Forge 1.16.5 Build
cd /d "%~dp0"

echo ==================================================
echo  Ruasol Market v4.0.5 Final QA - Forge 1.16.5 / 36.2.39
echo  Gradle 7.6.4 - Windows tek tik build paketi
echo ==================================================
echo.

if exist BUILD_LOG.txt del BUILD_LOG.txt

where java >nul 2>nul
if errorlevel 1 (
  echo [HATA] Java bulunamadi. Java 17 kurulu olmali. >> BUILD_LOG.txt
  type BUILD_LOG.txt
  pause
  exit /b 1
)

for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set JAVA_VER=%%v
echo Java: %JAVA_VER%

echo Gradle 7.6.4 hazirlaniyor...
set GRADLE_DIR=%CD%\.gradle-local\gradle-7.6.4
set GRADLE_ZIP=%CD%\.gradle-local\gradle-7.6.4-bin.zip
if not exist "%GRADLE_DIR%\bin\gradle.bat" (
  mkdir "%CD%\.gradle-local" >nul 2>nul
  echo Gradle 7.6.4 indiriliyor. Ilk build icin internet gerekebilir...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-7.6.4-bin.zip' -OutFile '%GRADLE_ZIP%'" >> BUILD_LOG.txt 2>&1
  if errorlevel 1 goto buildfail
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%GRADLE_ZIP%' -DestinationPath '%CD%\.gradle-local' -Force" >> BUILD_LOG.txt 2>&1
  if errorlevel 1 goto buildfail
)

echo Build basliyor...
"%GRADLE_DIR%\bin\gradle.bat" --no-daemon clean build >> BUILD_LOG.txt 2>&1
if errorlevel 1 goto buildfail

echo.
echo [OK] Build tamamlandi.
echo Jar dosyasi: build\libs\ruasol-market-forge-1.16.5-4.0.5.jar
echo.
pause
exit /b 0

:buildfail
echo.
echo [HATA] Build basarisiz oldu. Detaylar BUILD_LOG.txt icinde.
echo En yaygin sebepler: internet yok, Java 17 yok, Forge/Gradle depolarina erisim yok veya dependency cache eksik.
echo.
type BUILD_LOG.txt
pause
exit /b 1
