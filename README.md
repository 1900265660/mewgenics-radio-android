# Mewgenics Radio Android

Mewgenics 电台模式的 Android 独立播放器。从游戏提取音频资源，通过 CDN 在线流式播放 547 首曲目，含原版六层音乐可视化动画。

## 下载

[GitHub Releases](https://github.com/1900265660/mewgenics-radio-android/releases)

| APK | 说明 |
|-----|------|
| `app-onlineDebug.apk` | CDN 在线播放（推荐） |
| `app-debug.apk` | 本地内置音频 |

## 功能

- 547 首完整曲目，Full Radio / Songs Only 切换
- Compose Canvas 六层动画复刻原版可视化
- 在线流媒体播放 + 按需缓存

## 本地构建

需要 Android Studio 和 SDK。

### 提取本地资源

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\extract-radio-assets.ps1
```

默认从 Steam 安装目录提取，输出到 `app/src/debug/assets/radio/`。

### 构建

```powershell
# debug（本地资源）
.\gradlew.bat :app:assembleDebug

# onlineDebug（CDN 资源）
.\gradlew.bat :app:assembleOnlineDebug
```

### CDN 资源准备

```powershell
# 安装 ffmpeg
winget install --id Gyan.FFmpeg

# 转换 + 生成 manifest
powershell -ExecutionPolicy Bypass -File .\tools\convert-radio-assets.ps1 -BitrateKbps 128 -BaseUrl "https://your-cdn.example/radio/v1/"

# 本地测试服务器
python .\tools\serve-radio-assets.py --root .\dist\radio-assets\128kbps --host 0.0.0.0 --port 8088
```

## 限制

- 动画为颜色近似复刻，非 SWF 精确还原
- 无后台播放
- Release 构建需要 CDN manifest URL
