# Carity 음성·Qwen 검증 준비 스크립트 (PC 에서 실행)
#   Android Studio 터미널(D:\02. SSAFY\Carity) 에서:
#   powershell -ExecutionPolicy Bypass -File tools\voice_ai_setup.ps1
# 하는 일
#   1) Ollama 확인(없으면 winget 으로 설치) → 서버 실행 → qwen2.5:3b 받기 → 한국어 응답 테스트
#   2) 에뮬레이터 TTS 엔진 확인 (없으면 앱이 PC 음성으로 대체 재생)
#   3) Windows 한국어 음성 확인 + labeling_server /api/tts 확인 (구버전 서버면 재시작)
#   4) Carity 시뮬레이션(MainActivity) 창 진단 정보 수집
# 결과: tools\out\voice_ai_setup.log
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$root = Split-Path -Parent $PSScriptRoot
$outDir = Join-Path $PSScriptRoot 'out'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$log = Join-Path $outDir 'voice_ai_setup.log'
"" | Out-File $log -Encoding utf8
function Log($m) { $line = "[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $m; Write-Host $line; Add-Content -Path $log -Value $line -Encoding utf8 }

$serverRoot = Join-Path (Split-Path -Parent $PSScriptRoot) 'server\labeling_server'   # Carity\server\labeling_server
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path $adb)) { $adb = 'adb' }

