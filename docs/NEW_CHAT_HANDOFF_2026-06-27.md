# 新对话交接：Mewgenics Radio Android

日期：2026-06-27  
项目路径：`E:\Projects\mewgenics-radio-android`  
游戏安装路径：`E:\SteamLibrary\steamapps\common\Mewgenics`  
上一份详细交接：`docs/HANDOFF_2026-06-09.md`

## 给新对话的最短提示

继续 `E:\Projects\mewgenics-radio-android`。这是 Mewgenics Radio Mode 的 Android 移植工程。Phase 1 本地 assets 播放已完成；Phase 2 在线 manifest + Opus + 按需缓存架构已实现。本地资源在 `dist/radio-assets/128kbps`，manifest 目前指向 `http://10.99.239.143:8088/`，但本地服务器当前未运行。未提交改动包括 README、Gradle `onlineDebug`、Manifest cleartext placeholder、`convert-radio-assets.ps1` 修复、新增 `tools/serve-radio-assets.py`、`docs/HANDOFF_2026-06-09.md` 和本文件。下一步先启动服务器，构建/安装 `onlineDebug`，做手机实机在线播放和缓存测试，通过后提交 Phase 2。

## 当前一句话状态

项目已经从“800MB 音频打进 APK 的本机测试版”推进到“App 小包 + 在线 Opus 音频 + 按需缓存”的发布架构雏形。代码和工具链已经搭好，缺的是手机实机复测、提交 Git、配置真实 CDN/HTTPS，以及之后的 radio 动画效果移植。

## Git 状态

当前分支与提交：

```text
branch: master
HEAD: 51c54c5 Initial Mewgenics radio Android app
remote: origin https://github.com/1900265660/-.git
```

注意：

- 远程仓库已配置，但之前 push 因 GitHub Credential Manager 等待登录而没有继续处理。
- 当前 Phase 2 相关改动尚未提交。
- 不要提交任何原始或转码后的 Mewgenics 资源。

当前未提交源码/文档改动：

```text
M  README.md
M  app/build.gradle.kts
M  app/src/main/AndroidManifest.xml
M  tools/convert-radio-assets.ps1
?? docs/
?? tools/serve-radio-assets.py
```

当前被 `.gitignore` 忽略的本地资源/构建产物：

```text
.gradle/
app/build/
app/src/debug/
dist/
local.properties
```

重要忽略规则已经存在：

```text
/dist/
/app/src/main/assets/radio/
/app/src/debug/assets/radio/
*.gpak
*.bak
*.ogg
*.opus
*.swf
```

## 已完成内容

### Phase 1：本地私用播放版

已完成 Android 私用测试 App：

- Kotlin Android App
- Jetpack Compose UI
- AndroidX Media3/ExoPlayer 播放
- 解析原版 `radio.gon`
- 复用原版 playlist 和 `radio_state_machine`
- 支持播放/暂停、下一段
- 支持 `FullRadio` / `SongsOnly`
- 显示当前 state、分类、音频文件名

本地 debug assets：

```text
app/src/debug/assets/radio/radio.gon
app/src/debug/assets/radio/audio/music/radio/**/*.ogg
```

当前统计：

```text
547 .ogg
798,765,202 bytes
```

### Phase 2：在线播放 + 按需缓存

已实现核心架构：

- `RemoteRadioManifest.kt`
  - 解析远程 `manifest.json`
  - track 字段包括 `id`、`category`、`relativePath`、`url`、`bytes`、`sha256`、`durationMs`、`codec`、`bitrateKbps`
- `RadioCacheManager.kt`
  - 缓存目录：`context.filesDir/radio-cache/`
  - 下载远程 track
  - 校验 `bytes` 和 `sha256`
  - 统计缓存大小
  - 清理缓存
- `RadioAssetResolver.kt`
  - 播放来源优先级：有效缓存 > 远程 URL > bundled asset
- `RadioPlayer.kt`
  - 播放通用 URI，不再只支持 `asset:///`
- `RadioViewModel.kt`
  - 加载 bundled/remote manifest
  - 调用 resolver
  - 自动缓存远程播放过的 track
  - 支持缓存 songs、缓存 full radio、清理缓存
