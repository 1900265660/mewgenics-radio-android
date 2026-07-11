# Mewgenics Radio Android

Mewgenics Radio Mode 的 Android 独立播放器。项目目标是把原游戏电台模式移植成一个可独立安装的 Android App：音频走在线 CDN 流式播放和按需缓存，界面保留电台氛围，并尽量还原原版 radio 的播报和歌曲接续体验。

> 本仓库不提交原始游戏音频、转码后的音频包或本地提取资源。音频资源通过远程 manifest 加载，或由用户从自己本机的 Mewgenics 安装目录中提取。

## 下载

最新版本请到 [GitHub Releases](https://github.com/1900265660/mewgenics-radio-android/releases) 下载。

推荐下载：

| 文件 | 说明 |
| --- | --- |
| `mewgenics-radio-v1.2.0-onlineDebug.apk` | 在线音频版，使用 COS/CDN manifest，推荐安装 |
| `app-debug.apk` | 本地 assets 调试版，需要自行提取音频到 `app/src/debug/assets/radio/` |

当前在线音频 manifest：

```text
https://mewgenics-1425638300.cos.ap-shanghai.myqcloud.com/radio/v1/manifest.json
```

## 功能

- 支持 Mewgenics radio 音频目录中的 547 个曲目条目。
- 支持 `Full Radio` 和 `Songs Only` 两种模式。
- `Full Radio` 使用原版 `radio.gon` 的 playlist 和 state machine 调度。
- 播报语音会绑定下一首歌：例如播报“下面播放 xxx”后，下一段歌曲会接上 `xxx`。
- 在线播放优先走 CDN，播放过的远程曲目按需进入本地缓存。
- 后台侧边栏提供播放源、音质、缓存大小、状态、错误报告和 GitHub 更新入口。
- 主界面只展示面向用户的歌曲信息，不再暴露 state/category/source/cache 等后端细节。
- Compose Canvas 视觉层和循环视频提供电台氛围展示。

## 安装和覆盖更新

Android 覆盖安装依赖 APK 内部的 `versionCode`，不是 GitHub release 标题。

`v1.2.0` 已修正内部版本：

```text
package: com.local.mewgenicsradio.online
versionCode: 120
versionName: 1.2.0-online
```

如果仍然无法覆盖安装，通常是旧包和新包签名不一致。可以先卸载旧版 `com.local.mewgenicsradio.online`，再安装新版 APK。

## 本地构建

需要 Android Studio / Android SDK / JDK 17。

常用环境示例：

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "E:\Android\Sdk"
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"
```

构建在线测试 APK：

```powershell
.\gradlew.bat --no-daemon :app:assembleOnlineDebug `
  -PRADIO_MANIFEST_URL="https://mewgenics-1425638300.cos.ap-shanghai.myqcloud.com/radio/v1/manifest.json"
```

构建本地 assets 调试 APK：

```powershell
.\gradlew.bat --no-daemon :app:assembleDebug
```

构建产物位置：

```text
app/build/outputs/apk/onlineDebug/app-onlineDebug.apk
app/build/outputs/apk/debug/app-debug.apk
```

## 本地资源提取

如果要使用本地 assets 调试版，需要从自己本机的 Mewgenics 安装目录提取 radio 资源：

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\extract-radio-assets.ps1
```

默认输出到：

```text
app/src/debug/assets/radio/
```

这些资源不会提交到 Git：

```text
app/src/debug/assets/radio/
app/src/main/assets/radio/
dist/
*.ogg
*.opus
*.gpak
*.swf
```

## CDN 资源准备

转码并生成 manifest：

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\convert-radio-assets.ps1 `
  -BitrateKbps 128 `
  -BaseUrl "https://your-cdn.example/radio/v1/"
```

本地 HTTP 测试服务：

```powershell
python .\tools\serve-radio-assets.py --root .\dist\radio-assets\128kbps --host 0.0.0.0 --port 8088
```

本地服务支持：

- `manifest.json`
- `radio.gon`
- `.opus` 音频
- HTTP Range / `206 Partial Content`
- `HEAD`
- CORS

## 验证

常用验证命令：

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest
.\gradlew.bat --no-daemon :app:assembleOnlineDebug `
  -PRADIO_MANIFEST_URL="https://mewgenics-1425638300.cos.ap-shanghai.myqcloud.com/radio/v1/manifest.json"
```

检查 APK 内部版本：

```powershell
E:\Android\Sdk\build-tools\35.0.0\aapt.exe dump badging `
  .\app\build\outputs\apk\onlineDebug\app-onlineDebug.apk
```

## 更新记录

### v1.2.0

发布时间：2026-07-07

- 修正 APK 内部版本号：`versionCode = 120`，`versionName = 1.2.0`。
- 修复 `v1.1.0` 后覆盖安装失败的问题：之前 release 标题是 1.1，但 APK 内部仍是 `versionCode = 1`。
- 发布 `mewgenics-radio-v1.2.0-onlineDebug.apk`，使用 COS 在线 manifest。
- Release notes 已记录在线 manifest、版本号和安装注意事项。

### v1.1.0

发布时间：2026-07-05

- 优化 Compose Canvas 可视化效果。
- 调亮配色，降低覆盖层透明度。
- 动画周期缩短到 6 秒。

### v1.0.0

发布时间：2026-07-05

- 建立 Android 独立播放器基础版本。
- 支持在线 manifest、远程音频播放、按需缓存和基础电台调度。
- 提供 `Full Radio` / `Songs Only` 模式。

### 当前开发版

- `Full Radio` 的播报片段现在会锁定下一首歌，保证语音播报和歌曲接续一致。
- 主界面只显示歌曲和播放模式。
- 后台侧边栏显示 source、quality、cache、state、错误报告和 GitHub 更新入口。
- 缓存按钮改为只缓存当前曲目，避免误触后缓存全部歌曲。

## 已知限制

- 当前仍是调试签名 APK，不是正式商店签名包。
- 在线播放依赖 COS/CDN manifest 可访问。
- release 构建需要显式提供 `RADIO_MANIFEST_URL`。
- 后台播放通知、媒体会话体验仍有继续完善空间。
