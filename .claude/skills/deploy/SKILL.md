---
name: deploy
description: Build the StrangePlanet debug APK, install it on every connected Android device (or a headless emulator), launch it, and check logcat for crashes. Use when asked to deploy, install, run on the phone, or test a change on device.
allowed-tools: PowerShell(./.claude/skills/deploy/deploy.ps1 *), PowerShell(./.claude/skills/deploy/start-emulator.ps1 *)
---

# Deploy to connected phones

Run from the repo root with the **PowerShell** tool:

```powershell
./.claude/skills/deploy/deploy.ps1
```

Options (combine freely):
- `-NoBuild` — skip Gradle and reinstall the last built APK.
- `-Screenshot` — save a screenshot per device to `build\screenshots\<serial>.png`, then view it with the Read tool.
- `-WaitSeconds 10` — wait longer before checking for crashes (default 5).
- `-Profile` — release-speed build (non-debuggable). Use this whenever smoothness matters; debug builds jank on their own.

To measure jank while the user plays: `./.claude/skills/deploy/jank.ps1 -Seconds 30 -Label "<game> <before|after>"` — prints janky-frame % and frame-time percentiles per phone and appends to `build/jank-log.txt`.

The script builds with `gradlew.bat assembleDebug`, installs on every device in `adb devices`, wakes the screen, force-restarts the app, then checks each device's crash buffer, the low-memory killer, and that the process is alive. It exits 1 on any build, install, or launch failure.

## No phone connected? Use the headless emulator

```powershell
./.claude/skills/deploy/start-emulator.ps1      # boots Pixel_9 with no window, 4 GB RAM; returns when booted (~20 s)
./.claude/skills/deploy/deploy.ps1 -Screenshot
adb -e emu kill                                 # stop it when done
```

The emulator is good for "does it build, launch, not crash, look right". It is **not** valid for jank or frame-time measurements (software GPU) — use the real phones for `dumpsys gfxinfo`. If both a phone and the emulator are attached, deploy installs on both.

## Reporting back

Summarise per device in plain words: model, OK / crashed / not running, and the version deployed. If a device crashed, show the first lines of the stack trace and the likely file:line in our code. If the summary says the phone is locked, ask the user to unlock it (one step) before taking screenshots — locked phones screenshot as solid black.

If a device shows as `unauthorized`, ask the user to accept the "Allow USB debugging?" prompt on the phone.
