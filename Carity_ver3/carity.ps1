<#
  Carity ver1 통합 실행기
  ─────────────────────────────────────────────
  Carity.bat 를 더블클릭하면 메뉴가 뜹니다. 또는:
    Carity.bat start     전체 시작 (Ollama·Qwen → 라벨링 서버 → 에뮬레이터 → 앱 설치·권한)
    Carity.bat setup     처음 1회 설치 (Python 서버 환경, Ollama + qwen2.5:3b, .env)
    Carity.bat build     안드로이드 앱 2개 빌드·설치 (MOVO 시뮬레이션+튜토리얼, 가디언즈 라벨 뷰어)
    Carity.bat server    라벨링 서버만 실행
    Carity.bat status    상태 점검
    Carity.bat logs      앱 로그 수집 → logs\
    Carity.bat copydata  영상·라벨 데이터를 Carity_ver1\data 로 복사
    Carity.bat stop      라벨링 서버 종료
  설정: Carity_ver1\.env (없으면 .env.example 로 자동 생성)
#>
param([string]$Action = '')
$ErrorActionPreference = 'Continue'
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch {}
$Root = $PSScriptRoot
$Android = Join-Path $Root 'android'
$ServerDir = Join-Path $Root 'server\labeling_server'
$LogDir = Join-Path $Root 'logs'
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
$RunLog = Join-Path $LogDir 'carity.log'
$Pkgs = @('com.example.pleostest', 'com.guardians.viewer')

function Log([string]$m, [string]$color = 'Gray') {
    $line = "[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $m
    Write-Host $line -ForegroundColor $color
    Add-Content -Path $RunLog -Value $line -Encoding utf8
}
function Ok($m) { Log "  OK  $m" 'Green' }
function Warn($m) { Log "  !!  $m" 'Yellow' }
function Head($m) { Log "== $m ==" 'Cyan' }

# ---------------------------------------------------------------- 설정 (.env)
function Read-Env {
    $envFile = Join-Path $Root '.env'
    if (-not (Test-Path $envFile)) { Copy-Item (Join-Path $Root '.env.example') $envFile; Log '.env 생성 (.env.example 복사)' }
    $cfg = @{}
    foreach ($line in Get-Content $envFile -Encoding UTF8) {
        $t = $line.Trim().TrimStart([char]0xFEFF)
        if ($t -eq '' -or $t.StartsWith('#') -or -not $t.Contains('=')) { continue }
        $k = $t.Substring(0, $t.IndexOf('=')).Trim(); $v = $t.Substring($t.IndexOf('=') + 1).Trim().Trim('"').Trim("'")
        $cfg[$k] = $v
    }
    return $cfg
}
$Cfg = Read-Env
function Cfg([string]$k, [string]$d = '') { if ($Cfg.ContainsKey($k) -and $Cfg[$k] -ne '') { return $Cfg[$k] } return $d }

$DataRoot = Cfg 'DATA_ROOT' 'D:\02. SSAFY\Hyundai Test'
if (-not [IO.Path]::IsPathRooted($DataRoot)) { $DataRoot = Join-Path $Root $DataRoot }
$Port = [int](Cfg 'SERVER_PORT' '8765')
$QwenModel = Cfg 'QWEN_MODEL' 'qwen2.5:3b'
$ServerArgs = Cfg 'SERVER_ARGS' ''

$Sdk = Cfg 'ANDROID_SDK' (Join-Path $env:LOCALAPPDATA 'Android\Sdk')
$Adb = Join-Path $Sdk 'platform-tools\adb.exe'; if (-not (Test-Path $Adb)) { $Adb = 'adb' }
$Emu = Join-Path $Sdk 'emulator\emulator.exe'
$Py = Join-Path $ServerDir '.venv\Scripts\python.exe'

function Adb { & $Adb @args 2>&1 }
function Http-Ok([string]$url, [int]$timeout = 3) { try { Invoke-WebRequest $url -UseBasicParsing -TimeoutSec $timeout | Out-Null; return $true } catch { return $false } }

