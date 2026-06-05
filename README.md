# Mewgenics Radio Android

Private Android test app for playing the Mewgenics radio mode outside the game.

This repository contains only source code and local extraction tooling. It must
not commit or redistribute original Mewgenics assets.

## Current Goal

Phase 2 adds a release-oriented online/cache path while keeping the private
debug build usable with local assets:

- Extract `audio/music/radio.gon` and `audio/music/radio/**/*.ogg` from your own
  Steam install of Mewgenics.
- Parse the original radio playlist and `radio_state_machine`.
- Play the station on Android with Kotlin, Compose, and Media3.

## Required Local Tools

Install Android Studio first. The current machine did not initially have Java,
Gradle, adb, or an Android SDK on `PATH`.

Android Studio and the Android SDK were installed locally during project setup:

```text
Android Studio: C:\Program Files\Android\Android Studio
Android SDK: E:\Android\Sdk
Local Gradle used for verification: E:\Android\gradle-8.10.2
```

For this PowerShell session, verify:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "E:\Android\Sdk"
$env:Path = "$env:JAVA_HOME\bin;E:\Android\Sdk\platform-tools;$env:Path"

& "$env:JAVA_HOME\bin\java.exe" -version
& "$env:ANDROID_HOME\platform-tools\adb.exe" version
```

Open this folder in Android Studio:

```text
E:\Projects\mewgenics-radio-android
```

## Extract Local Assets

Run the extractor from the repo root:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\extract-radio-assets.ps1
```

Default input:

```text
E:\SteamLibrary\steamapps\common\Mewgenics\resources.gpak
```

Default output for private debug builds:

```text
.\app\src\debug\assets\radio
```

Expected output:

```text
app/src/debug/assets/radio/radio.gon
app/src/debug/assets/radio/audio/music/radio/**/*.ogg
```

The output folder is ignored by Git. Do not commit extracted `.ogg`, `.swf`, or
`.gpak` files.

## Convert Assets for Online Playback

The release architecture expects Opus files and a generated manifest. Install
`ffmpeg` first, then run:

```powershell
winget install --id Gyan.FFmpeg --accept-package-agreements --accept-source-agreements
```

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\convert-radio-assets.ps1 -BitrateKbps 128 -BaseUrl "https://your-cdn.example/radio/v1/"
```

Default output:

```text
dist/radio-assets/128kbps/manifest.json
dist/radio-assets/128kbps/radio.gon
dist/radio-assets/128kbps/audio/music/radio/**/*.opus
```

`dist/` and `.opus` files are ignored by Git. Upload this folder to your CDN or
local test server, then set `RADIO_MANIFEST_URL` in the Android build config.

## Build

Once Android Studio has installed the Android SDK, run:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "E:\Android\Sdk"
$env:Path = "$env:JAVA_HOME\bin;E:\Android\Sdk\platform-tools;$env:Path"

.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

For online/release testing, pass the manifest URL without editing source code:

```powershell
.\gradlew.bat :app:assembleRelease -PRADIO_MANIFEST_URL="https://your-cdn.example/radio/v1/manifest.json"
```

The same value can also be provided through an environment variable:

```powershell
$env:RADIO_MANIFEST_URL = "https://your-cdn.example/radio/v1/manifest.json"
.\gradlew.bat :app:assembleRelease
```

Debug/private builds can use `app/src/debug/assets/radio`. Release/public builds
do not package that debug source set, so they stay small and require the online
manifest path.

## MVP Limits

- This version does not restore the original SWF music visualizer.
- Background media session behavior is not yet a release-quality feature.
- Release/public builds should use the online manifest path and should not
  include `app/src/debug/assets/radio`.
