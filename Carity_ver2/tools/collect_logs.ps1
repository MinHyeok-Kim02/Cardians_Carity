# Carity 음성·AI·라벨 로그 수집 → tools\out\carity_logs.txt
#   powershell -ExecutionPolicy Bypass -File tools\collect_logs.ps1
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path $adb)) { $adb = 'adb' }
$outDir = Join-Path $PSScriptRoot 'out'; New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$out = Join-Path $outDir 'carity_logs.txt'
$lines = & $adb logcat -d -v time 2>&1 | Select-String -Pattern 'CarityVoice|CarityAI|CarityLabel|AndroidRuntime|pleostest|tts' | ForEach-Object { $_.Line }
$lines | Select-Object -Last 400 | Out-File $out -Encoding utf8
& $adb shell dumpsys activity activities 2>&1 | Select-String -Pattern 'pleostest|topResumedActivity' | Select-Object -First 20 | ForEach-Object { $_.Line } | Out-File $out -Append -Encoding utf8
Write-Host "저장: $out ($($lines.Count) 줄)"