- UI
  - 显示当前来源：`Bundled` / `Remote` / `Cached`
  - 显示缓存大小
  - 显示音质
  - 提供清理缓存、缓存 songs、缓存 full radio 操作

新增本地服务器：

```text
tools/serve-radio-assets.py
```

服务器能力：

- 服务 `manifest.json`、`radio.gon`、`.opus`
- 支持 HTTP Range
- 支持 `206 Partial Content`
- `.opus` 返回 `audio/ogg`
- 支持 `HEAD`
- 带 CORS
- 有路径保护

新增 Android 构建类型：

```text
onlineDebug
```

用途：

- 本地/局域网在线播放测试
- 使用 debug 签名
- application id：`com.local.mewgenicsradio.online`
- 允许本地 HTTP cleartext
- 不打包 `app/src/debug/assets/radio`
- APK 小，适合手机实机在线测试

## 当前在线资源状态

Opus 输出目录：

```text
dist/radio-assets/128kbps
```

当前统计：

```text
549 files
304,766,117 bytes
```

Manifest：

```text
path: dist/radio-assets/128kbps/manifest.json
baseUrl: http://10.99.239.143:8088/
codec: opus
bitrateKbps: 128
trackCount: 547
actual tracks: 547
```

注意：`baseUrl` 写死了当时电脑的 LAN IP。若当前电脑 IP 变化，必须重新生成 manifest，或者改用真实 CDN URL。

## 当前服务器状态

`8088` 当前没有监听，本地测试服务器未运行。

启动本地服务器：

```powershell
cd E:\Projects\mewgenics-radio-android
python .\tools\serve-radio-assets.py --root .\dist\radio-assets\128kbps --host 0.0.0.0 --port 8088
```

本机 smoke test：

```powershell
Invoke-WebRequest "http://127.0.0.1:8088/manifest.json"
curl.exe -I -r 0-99 "http://127.0.0.1:8088/audio/music/radio/songs/catsanova.opus"
```

预期 Range 响应：

```text
HTTP/1.0 206 Partial Content
Content-Type: audio/ogg
Content-Length: 100
Content-Range: bytes 0-99/...
Accept-Ranges: bytes
```

手机要和电脑在同一个局域网。若手机访问不到 `http://<电脑IP>:8088/manifest.json`，优先检查：

- 电脑 IP 是否已变化
- 本地服务器是否正在运行
- Windows 防火墙是否允许 8088
- 手机和电脑是否同一 Wi-Fi / LAN

之前曾添加过防火墙规则：

```text
Mewgenics Radio Local Test Server (TCP 8088)
port: 8088
remote: LocalSubnet
profile: Public, Private
```

## 工具链环境

Android Studio：

```text
C:\Program Files\Android\Android Studio
```

Android SDK：

```text
E:\Android\Sdk
```

JBR/JDK：

```text
C:\Program Files\Android\Android Studio\jbr
```

常用环境设置：

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "E:\Android\Sdk"
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"
```

ffmpeg 是通过 winget 安装的。如果当前 shell 找不到 `ffmpeg` / `ffprobe`，临时加入：

```powershell
$ffmpegBin = "C:\Users\Administrator\AppData\Local\Microsoft\WinGet\Packages\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe\ffmpeg-8.1.1-full_build\bin"
$env:Path = "$ffmpegBin;$env:Path"
```

## 常用命令

重新生成 128kbps Opus 资源和 manifest：

```powershell
cd E:\Projects\mewgenics-radio-android
$lanIp = "当前电脑局域网IP"
powershell -ExecutionPolicy Bypass -File .\tools\convert-radio-assets.ps1 -BitrateKbps 128 -BaseUrl "http://$lanIp:8088/"
```

构建在线测试 APK：

```powershell
cd E:\Projects\mewgenics-radio-android
.\gradlew.bat --no-daemon :app:assembleOnlineDebug -PRADIO_MANIFEST_URL="http://当前电脑局域网IP:8088/manifest.json"
```

安装到手机：

```powershell
adb devices
adb install -r .\app\build\outputs\apk\onlineDebug\app-onlineDebug.apk
```

跑单元测试：

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest
```

