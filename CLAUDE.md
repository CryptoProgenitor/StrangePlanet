# StrangePlanet

Jetpack Compose Android app (`com.quokkalabs.strangeplanet`, launcher `.MainActivity`): a "Strange Planet" creature screen plus a launcher ("Planetary Bulletin") of mini-games. Single `app` module, Kotlin, minSdk 26, targetSdk 36.

## Working with the user

- The user vibe codes and does not edit in the IDE. Do the work; when they must do something by hand (unlock phone, tap a prompt), give **one step at a time**.
- Tell them when the conversation is getting long.

## Branches

- **`master` is stale (v1.7).** The real line of development is `claude/check-strange-planet-app-sQtLb`. Branch new work from there, never from `master`.
- `origin/claude/dreamy-curie-uifsmx` holds untested jank-fix commits built on old master — use as reference only, do not merge.

## Environment (Windows 11)

- Android SDK: `C:\Users\eddar\AppData\Local\Android\Sdk` (this is what `local.properties` must point to). `C:\android_sdk` only contains `platform-tools`/adb and cannot build.
- `JAVA_HOME` = `C:\Program Files\Android\Android Studio\jbr`. `adb` is on PATH.
- Devices: S24 Ultra (SM-S928B, serial `R5CWC2C2XAH`, smooth), Galaxy S24+ (likely Exynos, shows jank).

## Commands (run from repo root)

Prefer the `/deploy` skill (`./.claude/skills/deploy/deploy.ps1`) — it builds, installs on every connected device, launches, and checks for crashes.

```powershell
.\gradlew.bat assembleDebug                      # build
.\gradlew.bat installDebug                       # build + install on all connected devices
adb shell am start -S -n com.quokkalabs.strangeplanet/.MainActivity   # (re)launch
adb logcat -d -b crash                           # crashes since last clear (clear: adb logcat -b crash -c)
adb logcat --pid=$(adb shell pidof com.quokkalabs.strangeplanet)      # app log (Bash tool)
adb shell dumpsys gfxinfo com.quokkalabs.strangeplanet reset          # zero frame stats, then play
adb shell dumpsys gfxinfo com.quokkalabs.strangeplanet                # read "Janky frames" + percentiles
adb shell dumpsys package com.quokkalabs.strangeplanet | Select-String versionName
```

Screenshot (works in either shell; then view the PNG with Read):

```powershell
adb shell screencap -p /sdcard/s.png; adb pull /sdcard/s.png build/screenshots/s.png
```

With several devices attached, add `-s <serial>` after `adb` (`-e` = the emulator, `-d` = the USB phone).

## Headless emulator (no phone needed)

```powershell
./.claude/skills/deploy/start-emulator.ps1   # boots Pixel_9 (Android 37) with no window, 4 GB RAM, returns when booted
adb -e emu kill                              # stop it
```

- Needs 4 GB (`-memory 4096`, the script's default). At the AVD's default 2 GB the low-memory killer kills the app within seconds; that looks like a silent crash but isn't.
- Launching `emulator.exe` by hand fails with "Cannot find AVD system path" unless `ANDROID_HOME`/`ANDROID_SDK_ROOT` are set to the AppData SDK for that process — the script does this.
- Fine for launch/crash/screenshot checks; **not** for jank numbers (software GPU, `swiftshader_indirect`). Measure frame times on the real phones.

## Measuring jank

Debug builds are debuggable, which makes Compose much slower — never judge smoothness on one. Use the `profile` build type (release settings, signed with the local debug key, installs over the debug app and keeps its data):

```powershell
./.claude/skills/deploy/deploy.ps1 -Profile                    # or .\gradlew.bat installProfile
./.claude/skills/deploy/jank.ps1 -Seconds 30 -Label "merge after"   # user plays meanwhile; appends to build/jank-log.txt
```

## Gotchas

- **Locked phone = solid black screenshot.** Check `adb shell dumpsys window | Select-String isKeyguardShowing`; wake with `adb shell input keyevent KEYCODE_WAKEUP`, then ask the user to unlock.
- **PowerShell 5.1**: no `&&` (use `; if ($?) { ... }`); quote `'stash@{0}'`; never redirect binary output with `>` (`adb exec-out screencap -p > x.png` corrupts the PNG — use the Bash tool or the pull approach above).
- **Git Bash rewrites `/sdcard/...` into a Windows path** — prefix with `MSYS_NO_PATHCONV=1` when passing device paths to adb from the Bash tool.
- `CHANGELOG.md` stopped at 2.3.1 and is not maintained.

## Releases

Every user-visible change bumps `versionCode` and `versionName` in `app/build.gradle.kts` (shown on the launcher as "v3.4.4 · build 66"). Commit subject convention: `<what changed> — v3.4.4 (66)`. CI (`.github/workflows/build.yml`) builds a debug APK on every push to `claude/**`.

## Code layout (`app/src/main/java/com/quokkalabs/strangeplanet/`)

- `domain/*Engine.kt` — pure game logic (step/update functions).
- `data/model/*State.kt` — immutable game state.
- `ui/viewmodel/*ViewModel.kt` — owns the game loop and exposes state.
- `ui/screen/*Screen.kt` — Compose rendering; `ui/components/` shared pieces (StarField, CreatureSprite, GamePosters, CylinderCarousel).
- `bluetooth/`, `firebase/`, `webrtc/` — 2-player modes.

Launcher titles → code:

| Title | Code |
|---|---|
| Sphere Deflection | Pong |
| Descending Entity Defence | SpaceInvaders |
| Sustenance Pursuit | Pac (MazeEngine) |
| Spatial Debris Avoidance | Asteroid |
| Strange Match | StrangeMatch |
| Spherical Agglomeration | Merge |
| Fragment Descent | Tetris |
| Ambient Decoration | Settings + live wallpaper |
| Creature Interaction | StrangePlanetViewModel / InteractiveScreen |

## Performance

- Game loops run on `AndroidUiDispatcher.Main` and pace with `FrameTicker.awaitTick()` (fixed 60 ticks/s from vsync) — never `delay(16)`.
- A game's `StateFlow` emits every tick. Read it in the Canvas **draw** lambda (`liveState.value`), and give the rest of the screen a `derivedStateOf` summary (see `MergeHud`, `TetrisHud`) so the screen doesn't recompose 60×/s.
- Don't build gradients/paths per entity per frame: cache sprites (`OrbSprites`, `CellSprites`) or brushes. Decode bitmaps at drawn size with `rememberScaledImage`/`decodeScaled`. Sprite PNGs live in `drawable-nodpi` and must always be drawn at an explicit size.
