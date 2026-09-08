[CmdletBinding()]
param(
    [string]$RuntimeDir,
    [string]$FirmwareBin,
    [string]$Version,
    [string]$OutputDir
)

$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$scriptDir = $PSScriptRoot

if (-not $OutputDir) {
    $OutputDir = Join-Path $repositoryRoot 'dist'
}

if (-not $RuntimeDir) {
    $runtimeCandidates = @(
        (Join-Path $repositoryRoot '.build\EFucoser_ULN2003_Flasher_Win32\runtime'),
        (Join-Path $scriptDir 'runtime')
    )
    $RuntimeDir = $runtimeCandidates |
        Where-Object { Test-Path (Join-Path $_ 'python.exe') } |
        Select-Object -First 1
}

if (-not $RuntimeDir) {
    throw 'Portable runtime was not found. Provide -RuntimeDir or place runtime under flasher\runtime.'
}
if (-not (Test-Path (Join-Path $RuntimeDir 'python.exe'))) {
    throw ('Portable runtime is missing python.exe: {0}' -f $RuntimeDir)
}
if (-not (Test-Path (Join-Path $RuntimeDir 'esptool.py'))) {
    throw ('Portable runtime is missing esptool.py: {0}' -f $RuntimeDir)
}

if (-not $FirmwareBin) {
    $compileDir = Join-Path $repositoryRoot 'build\flasher\compile'
    $buildBase = Join-Path $repositoryRoot 'build'
    $resolvedCompile = [System.IO.Path]::GetFullPath($compileDir)
    $resolvedBuildBase = [System.IO.Path]::GetFullPath($buildBase)
    if (-not $resolvedCompile.StartsWith($resolvedBuildBase, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Compile output must be inside the build directory.'
    }
    if (Test-Path $compileDir) {
        Remove-Item -LiteralPath $compileDir -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path $compileDir | Out-Null

    $arduinoCli = Get-Command arduino-cli -ErrorAction Stop
    & $arduinoCli.Source compile --fqbn esp8266:esp8266:d1_mini --output-dir $compileDir (Join-Path $repositoryRoot 'ESP8266FocuserFirmware')
    if ($LASTEXITCODE -ne 0) {
        throw ('arduino-cli compile failed with exit code {0}' -f $LASTEXITCODE)
    }

    $FirmwareBin = Get-ChildItem -LiteralPath $compileDir -Filter '*.bin' |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}

if (-not (Test-Path $FirmwareBin)) {
    throw ('Firmware file does not exist: {0}' -f $FirmwareBin)
}

if (-not $Version) {
    $inoPath = Join-Path $repositoryRoot 'ESP8266FocuserFirmware\ESP8266FocuserFirmware.ino'
    $inoText = Get-Content -LiteralPath $inoPath -Raw
    if ($inoText -match '#define\s+FIRMWARE_VERSION\s+(\d+)') {
        $Version = $Matches[1]
    } else {
        $Version = 'latest'
    }
}

$firmwareName = ('EFucoser_ESP8266_ULN2003_v{0}.bin' -f $Version)
$packageName = ('EFucoser_ESP8266_ULN2003_Flasher_Win32_v{0}' -f $Version)
$packageRoot = Join-Path (Join-Path $repositoryRoot 'build\flasher-package') $packageName
$buildBase = Join-Path $repositoryRoot 'build'
$resolvedPackage = [System.IO.Path]::GetFullPath($packageRoot)
$resolvedBuildBase = [System.IO.Path]::GetFullPath($buildBase)
if (-not $resolvedPackage.StartsWith($resolvedBuildBase, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Package output must be inside the build directory.'
}
if (Test-Path $packageRoot) {
    Remove-Item -LiteralPath $packageRoot -Recurse -Force
}
New-Item -ItemType Directory -Force -Path (Join-Path $packageRoot 'firmware') | Out-Null

Copy-Item -LiteralPath $FirmwareBin -Destination (Join-Path (Join-Path $packageRoot 'firmware') $firmwareName)
Copy-Item -LiteralPath (Join-Path $scriptDir 'flash.py') -Destination $packageRoot
Copy-Item -LiteralPath (Join-Path $scriptDir 'flash-firmware.bat') -Destination $packageRoot
Copy-Item -LiteralPath (Join-Path $scriptDir 'flash-erase.bat') -Destination $packageRoot
Copy-Item -LiteralPath (Join-Path $scriptDir 'README.md') -Destination (Join-Path $packageRoot 'README.md')
$usageFiles = Get-ChildItem -LiteralPath $scriptDir -Filter '*.txt'
foreach ($usageFile in $usageFiles) {
    Copy-Item -LiteralPath $usageFile.FullName -Destination (Join-Path $packageRoot 'README.txt') -Force
}
Copy-Item -LiteralPath $RuntimeDir -Destination (Join-Path $packageRoot 'runtime') -Recurse
$runtimeFlashPy = Join-Path $packageRoot 'runtime\flash.py'
if (Test-Path $runtimeFlashPy) {
    Remove-Item -LiteralPath $runtimeFlashPy -Force
}

$firmwareHash = Get-FileHash -LiteralPath $FirmwareBin -Algorithm SHA256
$manifest = [ordered]@{
    fileName = $firmwareName
    version = $Version
    sha256 = $firmwareHash.Hash
    chip = 'esp8266'
    baud = 115200
    flashMode = 'dio'
    flashFreq = '40m'
    flashSize = 'detect'
    offset = '0x00000'
} | ConvertTo-Json

$manifestPath = Join-Path $packageRoot 'firmware\manifest.json'
[System.IO.File]::WriteAllText(
    $manifestPath,
    $manifest,
    (New-Object System.Text.UTF8Encoding($false))
)

if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
}
$zipPath = Join-Path $OutputDir ('EFucoser_ESP8266_ULN2003_Flasher_Win32_v{0}.zip' -f $Version)
if (Test-Path $zipPath) {
    Remove-Item -LiteralPath $zipPath -Force
}

Compress-Archive -Path $packageRoot -DestinationPath $zipPath -CompressionLevel Optimal

$zipHash = Get-FileHash -LiteralPath $zipPath -Algorithm SHA256
Write-Output ('Firmware: {0}' -f $FirmwareBin)
Write-Output ('Version: {0}' -f $Version)
Write-Output ('SHA256: {0}' -f $firmwareHash.Hash)
Write-Output ('Package: {0}' -f $zipPath)
Write-Output ('Package size: {0} bytes' -f (Get-Item -LiteralPath $zipPath).Length)
Write-Output ('Package SHA256: {0}' -f $zipHash.Hash)
