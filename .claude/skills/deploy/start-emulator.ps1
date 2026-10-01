# Boot an Android emulator headless (no window) and return once it has finished booting.
# The emulator keeps running afterwards; stop it with:  adb -e emu kill
#
#   ./.claude/skills/deploy/start-emulator.ps1               # Pixel_9, 4 GB RAM
#   ./.claude/skills/deploy/start-emulator.ps1 -Avd Pixel_5
param(
    [string]$Avd = 'Pixel_9',
    [int]$MemoryMb = 4096,
    [int]$TimeoutSeconds = 180
)

if ((adb devices) -match '^emulator-\d+\s+device') {
    Write-Host 'An emulator is already running.'
    exit 0
}

# Take the SDK from local.properties: the user-level ANDROID_HOME points at C:\android_sdk,
# which only holds adb, and the emulator fails with "Cannot find AVD system path" there.
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$sdkLine = Select-String -Path (Join-Path $root 'local.properties') -Pattern '^sdk\.dir=(.+)$'
$sdk = $sdkLine.Matches[0].Groups[1].Value -replace '\\:', ':' -replace '\\\\', '\'
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk

# 4 GB matters: the default 2 GB makes the low-memory killer kill the app (and the launcher).
Start-Process -FilePath (Join-Path $sdk 'emulator\emulator.exe') -WindowStyle Hidden -ArgumentList @(
    '-avd', $Avd, '-no-window', '-no-audio', '-no-boot-anim', '-no-snapshot-save',
    '-gpu', 'swiftshader_indirect', '-memory', $MemoryMb
)

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 2
    $booted = (adb -e shell getprop sys.boot_completed 2>$null | Out-String).Trim()
    if ($booted -eq '1') {
        Write-Host "Emulator $Avd booted (headless, $MemoryMb MB)."
        exit 0
    }
}
Write-Host "FAIL: emulator $Avd did not finish booting within $TimeoutSeconds s."
exit 1
