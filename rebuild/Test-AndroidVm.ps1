param([switch]$RunTests, [switch]$RunRestartProbe)
$ErrorActionPreference = 'Stop'
$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$serial = 'emulator-5556'
$avd = 'AA_Player_Test_API_35'
$logs = Join-Path $PSScriptRoot '.tools\testing'
New-Item -ItemType Directory -Force $logs | Out-Null

# Never select a user's device implicitly, even when only one is connected.
$devices = & $adb devices
if (-not ($devices -match "^$serial\s")) {
    $available = & (Join-Path $sdk 'emulator\emulator.exe') -list-avds
    if ($available -notcontains $avd) { throw "Missing dedicated AVD: $avd" }
    if (Get-NetTCPConnection -LocalPort 5556,5557 -ErrorAction SilentlyContinue) {
        throw 'Dedicated emulator ports are occupied. No existing device will be changed.'
    }
    Start-Process -FilePath (Join-Path $sdk 'emulator\emulator.exe') -WindowStyle Hidden `
        -ArgumentList @('-avd',$avd,'-port','5556','-no-window','-no-snapshot','-no-boot-anim','-gpu','swiftshader_indirect') `
        -RedirectStandardOutput (Join-Path $logs 'emulator.stdout.log') `
        -RedirectStandardError (Join-Path $logs 'emulator.stderr.log') | Out-Null
}
$deadline = (Get-Date).AddMinutes(4)
do {
    $boot = & $adb -s $serial shell getprop sys.boot_completed 2>$null
    if ($boot -eq '1') { break }
    if ((Get-Date) -gt $deadline) { throw 'Dedicated VM boot timed out; inspect .tools/testing logs.' }
    Start-Sleep -Seconds 2
} while ($true)
$identity = & $adb -s $serial emu avd name
if ($LASTEXITCODE -ne 0 -or $identity -notcontains $avd) {
    throw "Device identity mismatch on $serial; refusing installation or testing."
}
Write-Host "Verified dedicated test VM: $avd ($serial)"
if ($RunTests -or $RunRestartProbe) {
    foreach ($apk in @('app\build\outputs\apk\debug\app-debug.apk', 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk')) {
        $apkPath = Join-Path $PSScriptRoot $apk
        if (-not (Test-Path $apkPath)) { throw "Build APK first: $apkPath" }
        & $adb -s $serial install -r $apkPath
        if ($LASTEXITCODE -ne 0) { throw "Installation failed: $apkPath" }
    }
}
if ($RunTests) {
    $result = & $adb -s $serial shell am instrument -w -e disableAnalytics true app.aavideoplayercar.test/app.aavideoplayercar.DownloadRestartProbe 2>&1
    $result | Tee-Object -FilePath (Join-Path $logs 'instrumentation.txt')
    if ($LASTEXITCODE -ne 0 -or -not ($result -match '^OK \(\d+ tests?\)')) { throw 'Instrumentation did not pass.' }
}
if ($RunRestartProbe) {
    function Invoke-RestartPhase([string]$phase) {
        $phaseResult = & $adb -s $serial shell am instrument -w -e phase $phase app.aavideoplayercar.test/app.aavideoplayercar.DownloadRestartProbe 2>&1
        $phaseExit = $LASTEXITCODE
        $phaseResult | Set-Content -LiteralPath (Join-Path $logs "restart-$phase.txt") -Encoding utf8
        Write-Host ($phaseResult -join [Environment]::NewLine)
        if ($phaseExit -ne 0 -or -not ($phaseResult -match "^INSTRUMENTATION_RESULT: probe=PASS $phase$")) { throw "Restart probe failed in $phase phase." }
    }
    Invoke-RestartPhase 'seed'
    $restartIdentity = & $adb -s $serial emu avd name
    if ($LASTEXITCODE -ne 0 -or $restartIdentity -notcontains $avd) { throw 'VM identity changed; refusing process stop.' }
    & $adb -s $serial shell am force-stop app.aavideoplayercar
    if ($LASTEXITCODE -ne 0) { throw 'Could not stop the test application.' }
    $remainingPid = & $adb -s $serial shell pidof app.aavideoplayercar
    if ($remainingPid) { throw 'Test application process still exists.' }
    Invoke-RestartPhase 'verify'
}
