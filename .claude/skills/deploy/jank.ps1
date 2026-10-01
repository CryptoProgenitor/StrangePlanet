# Measure frame pacing while someone plays: resets the app's frame stats, waits, then
# prints the jank summary from `dumpsys gfxinfo` for each connected phone.
#
#   ./.claude/skills/deploy/jank.ps1 -Seconds 30 -Label "merge before"
#
# Results are also appended to build/jank-log.txt so before/after runs can be compared.
param(
    [int]$Seconds = 30,
    [string]$Label = '',
    [string]$Serial = ''
)

$pkg = 'com.quokkalabs.strangeplanet'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$log = Join-Path $root 'build\jank-log.txt'
New-Item -ItemType Directory -Force (Split-Path $log) | Out-Null

$serials = if ($Serial) { @($Serial) } else {
    @(adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\tdevice$' } | ForEach-Object { ($_ -split '\s+')[0] })
}
if ($serials.Count -eq 0) { Write-Host 'FAIL: no devices connected.'; exit 1 }

foreach ($s in $serials) { adb -s $s shell dumpsys gfxinfo $pkg reset | Out-Null }
Write-Host "Recording for $Seconds s - play now..."
Start-Sleep -Seconds $Seconds

foreach ($s in $serials) {
    $model = (adb -s $s shell getprop ro.product.model).Trim()
    $refresh = (adb -s $s shell dumpsys display | Select-String 'mRefreshRate=|renderFrameRate' | Select-Object -First 1).Line.Trim()
    $stats = adb -s $s shell dumpsys gfxinfo $pkg |
        Select-String 'Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile|Number Missed Vsync|Number Slow UI thread|Number Slow issue draw|Number Frame deadline missed' |
        ForEach-Object { $_.Line.Trim() } | Select-Object -Unique
    $header = "== $(Get-Date -Format 'yyyy-MM-dd HH:mm') $model ($s) $Label"
    $block = @($header) + $stats + @("   display: $refresh", '')
    $block | ForEach-Object { Write-Host $_ }
    Add-Content -Path $log -Value $block
}
