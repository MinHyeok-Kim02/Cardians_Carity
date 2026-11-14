@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo [1/4] Python venv (.venv)
if not exist ".venv\Scripts\python.exe" (
  py -3 -m venv .venv 2>nul || python -m venv .venv
)
if not exist ".venv\Scripts\python.exe" (
  echo Python 3.10+ not found. Install Python and retry.
  pause
  exit /b 1
)
echo [2/4] pip upgrade
".venv\Scripts\python.exe" -m pip install --upgrade pip
echo [3/4] PyTorch CUDA 12.8 build (works on CPU too if no NVIDIA GPU)
".venv\Scripts\python.exe" -m pip install torch torchvision --index-url https://download.pytorch.org/whl/cu128
echo [4/4] ultralytics, fastapi, ffmpeg ...
".venv\Scripts\python.exe" -m pip install -r requirements.txt
echo.
".venv\Scripts\python.exe" -c "import torch;print(\"torch\",torch.__version__,\"CUDA available:\",torch.cuda.is_available())"
echo Setup done. Run run_server.bat
pause
