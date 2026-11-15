@echo off
rem Carity launcher - double-click for menu, or: Carity.bat start ^| setup ^| build ^| server ^| status ^| logs ^| copydata ^| stop ^| stopall ^| diag ^| reset
chcp 65001 >nul
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0carity.ps1" %*
echo.
pause
