param(
    [string]$GpakPath = "E:\SteamLibrary\steamapps\common\Mewgenics\resources.gpak",
    [string]$OutputRoot = "E:\Projects\mewgenics-radio-android\app\src\debug\assets\radio",
    [switch]$IncludeVisualizer
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -Path $GpakPath)) {
    throw "resources.gpak not found: $GpakPath"
}

New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null

$fs = [System.IO.File]::OpenRead($GpakPath)
$br = New-Object System.IO.BinaryReader($fs)

try {
    $count = $br.ReadUInt32()
    $entries = New-Object System.Collections.Generic.List[object]

    for ($i = 0; $i -lt $count; $i++) {
        $nameLength = $br.ReadUInt16()
        if ($nameLength -le 0 -or $nameLength -gt 512) {
            throw "Invalid entry name length $nameLength at index $i"
        }

        $nameBytes = $br.ReadBytes($nameLength)
        $name = [System.Text.Encoding]::UTF8.GetString($nameBytes)
        $size = $br.ReadUInt32()

        $entries.Add([pscustomobject]@{
            Name = $name
            Size = [uint32]$size
            Offset = [int64]0
        })
    }

    $offset = [int64]$fs.Position
    foreach ($entry in $entries) {
        $entry.Offset = $offset
        $offset += [int64]$entry.Size
    }

    $selected = @()
    foreach ($entry in $entries) {
        $isRadioConfig = $entry.Name -eq "audio/music/radio.gon"
        $isRadioAudio = $entry.Name -like "audio/music/radio/*.ogg" -or $entry.Name -like "audio/music/radio/*/*.ogg"
        $isVisualizer = $IncludeVisualizer -and $entry.Name -eq "swfs/music_visualizer.swf"

        if ($isRadioConfig -or $isRadioAudio -or $isVisualizer) {
            $selected += $entry
        }
    }

    if ($selected.Count -eq 0) {
        throw "No radio assets found in $GpakPath"
    }

    $bufferSize = 1024 * 1024
    $buffer = New-Object byte[] $bufferSize
    $totalBytes = [int64]0
    $written = 0

    foreach ($entry in $selected) {
        if ($entry.Name -eq "audio/music/radio.gon") {
            $relativeOut = "radio.gon"
        } else {
            $relativeOut = $entry.Name.Replace("/", [System.IO.Path]::DirectorySeparatorChar)
        }

        $outPath = Join-Path $OutputRoot $relativeOut
        $outDir = Split-Path -Parent $outPath
        New-Item -ItemType Directory -Force -Path $outDir | Out-Null

        [void]$fs.Seek($entry.Offset, [System.IO.SeekOrigin]::Begin)
        $out = [System.IO.File]::Create($outPath)
        try {
            $remaining = [int64]$entry.Size
            while ($remaining -gt 0) {
                $toRead = [Math]::Min($buffer.Length, $remaining)
                $read = $fs.Read($buffer, 0, [int]$toRead)
                if ($read -le 0) {
                    throw "Unexpected end of file while extracting $($entry.Name)"
                }
                $out.Write($buffer, 0, $read)
                $remaining -= $read
            }
        } finally {
            $out.Dispose()
        }

        $written++
        $totalBytes += [int64]$entry.Size
    }

    Write-Output "Extracted $written files"
    Write-Output "Total bytes: $totalBytes"
    Write-Output "Output: $OutputRoot"
} finally {
    $br.Dispose()
    $fs.Dispose()
}