构建 release 小包：

```powershell
.\gradlew.bat --no-daemon :app:assembleRelease -PRADIO_MANIFEST_URL="https://your-cdn.example/radio/v1/manifest.json"
```

本地 HTTP release 测试才需要：

```powershell
-PALLOW_CLEARTEXT=true
```

正式发布应使用 HTTPS，不要依赖 cleartext。

## 最近构建产物

最近 APK 产物：

```text
app/build/outputs/apk/debug/app-debug.apk              811,825,018 bytes  2026-06-05 17:52
app/build/outputs/apk/onlineDebug/app-onlineDebug.apk   12,212,212 bytes  2026-06-05 18:57
app/build/outputs/apk/release/app-release-unsigned.apk   9,227,587 bytes  2026-06-05 18:58
```

`onlineDebug` 之前已检查过不包含 `.ogg`、`.opus`、`.swf`、`.gpak`。

2026-06-27 这次只整理交接文档，没有重新跑 Gradle 构建。

## 重要文件

主代码：

```text
app/src/main/java/com/local/mewgenicsradio/MainActivity.kt
app/src/main/java/com/local/mewgenicsradio/RadioViewModel.kt
app/src/main/java/com/local/mewgenicsradio/RadioPlayer.kt
app/src/main/java/com/local/mewgenicsradio/RadioScheduler.kt
app/src/main/java/com/local/mewgenicsradio/RadioConfigParser.kt
app/src/main/java/com/local/mewgenicsradio/RadioRepository.kt
app/src/main/java/com/local/mewgenicsradio/RadioAssetCatalog.kt
app/src/main/java/com/local/mewgenicsradio/RadioAssetResolver.kt
app/src/main/java/com/local/mewgenicsradio/RadioCacheManager.kt
app/src/main/java/com/local/mewgenicsradio/RemoteRadioManifest.kt
```

工具：

```text
tools/extract-radio-assets.ps1
tools/convert-radio-assets.ps1
tools/serve-radio-assets.py
```

测试：

```text
app/src/test/java/com/local/mewgenicsradio/RadioConfigParserTest.kt
app/src/test/java/com/local/mewgenicsradio/RemoteAssetSystemTest.kt
```

文档：

```text
README.md
docs/HANDOFF_2026-06-09.md
docs/NEW_CHAT_HANDOFF_2026-06-27.md
```

## 当前风险和限制

- Phase 2 改动还没 commit。
- GitHub push 认证还没处理。
- 本地服务器当前未运行。
- Manifest 仍使用局域网 HTTP URL，不是正式 CDN/HTTPS。
- Release 正式签名配置未做。
- 后台播放、通知栏媒体控制还不是 release-quality。
- 尚未做 2026-06-27 当日的 Android 实机复测。
- 原游戏 radio 动画效果还未移植。
- SWF 不建议直接在 Android 运行，后续更适合用 Compose Canvas / 原生绘制复刻视觉效果。

## 推荐下一步

1. 获取当前电脑 LAN IP。
2. 若 IP 不同，重新运行 `convert-radio-assets.ps1` 生成 manifest。
3. 启动 `tools/serve-radio-assets.py`。
4. 手机浏览器访问 `http://<电脑IP>:8088/manifest.json`，确认可达。
5. 构建并安装 `onlineDebug`。
6. 实机测试：
   - 首次播放来源应为 `Remote`
   - 播放过的 track 应进入缓存
   - 再次命中应显示 `Cached`
   - `Clear Cache` 后缓存大小归零
   - `SongsOnly` 只播放 songs
   - `FullRadio` 继续按原版 state machine 调度
7. 测试通过后提交 Phase 2：
   - `README.md`
   - `app/build.gradle.kts`
   - `app/src/main/AndroidManifest.xml`
   - `tools/convert-radio-assets.ps1`
   - `tools/serve-radio-assets.py`
   - `docs/*.md`
8. 处理 GitHub 登录并 push。
9. 开始下一阶段：真实 CDN/HTTPS 配置与原版 radio 动画效果移植。
