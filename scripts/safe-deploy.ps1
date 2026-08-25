param(
    [Parameter(Mandatory = $true)][string]$TargetSerial,
    [Parameter(Mandatory = $true)][ValidateSet('Physical', 'Emulator')][string]$TargetKind,
    [Parameter(Mandatory = $true)][string]$ExpectedModel,
    [string]$ApkPath = (Join-Path $PSScriptRoot '..\outputs\CardMemo-debug.apk'),
    [switch]$BackupConfirmed,
    [switch]$PreflightOnly
)

$ErrorActionPreference = 'Stop'
$PackageName = 'jp.knaka.cardmemo'

function Test-SigningMatch([string]$InstalledFingerprint, [string]$CandidateFingerprint) {
    return $InstalledFingerprint.Trim().ToUpperInvariant() -eq $CandidateFingerprint.Trim().ToUpperInvariant()
}

function Get-DeviceKind([string]$QemuValue) {
    if ($QemuValue.Trim() -eq '1') { return 'Emulator' }
    return 'Physical'
}

function Get-CertificateFingerprint([string]$ApkSigner, [string]$Path) {
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        # Windows PowerShell 5.1 can promote native stderr to NativeCommandError
        # when the caller uses Stop. Capture it locally and decide by exit code.
        $ErrorActionPreference = 'Continue'
        $output = & $ApkSigner verify --print-certs $Path 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    $outputLines = @($output | ForEach-Object { $_.ToString() })
    if ($exitCode -ne 0) {
        throw "Unable to verify the APK signature: $Path`n$($outputLines -join [Environment]::NewLine)"
    }

    $digestPattern = '^Signer #1 certificate SHA-256 digest:\s*([0-9A-Fa-f:]+)\s*$'
    $digestMatch = $outputLines | Select-String -Pattern $digestPattern | Select-Object -First 1
    if (-not $digestMatch) { throw "Unable to read the SHA-256 signing fingerprint: $Path" }

    $fingerprint = $digestMatch.Matches[0].Groups[1].Value
    if (($fingerprint -replace ':', '') -notmatch '^[0-9A-Fa-f]{64}$') {
        throw "Invalid SHA-256 signing fingerprint returned for APK: $Path"
    }
    return $fingerprint
}

function Invoke-RecoFiSafeDeploy {
    $sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
    $adb = Join-Path $sdk 'platform-tools\adb.exe'
    $buildToolsDirectory = Get-ChildItem -LiteralPath (Join-Path $sdk 'build-tools') -Directory | Sort-Object { try { [version]$_.Name } catch { [version]'0.0' } } -Descending | Select-Object -First 1
    $apksigner = if ($buildToolsDirectory) { Join-Path $buildToolsDirectory.FullName 'apksigner.bat' } else { $null }
    if (-not (Test-Path -LiteralPath $adb)) { throw "adb was not found: $adb" }
    if (-not $apksigner) { throw 'apksigner was not found. Verify that Android SDK Build-Tools is installed.' }
    $resolvedApk = (Resolve-Path -LiteralPath $ApkPath).Path

    Write-Host 'Connected devices:'
    & $adb devices -l
    if ($LASTEXITCODE -ne 0) { throw 'Unable to list connected devices.' }
    $state = (& $adb -s $TargetSerial get-state 2>$null).Trim()
    if ($state -ne 'device') { throw "The target device is not available: $TargetSerial" }
    $model = (& $adb -s $TargetSerial shell getprop ro.product.model).Trim()
    $kind = Get-DeviceKind ((& $adb -s $TargetSerial shell getprop ro.kernel.qemu).Trim())
    Write-Host "Target: serial=$TargetSerial / model=$model / kind=$kind"
    if ($kind -ne $TargetKind) { throw "Device kind mismatch. Expected=$TargetKind Actual=$kind" }
    if ($model -ne $ExpectedModel) { throw "Device model mismatch. Expected=$ExpectedModel Actual=$model" }
    if ($TargetKind -eq 'Physical' -and -not $BackupConfirmed) {
        throw 'A current backup has not been confirmed. Create and verify a RecoFi backup, then specify -BackupConfirmed.'
    }

    $packagePathLine = (& $adb -s $TargetSerial shell pm path $PackageName) | Where-Object { $_ -match '^package:.+/base\.apk$' } | Select-Object -First 1
    if ($LASTEXITCODE -ne 0 -or -not $packagePathLine) {
        throw 'RecoFi is not installed on the target device. Stopping because this would not be a safe in-place update.'
    }
    $remoteApk = $packagePathLine.Substring('package:'.Length).Trim()
    $tempDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ("recofi-preflight-" + [Guid]::NewGuid().ToString('N'))
    $installedApk = Join-Path $tempDirectory 'installed-base.apk'
    New-Item -ItemType Directory -Path $tempDirectory | Out-Null
    try {
        & $adb -s $TargetSerial pull $remoteApk $installedApk | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Unable to retrieve the installed RecoFi APK for signature verification.' }
        $installedFingerprint = Get-CertificateFingerprint $apksigner $installedApk
        $candidateFingerprint = Get-CertificateFingerprint $apksigner $resolvedApk
        Write-Host "Installed APK SHA-256: $installedFingerprint"
        Write-Host "Candidate APK SHA-256: $candidateFingerprint"
        if (-not (Test-SigningMatch $installedFingerprint $candidateFingerprint)) {
            throw 'Signing fingerprints do not match. Update is blocked. Removing the installed app would also delete its data. This script will not uninstall it.'
        }
        Write-Host 'Preflight passed: target, installed app, signing fingerprint, and backup confirmation are valid.'
        if ($PreflightOnly) { return }
        & $adb -s $TargetSerial install -r $resolvedApk
        if ($LASTEXITCODE -ne 0) { throw 'The adb install -r update failed. The installed app was not removed.' }
        Write-Host 'RecoFi was updated safely in place.'
    } finally {
        if (Test-Path -LiteralPath $installedApk) { Remove-Item -LiteralPath $installedApk -Force }
        if (Test-Path -LiteralPath $tempDirectory) { Remove-Item -LiteralPath $tempDirectory -Force }
    }
}

if ($MyInvocation.InvocationName -ne '.') { Invoke-RecoFiSafeDeploy }
