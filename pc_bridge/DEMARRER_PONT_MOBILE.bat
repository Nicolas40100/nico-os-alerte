@echo off
chcp 65001 >nul
cd /d "%~dp0"
title Nico Alert - Pont local Nico OS

echo.
echo ================================================
echo   NICO ALERT ^<^-> NICO OS LOCAL
echo ================================================
echo.

where py >nul 2>&1
if %errorlevel%==0 (
  py -3 mobile_bridge.py
) else (
  python mobile_bridge.py
)

echo.
echo Le pont mobile s'est arrete.
pause