# ---------------------------------------------------------------- Ollama / Qwen
function Find-Ollama {
    $dir = Join-Path $env:LOCALAPPDATA 'Programs\Ollama'
    if (Test-Path $dir) { $env:Path = "$dir;$env:Path" }
    $c = Get-Command ollama -ErrorAction SilentlyContinue
    if ($c) { return $c.Source } return $null
}
function Ensure-Ollama([bool]$install) {
    Head "Qwen (Ollama, $QwenModel)"
    $o = Find-Ollama
    if (-not $o -and $install) {
        Log '  Ollama 설치 (winget Ollama.Ollama)'
        winget install --id Ollama.Ollama -e --accept-source-agreements --accept-package-agreements
        $o = Find-Ollama
    }
    if (-not $o) { Warn 'Ollama 없음 → AI 판단은 규칙 엔진 문장으로 대체됩니다. (Carity.bat setup 으로 설치)'; return }
    if (-not (Http-Ok 'http://127.0.0.1:11434/api/tags')) {
        Start-Process -FilePath $o -ArgumentList 'serve' -WindowStyle Minimized
        for ($i = 0; ($i -lt 20) -and -not (Http-Ok 'http://127.0.0.1:11434/api/tags' 2); $i++) { Start-Sleep 1 }
    }
    if (-not (Http-Ok 'http://127.0.0.1:11434/api/tags')) { Warn 'Ollama 서버 응답 없음'; return }
    $tags = (Invoke-RestMethod 'http://127.0.0.1:11434/api/tags' -TimeoutSec 5).models | ForEach-Object { $_.name }
    if ($tags -notcontains $QwenModel) { Log "  $QwenModel 받는 중 (처음 1회, 약 1.9GB)"; & $o pull $QwenModel }
    # 모델을 미리 메모리에 올려 두고 60분 유지 (첫 판단이 15초 넘게 걸리는 것 방지)
    try {
        $warm = @{ model = $QwenModel; prompt = ''; keep_alive = '60m' } | ConvertTo-Json
        Invoke-RestMethod 'http://127.0.0.1:11434/api/generate' -Method Post -ContentType 'application/json' -Body $warm -TimeoutSec 120 | Out-Null
        Ok "Qwen 모델 미리 적재 완료 (60분 유지)"
    } catch { Warn "Qwen 모델 미리 적재 실패: $($_.Exception.Message)" }
    Ok "Ollama 실행 중 · 모델 $QwenModel (에뮬레이터에서 http://10.0.2.2:11434)"
}

