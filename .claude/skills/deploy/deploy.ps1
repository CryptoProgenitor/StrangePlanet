# Build the debug APK, install it on every connected device, launch it,
# and report whether it is running and whether anything crashed.
#
#   ./.claude/skills/deploy/deploy.ps1                # build + install + launch
#   ./.claude/skills/deploy/deploy.ps1 -NoBuild       # reuse the last APK
#   ./.claude/skills/deploy/deploy.ps1 -Screenshot    # also save build/screenshots/<serial>.png
#   ./.claude/skills/deploy/deploy.ps1 -Profile       # release-speed build (use for jank testing)
#
# Exit code 0 = every device launched cleanly, 1 = build/install failed or a crash was seen.
param(
    [switch]$NoBuild,
    [switch]$Screenshot,
    [switch]$Profile,
    [int]$WaitSeconds = 5
)

$pkg = 'com.quokkalabs.strangeplanet'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$variant = if ($Profile) { 'profile' } else { 'debug' }
$apk = Join-Path $root "app\build\outputs\apk\$variant\app-$variant.apk"

function Get-Devices {
    $lines = adb devices | Select-Object -Skip 1 | Where-Object { $_.Trim() }
    foreach ($line in $lines) {
        $parts = $line -split '\s+'
        [pscustomobject]@{ Serial = $parts[0]; State = $parts[1] }
    }
}

$all = @(Get-Devices)
foreach ($d in $all | Where-Object State -ne 'device') {
    Write-Host "!! $($d.Serial) is '$($d.State)' - skipped (unlock the phone / accept the USB debugging prompt)"
}
$devices = @($all | Where-Object State -eq 'device')
if ($devices.Count -eq 0) {
    Write-Host 'FAIL: no devices connected (check the cable and that USB debugging is on).'
    exit 1
}

if (-not $NoBuild) {
    Write-Host "== Building $variant APK"
    $task = if ($Profile) { 'assembleProfile' } else { 'assembleDebug' }
    # Profile builds are phone-only (arm64); an emulator needs the x86 copies too.
    $extra = @()
    if ($Profile -and ($devices | Where-Object { $_.Serial -like 'emulator-*' })) { $extra += '-PallAbis' }
    & (Join-Path $root 'gradlew.bat') -p $root $task --console=plain -q @extra
    if ($LASTEXITCODE -ne 0) {
        Write-Host 'FAIL: Gradle build failed (see errors above).'
        exit 1
    }
}
if (-not (Test-Path $apk)) {
    Write-Host "FAIL: APK not found at $apk"
    exit 1
}

$results = @()
foreach ($d in $devices) {
    $s = $d.Serial
    $model = (adb -s $s shell getprop ro.product.model).Trim()
    Write-Host "== $model ($s): installing"
    $install = adb -s $s install -r $apk 2>&1 | Out-String
    if ($install -notmatch 'Success') {
        Write-Host $install.Trim()
        $results += [pscustomobject]@{ Device = "$model ($s)"; Status = 'INSTALL FAILED'; Detail = '' }
        continue
    }
    adb -s $s logcat -c | Out-Null
    adb -s $s shell input keyevent KEYCODE_WAKEUP | Out-Null
    $start = adb -s $s shell am start -W -S -n "$pkg/.MainActivity" | Out-String
    $launchMs = if ($start -match 'TotalTime: (\d+)') { $Matches[1] } else { '?' }
    $results += [pscustomobject]@{ Device = "$model ($s)"; Serial = $s; Status = 'LAUNCHED'; Detail = ''; LaunchMs = $launchMs }
}

Start-Sleep -Seconds $WaitSeconds

$failed = $false
foreach ($r in $results) {
    if ($r.Status -ne 'LAUNCHED') { $failed = $true; continue }
    $s = $r.Serial
    $procId = (adb -s $s shell pidof $pkg | Out-String).Trim()
    $crash = (adb -s $s logcat -d -b crash | Out-String).Trim()
    $locked = (adb -s $s shell dumpsys window | Select-String 'isKeyguardShowing=true') -ne $null
    $lmk = adb -s $s logcat -d | Select-String "lowmemorykiller: Kill '$pkg"

    if ($crash) {
        $r.Status = 'CRASHED'
        $r.Detail = ($crash -split "`n" | Select-Object -First 25) -join "`n"
        $failed = $true
    } elseif ($lmk) {
        $r.Status = 'KILLED BY LOW-MEMORY KILLER (device is out of RAM - not an app crash)'
        $failed = $true
    } elseif (-not $procId) {
        $r.Status = 'NOT RUNNING (no crash logged - check: adb logcat -d | Select-String strangeplanet)'
        $failed = $true
    } else {
        $r.Status = "OK (pid $procId, cold start $($r.LaunchMs) ms)"
    }
    if ($locked) { $r.Status += ' - phone is LOCKED, screenshots will be black' }

    if ($Screenshot) {
        $dir = Join-Path $root 'build\screenshots'
        New-Item -ItemType Directory -Force $dir | Out-Null
        adb -s $s shell screencap -p /sdcard/sp_shot.png | Out-Null
        adb -s $s pull /sdcard/sp_shot.png (Join-Path $dir "$s.png") 2>$null | Out-Null
        adb -s $s shell rm /sdcard/sp_shot.png | Out-Null
        $r.Status += " - screenshot: build\screenshots\$s.png"
    }
}

$version = (Select-String -Path (Join-Path $root 'app\build.gradle.kts') -Pattern 'versionName = "(.+)"').Matches[0].Groups[1].Value
Write-Host ''
Write-Host "== Deploy summary (v$version, $variant build)"
foreach ($r in $results) {
    Write-Host "$($r.Device): $($r.Status)"
    if ($r.Detail) { Write-Host $r.Detail }
}
if ($failed) { exit 1 } else { exit 0 }
