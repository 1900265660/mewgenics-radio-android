param(
    [string]$InputRoot = "E:\Projects\mewgenics-radio-android\app\src\debug\assets\radio",
    [string]$OutputRoot = "E:\Projects\mewgenics-radio-android\dist\radio-assets",
    [string]$BaseUrl = "https://example.com/mewgenics-radio/v1/",
    [ValidateSet(96, 128)]
    [int]$BitrateKbps = 128
)

$ErrorActionPreference = "Stop"

function Require-Command($name) {
    $command = Get-Command $name -ErrorAction SilentlyContinue
    if (-not $command) {
        throw "$name was not found. Install ffmpeg and make sure $name is on PATH."
    }
}

Require-Command "ffmpeg"
Require-Command "ffprobe"

$radioGon = Join-Path $InputRoot "radio.gon"
$audioRoot = Join-Path $InputRoot "audio\music\radio"

if (-not (Test-Path -Path $radioGon)) {
    throw "radio.gon not found: $radioGon"
}
if (-not (Test-Path -Path $audioRoot)) {
    throw "Radio audio root not found: $audioRoot"
}

$qualityRoot = Join-Path $OutputRoot "$($BitrateKbps)kbps"
$outputAudioRoot = Join-Path $qualityRoot "audio\music\radio"
New-Item -ItemType Directory -Force -Path $outputAudioRoot | Out-Null

Copy-Item -Path $radioGon -Destination (Join-Path $qualityRoot "radio.gon") -Force

$tracks = New-Object System.Collections.Generic.List[object]
$inputFiles = Get-ChildItem -Path $audioRoot -Recurse -File -Filter "*.ogg"

foreach ($input in $inputFiles) {
    $relative = $input.FullName.Substring($audioRoot.Length + 1)
    $category = ($relative -split "\\")[0]
    $relativeNoExtension = [System.IO.Path]::ChangeExtension($relative, $null)
    $outputRelative = [System.IO.Path]::ChangeExtension($relative, ".opus")
    $outputPath = Join-Path $outputAudioRoot $outputRelative
    $outputDir = Split-Path -Parent $outputPath
    New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

    if (-not (Test-Path -Path $outputPath)) {
        & ffmpeg -hide_banner -loglevel error -y -i $input.FullName -c:a libopus -b:a "$($BitrateKbps)k" -vbr on $outputPath
        if ($LASTEXITCODE -ne 0) {
            throw "ffmpeg failed for $($input.FullName)"
        }
    }

    $durationRaw = & ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 $outputPath
    if ($LASTEXITCODE -ne 0) {
        throw "ffprobe failed for $outputPath"
    }

    $durationMs = [int64]([double]::Parse($durationRaw, [Globalization.CultureInfo]::InvariantCulture) * 1000)
    $hash = (Get-FileHash -Path $outputPath -Algorithm SHA256).Hash.ToLowerInvariant()
    $size = (Get-Item -Path $outputPath).Length
    $urlPath = ("audio/music/radio/" + ($outputRelative -replace "\\", "/"))
    $base = if ($BaseUrl.EndsWith("/")) { $BaseUrl } else { "$BaseUrl/" }

    $tracks.Add([ordered]@{
        id = [System.IO.Path]::GetFileNameWithoutExtension($outputPath)
        category = $category
        relativePath = $urlPath
        url = "$base$urlPath"
        bytes = $size
        sha256 = $hash
        durationMs = $durationMs
        codec = "opus"
        bitrateKbps = $BitrateKbps
    })
}

$manifest = [ordered]@{
    version = 1
    generatedAt = (Get-Date).ToUniversalTime().ToString("o")
    baseUrl = if ($BaseUrl.EndsWith("/")) { $BaseUrl } else { "$BaseUrl/" }
    codec = "opus"
    bitrateKbps = $BitrateKbps
    radioConfigPath = "radio.gon"
    trackCount = $tracks.Count
    tracks = $tracks
}

$manifestPath = Join-Path $qualityRoot "manifest.json"
$manifest | ConvertTo-Json -Depth 8 | Set-Content -Path $manifestPath -Encoding UTF8

$totalBytes = ($tracks | Measure-Object bytes -Sum).Sum
Write-Output "Converted tracks: $($tracks.Count)"
Write-Output "Total bytes: $totalBytes"
Write-Output "Manifest: $manifestPath"