# ---------------------------------------------------------------- 라벨링 서버
function Ensure-ServerEnv([bool]$install) {
    if (Test-Path $Py) { return $true }
    $old = Join-Path (Cfg 'LEGACY_SERVER' 'D:\02. SSAFY\Hyundai Test\labeling_server') '.venv'
    if (Test-Path (Join-Path $old 'Scripts\python.exe')) {
        Log "  기존 Python 환경 복사: $old  (약 5GB, 3~5분 걸립니다. 창을 닫지 마세요)" 'Yellow'
        $sw = [Diagnostics.Stopwatch]::StartNew()
        robocopy $old (Join-Path $ServerDir '.venv') /E /NFL /NDL /NJH /NJS /NP /R:1 /W:1 | Out-Null
        Log ("  복사 끝 ({0:N0}초)" -f $sw.Elapsed.TotalSeconds)
        if ((Test-Path $Py) -and ((& $Py -c "import ultralytics, fastapi, uvicorn; print('ok')" 2>$null) -eq 'ok')) { Ok '서버 Python 환경 준비 (복사)'; return $true }
        Warn '복사한 환경이 동작하지 않아 새로 설치합니다'
        Remove-Item (Join-Path $ServerDir '.venv') -Recurse -Force -ErrorAction SilentlyContinue
    }
    if (-not $install) { Warn '서버 Python 환경 없음 → Carity.bat setup 실행'; return $false }
    Log '  Python 가상환경 생성 + torch(CUDA 12.8) + requirements 설치 (수 분)'
    Push-Location $ServerDir
    & py -3 -m venv .venv 2>$null | Out-Host; if (-not (Test-Path $Py)) { & python -m venv .venv | Out-Host }
    & $Py -m pip install --upgrade pip | Out-Host
    & $Py -m pip install torch torchvision --index-url https://download.pytorch.org/whl/cu128 | Out-Host
    & $Py -m pip install -r requirements.txt | Out-Host
    Pop-Location
    return (Test-Path $Py)
}
function Server-Procs {
    # 포트를 잡고 있는 프로세스를 우선 찾고, 없으면 labeling_server 의 python 을 찾음
    $ids = @()
    try { $ids = @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop | Select-Object -ExpandProperty OwningProcess -Unique) } catch {}
    $procs = @(Get-CimInstance Win32_Process -Filter "Name='python.exe'" | Where-Object { ($ids -contains $_.ProcessId) -or ($_.CommandLine -like '*server.py*' -and $_.ExecutablePath -like '*labeling_server*') })
    return $procs
}
function Start-Server {
    Head "라벨링 서버 (http://127.0.0.1:$Port · 데이터 $DataRoot)"
    if (Http-Ok "http://127.0.0.1:$Port/api/health") {
        $runningRoot = ''
        try { $runningRoot = (Invoke-RestMethod "http://127.0.0.1:$Port/api/health" -TimeoutSec 3).root } catch {}
        $sameRoot = $runningRoot -and ([IO.Path]::GetFullPath($runningRoot).TrimEnd('\') -ieq [IO.Path]::GetFullPath($DataRoot).TrimEnd('\'))
        if (-not $sameRoot) {
            Warn "실행 중인 서버의 데이터 폴더가 다릅니다: '$runningRoot' → '$DataRoot' 로 재시작"; Stop-Server; Start-Sleep 2
        } elseif (Http-Ok ("http://127.0.0.1:$Port/api/tts?text=" + [Uri]::EscapeDataString('준비')) 40) {
            Ok '이미 실행 중 (데이터 폴더 일치, PC 음성 /api/tts 포함)'; return
        } else { Warn '구버전 서버 실행 중 → 재시작'; Stop-Server; Start-Sleep 2 }
    }
    if (-not (Ensure-ServerEnv $false)) { return }
    if (-not (Test-Path $DataRoot)) { Warn "DATA_ROOT 없음: $DataRoot (.env 확인)"; return }
    $cmdArgs = "/k chcp 65001 >nul & set PYTHONIOENCODING=utf-8& set PYTHONUTF8=1& title Carity labeling_server& `"$Py`" server.py --root `"$DataRoot`" --port $Port $ServerArgs"
    Start-Process -FilePath 'cmd.exe' -ArgumentList $cmdArgs -WorkingDirectory $ServerDir
    for ($i = 0; $i -lt 40 -and -not (Http-Ok "http://127.0.0.1:$Port/api/health" 2); $i++) { Start-Sleep 2 }
    if (Http-Ok "http://127.0.0.1:$Port/api/health") { Ok '서버 시작 (에뮬레이터에서 http://10.0.2.2:8765)' } else { Warn '서버 응답 없음 → 서버 창의 오류를 확인하세요' }
}
function Stop-Server { Server-Procs | ForEach-Object { Log "  서버 종료 PID $($_.ProcessId)"; Stop-Process -Id $_.ProcessId -Force } }

# Carity_ver1 폴더의 파일을 잡고 있는 프로세스를 모두 정리 (이름 변경·삭제 전에 사용)
function Stop-All {
    Head '전체 종료 (파일 잠금 해제)'
    Stop-Server
    # 서버를 띄운 cmd 창 (작업 폴더가 Carity_ver1 안이라 폴더 이름 변경을 막음)
    Get-CimInstance Win32_Process -Filter "Name='cmd.exe'" | Where-Object { $_.CommandLine -like '*Carity labeling_server*' } |
        ForEach-Object { Log "  서버 창 종료 PID $($_.ProcessId)"; Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
    # 진행 중인 복사
    Get-CimInstance Win32_Process -Filter "Name='Robocopy.exe'" | Where-Object { $_.CommandLine -like '*Carity_ver1*' } |
        ForEach-Object { Log "  복사 중단 PID $($_.ProcessId)"; Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
    # Gradle·Kotlin 데몬 (android 폴더의 빌드 파일을 잡고 있음)
    if (Test-Path (Join-Path $Android 'gradlew.bat')) {
        Log '  Gradle 데몬 종료'
        Push-Location $Android; & .\gradlew.bat --stop 2>&1 | Out-Null; Pop-Location
    }
    Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -like '*KotlinCompileDaemon*' } |
        ForEach-Object { Log "  Kotlin 데몬 종료 PID $($_.ProcessId)"; Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
    & $Adb kill-server 2>&1 | Out-Null
    Ok '정리 완료'
    Log '  남은 잠금: Android Studio 가 android 프로젝트를 열고 있으면 File > Close Project 로 닫으세요.' 'White'
    Log '  이 창(Carity.bat)도 Carity_ver1 폴더에서 실행 중이므로, 아무 키나 눌러 닫은 뒤 이름을 바꾸세요.' 'White'
}

# ---------------------------------------------------------------- 에뮬레이터 / 앱
function Device-Online { return @(@(Adb devices) -match "`tdevice$").Count -gt 0 }
function Ensure-Emulator {
    Head 'Pleos 에뮬레이터'
    if (Device-Online) { Ok '연결됨'; return $true }
    # 이미 켜진 에뮬레이터가 있는데 adb 가 못 찾는 경우: 두 번째 실행 대신 adb 재연결
    if (Get-Process -Name 'qemu-system-*' -ErrorAction SilentlyContinue) {
        Log '  에뮬레이터는 켜져 있으나 adb 연결 없음 → adb 재시작'
        & $Adb kill-server 2>&1 | Out-Null; & $Adb start-server 2>&1 | Out-Null
        for ($i = 0; $i -lt 30 -and -not (Device-Online); $i++) { Start-Sleep 2 }
        if (Device-Online) { Ok 'adb 재연결 완료'; return $true }
        Warn '에뮬레이터가 응답하지 않습니다. 에뮬레이터 창을 닫고 다시 시도하세요.'; return $false
    }
    $avd = Cfg 'EMULATOR_AVD' ''
    if (-not $avd -and (Test-Path $Emu)) {
        $list = & $Emu -list-avds 2>$null
        $avd = ($list | Where-Object { $_ -match 'Pleos' } | Select-Object -First 1); if (-not $avd) { $avd = $list | Select-Object -First 1 }
    }
    if (-not $avd -or -not (Test-Path $Emu)) { Warn '에뮬레이터를 찾지 못했습니다. Android Studio 에서 Pleos Connect 를 실행하세요.'; return $false }
    Log "  에뮬레이터 시작: $avd"
    Start-Process -FilePath $Emu -ArgumentList "-avd `"$avd`"" -WindowStyle Normal
    for ($i = 0; $i -lt 90 -and -not (Device-Online); $i++) { Start-Sleep 2 }
    for ($i = 0; $i -lt 90; $i++) { if (((Adb shell getprop sys.boot_completed) -join '').Trim() -eq '1') { break }; Start-Sleep 2 }
    if (Device-Online) { Ok '부팅 완료'; return $true } else { Warn '에뮬레이터 연결 실패'; return $false }
}
function Apps-Installed { $p = (Adb shell pm list packages) -join "`n"; return ($Pkgs | Where-Object { $p -notmatch [regex]::Escape($_) }).Count -eq 0 }
function Build-Install {
    Head '앱 빌드·설치 (app: MOVO 시뮬레이션+튜토리얼 · viewer: 가디언즈 라벨 뷰어)'
    if (-not $env:JAVA_HOME) {
        foreach ($j in @("$env:ProgramFiles\Android\Android Studio\jbr", "$env:ProgramFiles\Android\Android Studio1\jbr")) { if (Test-Path $j) { $env:JAVA_HOME = $j; break } }
    }
    if (-not (Test-Path (Join-Path $Android 'local.properties'))) {
        "sdk.dir=" + ($Sdk -replace '\\', '\\\\' -replace ':', '\:') | Out-File (Join-Path $Android 'local.properties') -Encoding ascii
    }
    Push-Location $Android
    & .\gradlew.bat ':app:assembleDebug' ':viewer:assembleDebug' --console=plain
    $code = $LASTEXITCODE
    Pop-Location
    if ($code -ne 0) { Warn "빌드 실패 (code $code) → Android Studio 에서 android 폴더를 열어 확인"; return }
    Ok '빌드 성공'
    if (-not (Device-Online)) { Warn '기기 없음 → 설치 생략'; return }
    foreach ($apk in @("$Android\app\build\outputs\apk\debug\app-debug.apk", "$Android\viewer\build\outputs\apk\debug\viewer-debug.apk")) {
        if (Test-Path $apk) { $r = (Adb install -r -t $apk) -join ' '; if ($r -match 'Success') { Ok "설치 $([IO.Path]::GetFileName($apk))" } else { Warn "설치 실패 $apk : $r" } }
        else { Warn "APK 없음: $apk" }
    }
}
function Grant-Perms {
    if (-not (Device-Online)) { return }
    $user = ((Adb shell am get-current-user) -join '').Trim(); if (-not $user) { $user = '10' }
    foreach ($perm in @('android.car.permission.CAR_SPEED', 'pleos.car.permission.TTS_SERVICE', 'pleos.car.permission.FUSED_LOCATION')) {
        Adb shell pm grant --user $user com.example.pleostest $perm | Out-Null
    }
    Ok "권한 미리 허용 (user $user) · 보이지 않는 권한 창으로 시뮬레이션이 막히는 문제 방지"
}

# ---------------------------------------------------------------- 상태 / 로그 / 데이터
function Show-Status {
    Head '상태'
    if (Http-Ok "http://127.0.0.1:$Port/api/health") {
        $h = $null; try { $h = Invoke-RestMethod "http://127.0.0.1:$Port/api/health" -TimeoutSec 3 } catch {}
        Ok ("라벨링 서버 :$Port · 데이터 폴더 " + $h.root + " · YOLO " + $h.model + " (" + $h.device + ")")
        if ($h -and $h.root -and ([IO.Path]::GetFullPath($h.root).TrimEnd('\') -ine [IO.Path]::GetFullPath($DataRoot).TrimEnd('\'))) { Warn "서버 데이터 폴더가 .env 의 DATA_ROOT($DataRoot)와 다릅니다 → Carity.bat server 로 재시작" }
    } else { Warn "라벨링 서버 꺼짐 :$Port" }
    # 포트를 잡은 프로세스 (서버가 두 개 떠 있거나 다른 프로그램이 잡은 경우 확인)
    try {
        Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop | ForEach-Object {
            $p = Get-CimInstance Win32_Process -Filter "ProcessId=$($_.OwningProcess)"
            Log ("  포트 {0} 사용: PID {1} {2} | {3}" -f $Port, $_.OwningProcess, $p.ExecutablePath, $p.CommandLine)
        }
    } catch { Log "  포트 $Port 를 잡은 프로세스 없음" }
    # 데이터 폴더 내용
    if (Test-Path $DataRoot) {
        $media = @(Get-ChildItem $DataRoot -Recurse -File -Include *.mp4,*.avi,*.mov,*.mkv,*.jpg,*.png -ErrorAction SilentlyContinue | Where-Object { $_.FullName -notmatch '\\_(labels|preview)\\' }).Count
        $labels = @(Get-ChildItem (Join-Path $DataRoot '_labels') -Recurse -File -Filter *.json -ErrorAction SilentlyContinue).Count
        Log "  데이터 폴더 내용: 영상·이미지 $media 개 · 라벨 $labels 개"
    } else { Warn "데이터 폴더 없음: $DataRoot" }
    # 에뮬레이터 안에서 PC 서버(10.0.2.2) 접속 시험
    if (Device-Online) {
        $r = (Adb shell "(printf 'GET /api/health HTTP/1.0\r\n\r\n'; sleep 3) | toybox nc -w 5 10.0.2.2 $Port | tail -1") -join ' '
        if ($r -match '"ok"') { Ok "에뮬레이터 → 10.0.2.2:$Port 접속 성공" } else { Warn "에뮬레이터 → 10.0.2.2:$Port 접속 실패: $r" }
    }
    if (Http-Ok 'http://127.0.0.1:11434/api/tags') { Ok 'Ollama :11434' } else { Warn 'Ollama 꺼짐 (AI 판단은 규칙 문장으로 대체)' }
    if (Device-Online) {
        Ok '에뮬레이터 연결'
        if (Apps-Installed) { Ok '앱 설치됨: Pleos test(시뮬레이션) · On-sense 튜토리얼 · 가디언즈 라벨 뷰어' } else { Warn '앱 미설치 → Carity.bat build' }
    } else { Warn '에뮬레이터 없음' }
    $k = Cfg 'ANTHROPIC_API_KEY' ''
    Log ("  AI 판단: " + $(if ($k) { 'Claude (.env 키 사용 · 다시 빌드해야 반영)' } else { "Qwen ($QwenModel)" }))
    Log "  데이터 폴더: $DataRoot"
}
function Collect-Logs {
    $out = Join-Path $LogDir ("app_{0}.txt" -f (Get-Date -Format 'yyyyMMdd_HHmmss'))
    (Adb logcat -d -v time) | Select-String -Pattern 'CarTalkVoice|OnSenseAI|OnSenseLabel|AndroidRuntime|pleostest|guardians' | ForEach-Object { $_.Line } | Select-Object -Last 600 | Out-File $out -Encoding utf8
    Ok "로그 저장: $out"
}
function Diagnose {
    Head '네트워크 진단 (에뮬레이터 → PC)'
    # 진단에 필요한 것부터 켭니다 (서버·에뮬레이터)
    Start-Server
    if (-not (Ensure-Emulator)) { Warn '에뮬레이터 없음 → 진단 중단'; return }
    Start-Sleep 5
    Show-Status
    $tests = [ordered]@{
        'ip route'              = 'ip route'
        'http_proxy(global)'    = 'settings get global http_proxy; settings get global global_http_proxy_host; settings get global global_http_proxy_port'
        'ping 10.0.2.2'         = 'ping -c 1 -W 2 10.0.2.2 2>&1 | tail -2'
        'nc 8765 (서버)'        = "(printf 'GET /api/health HTTP/1.0\r\n\r\n'; sleep 3) | toybox nc -w 5 10.0.2.2 $Port 2>&1 | head -3; echo rc=`$?"
        'nc 11434 (Ollama)'     = "(printf 'GET /api/tags HTTP/1.0\r\n\r\n'; sleep 3) | toybox nc -w 5 10.0.2.2 11434 2>&1 | head -3; echo rc=`$?"
        'which nc/curl'         = 'which nc toybox curl 2>&1'
    }
    foreach ($k in $tests.Keys) {
        $out = (Adb shell $tests[$k]) -join ' | '
        Log ("  [{0}] {1}" -f $k, $out)
    }
    Log '  [앱 로그: 뷰어/라벨 연결 오류]'
    (Adb logcat -d -v time) | Select-String -Pattern 'guardians|OnSenseLabel|OnSenseAI|ConnectException|SocketTimeout|Cleartext|UnknownService' | Select-Object -Last 30 | ForEach-Object { Log ("    " + $_.Line) }
}
function Copy-Data {
    Head '데이터 복사 → Carity_ver1\data'
    $dst = Join-Path $Root 'data'
    foreach ($d in @('normal', '_labels', '_preview', '_uploads')) {
        $src = Join-Path $DataRoot $d
        if (Test-Path $src) { Log "  $d 복사 중…"; robocopy $src (Join-Path $dst $d) /E /NFL /NDL /NJH /NJS /NP | Out-Null }
    }
    Ok '완료 → .env 의 DATA_ROOT=data 로 바꾸면 Carity_ver1\data 를 사용합니다'
}

# ---------------------------------------------------------------- 실행
function Do-Start {
    Head 'Carity 전체 시작'
    Ensure-Ollama $false
    Start-Server
    if (Ensure-Emulator) {
        if (-not (Apps-Installed) -or (Cfg 'BUILD_ON_START' 'false') -eq 'true') { Build-Install }
        Grant-Perms
        Adb shell am force-stop com.example.pleostest | Out-Null
        Log '  에뮬레이터 하단 앱 목록에서 실행하세요: "Pleos test"(시뮬레이션·라벨 탭) / "On-sense 튜토리얼" / "가디언즈 라벨 뷰어"' 'White'
    }
    Show-Status
}
function Do-Setup {
    Head 'Carity 설치 (처음 1회)'
    Ensure-ServerEnv $true | Out-Null
    Ensure-Ollama $true
    Show-Status
}

if (-not $Action) {
    Write-Host ''
    Write-Host '  Carity ver1 · 좁은 길 통과 보조 (MOVO + 가디언즈)' -ForegroundColor Cyan
    Write-Host '  1) 전체 시작   2) 처음 설치   3) 앱 빌드·설치   4) 서버만 실행'
    Write-Host '  5) 상태 점검   6) 로그 수집   7) 데이터 복사    8) 전체 종료(파일 잠금 해제)   9) 네트워크 진단   0) 나가기'
    $sel = Read-Host '  번호'
    $Action = @{ '1' = 'start'; '2' = 'setup'; '3' = 'build'; '4' = 'server'; '5' = 'status'; '6' = 'logs'; '7' = 'copydata'; '8' = 'stopall'; '9' = 'diag' }[$sel]
    if (-not $Action) { exit 0 }
}
switch ($Action.ToLower()) {
    'start' { Do-Start }
    'setup' { Do-Setup }
    'build' { if (Ensure-Emulator) { Build-Install; Grant-Perms } else { Build-Install } }
    'server' { Start-Server }
    'status' { Show-Status }
    'logs' { Collect-Logs }
    'copydata' { Copy-Data }
    'stop' { Stop-Server }
    'stopall' { Stop-All }
    'diag' { Diagnose }
    default { Write-Host "알 수 없는 명령: $Action" }
}
