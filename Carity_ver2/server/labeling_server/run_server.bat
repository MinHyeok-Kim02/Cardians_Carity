@echo off
chcp 65001 >nul
cd /d "%~dp0"
if not exist ".venv\Scripts\python.exe" (
  echo Run setup_server.bat first.
  pause
  exit /b 1
)
set PYTHONIOENCODING=utf-8
set PYTHONUTF8=1
rem root = parent folder of labeling_server. Extra options: --force-cpu --port 8765 --sample-fps 10
".venv\Scripts\python.exe" server.py %*
pause