# ---------------------------------------------------------------- 1) Ollama + Qwen
Log '== 1) Ollama / Qwen =='
$ollamaDir = Join-Path $env:LOCALAPPDATA 'Programs\Ollama'
if (Test-Path $ollamaDir) { $env:Path = "$ollamaDir;$env:Path" }
if (-not (Get-Command ollama -ErrorAction SilentlyContinue)) {
    Log 'Ollama 미설치 → winget 으로 설치 (Ollama.Ollama)'
    winget install --id Ollama.Ollama -e --accept-source-agreements --accept-package-agreements 2>&1 | ForEach-Object { Log "  $_" }
    if (Test-Path $ollamaDir) { $env:Path = "$ollamaDir;$env:Path" }
}
if (-not (Get-Command ollama -ErrorAction SilentlyContinue)) {
    Log 'Ollama 를 찾을 수 없습니다. https://ollama.com/download 에서 설치 후 다시 실행하세요.'
} else {
    Log ("ollama: " + (Get-Command ollama).Source)
    $up = $false
    try { Invoke-RestMethod http://127.0.0.1:11434/api/tags -TimeoutSec 3 | Out-Null; $up = $true } catch {}
    if (-not $up) {
        Log 'Ollama 서버 시작 (ollama serve, 127.0.0.1:11434 → 에뮬레이터에서 10.0.2.2:11434)'
        Start-Process -FilePath (Get-Command ollama).Source -ArgumentList 'serve' -WindowStyle Minimized
        for ($i = 0; $i -lt 20 -and -not $up; $i++) { Start-Sleep 1; try { Invoke-RestMethod http://127.0.0.1:11434/api/tags -TimeoutSec 2 | Out-Null; $up = $true } catch {} }
    }
    Log ("Ollama 서버: " + $(if ($up) { '실행 중' } else { '응답 없음' }))
    Log 'qwen2.5:3b 받는 중 (약 1.9GB, 처음 한 번)'
    & (Get-Command ollama).Source pull qwen2.5:3b 2>&1 | Select-Object -Last 3 | ForEach-Object { Log "  $_" }
    $body = @{ model = 'qwen2.5:3b'; temperature = 0.2; stream = $false; messages = @(
        @{ role = 'system'; content = '초보 운전자 음성 코치. JSON 하나만: {"action":"...","speech":"...","reason":"..."}' },
        @{ role = 'user'; content = '{"measurements":{"left_m":"0.25","right_m":"1.10"},"rule":{"action":"KEEP_RIGHT","speech":"왼쪽에 가깝습니다. 핸들을 오른쪽으로 살짝 돌리세요."}}' }) } | ConvertTo-Json -Depth 5
    try {
        $sw = [Diagnostics.Stopwatch]::StartNew()
        $r = Invoke-RestMethod http://127.0.0.1:11434/v1/chat/completions -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 120
        Log ("Qwen 응답 {0}ms: {1}" -f $sw.ElapsedMilliseconds, $r.choices[0].message.content)
    } catch { Log "Qwen 테스트 실패: $($_.Exception.Message)" }
}

# ---------------------------------------------------------------- 2) 에뮬레이터 TTS
Log '== 2) 에뮬레이터 TTS 엔진 =='
& $adb devices 2>&1 | ForEach-Object { Log "  $_" }
Log '  TTS 서비스 목록:'
& $adb shell cmd package query-services -a android.intent.action.TTS_SERVICE 2>&1 | Select-Object -First 20 | ForEach-Object { Log "    $_" }
Log ("  기본 엔진: " + (& $adb shell settings get secure tts_default_synth 2>&1))
& $adb shell pm list packages 2>&1 | Select-String -Pattern 'tts|speech|voice' | ForEach-Object { Log "    $_" }

# ---------------------------------------------------------------- 3) PC 음성 (Windows 한국어) + labeling_server /api/tts
Log '== 3) PC 한국어 음성 / labeling_server =='
try {
    Add-Type -AssemblyName System.Speech
    $s = New-Object System.Speech.Synthesis.SpeechSynthesizer
    $s.GetInstalledVoices() | ForEach-Object { Log ("  음성: {0} [{1}] enabled={2}" -f $_.VoiceInfo.Name, $_.VoiceInfo.Culture.Name, $_.Enabled) }
    $s.Dispose()
} catch { Log "  System.Speech 확인 실패: $($_.Exception.Message)" }
function TestTts {
    try {
        $u = 'http://127.0.0.1:8765/api/tts?text=' + [Uri]::EscapeDataString('왼쪽에 너무 가깝게 붙습니다.')
        $resp = Invoke-WebRequest $u -UseBasicParsing -TimeoutSec 40
        return "OK " + $resp.RawContentLength + " bytes"
    } catch {
        $code = $null; try { $code = [int]$_.Exception.Response.StatusCode } catch {}
        return "FAIL $code $($_.Exception.Message)"
    }
}
$t = TestTts
Log "  /api/tts: $t"
if ($t -like 'FAIL 404*' -or $t -like 'FAIL  *' -or $t -like 'FAIL *연결*' -or $t -like 'FAIL *connect*') {
    Log '  labeling_server 가 구버전이거나 꺼져 있음 → 재시작'
    Get-CimInstance Win32_Process -Filter "Name='python.exe'" | Where-Object { $_.CommandLine -like '*server.py*' -and $_.ExecutablePath -like '*labeling_server*' } |
        ForEach-Object { Log "  종료: PID $($_.ProcessId)"; Stop-Process -Id $_.ProcessId -Force }
    Start-Process -FilePath (Join-Path $serverRoot 'run_server.bat') -WorkingDirectory $serverRoot
    for ($i = 0; $i -lt 30; $i++) { Start-Sleep 2; try { Invoke-RestMethod http://127.0.0.1:8765/api/health -TimeoutSec 2 | Out-Null; break } catch {} }
    Log "  /api/tts (재시작 후): $(TestTts)"
}

# ---------------------------------------------------------------- 4) MainActivity 진단
Log '== 4) Carity MainActivity 진단 =='
& $adb shell dumpsys activity activities 2>&1 | Select-String -Pattern 'pleostest|Display #|mResumedActivity|topResumedActivity' | Select-Object -First 60 | ForEach-Object { Log "  $_" }
& $adb logcat -d -t 3000 2>&1 | Select-String -Pattern 'pleostest|ActivityTaskManager.*(START|Abort|denied|Blocked)|CarLauncher|TaskView|AndroidRuntime' | Select-Object -Last 80 | ForEach-Object { Log "  $_" }
Log '완료. 이 창은 닫아도 됩니다 (Ollama 서버는 계속 실행).'
