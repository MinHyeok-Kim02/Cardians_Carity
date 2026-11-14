@echo off
rem Carity ver1 launcher - double-click for menu, or: Carity.bat start ^| setup ^| build ^| server ^| status ^| logs ^| copydata ^| stop
chcp 65001 >nul
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0carity.ps1" %*
echo.
pause
