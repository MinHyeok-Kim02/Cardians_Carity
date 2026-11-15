# MOVO 실행 준비: 권한 미리 허용(보이지 않는 권한 창 문제 해결) 후 앱 강제 종료
#   powershell -ExecutionPolicy Bypass -File tools\movo_prepare.ps1
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path $adb)) { $adb = 'adb' }
$pkg = 'com.example.pleostest'
$outDir = Join-Path $PSScriptRoot 'out'; New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$log = Join-Path $outDir 'movo_prepare.log'; "" | Out-File $log -Encoding utf8
function Log($m) { Write-Host $m; Add-Content -Path $log -Value $m -Encoding utf8 }
$user = (& $adb shell am get-current-user 2>$null | Out-String).Trim(); if (-not $user) { $user = '10' }
Log "current user: $user"
foreach ($perm in @('android.car.permission.CAR_SPEED', 'pleos.car.permission.TTS_SERVICE', 'pleos.car.permission.FUSED_LOCATION')) {
    $r = & $adb shell pm grant --user $user $pkg $perm 2>&1 | Out-String
    Log ("grant {0}: {1}" -f $perm, $(if ($r.Trim()) { $r.Trim() } else { 'OK' }))
}
& $adb shell dumpsys package $pkg 2>&1 | Select-String -Pattern 'CAR_SPEED|TTS_SERVICE|FUSED_LOCATION' | ForEach-Object { Log "  $_" }
& $adb shell am force-stop --user $user $pkg 2>&1 | Out-Null
Log 'force-stop 완료 → 에뮬레이터 앱 목록에서 "Pleos test" 를 눌러 실행하세요.'
& $adb logcat -c 2>&1 | Out-Null
Log 'logcat 초기화 완료'
