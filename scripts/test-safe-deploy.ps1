$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'safe-deploy.ps1') -TargetSerial test -TargetKind Emulator -ExpectedModel test -PreflightOnly

function Assert-True([bool]$Value, [string]$Message) { if (-not $Value) { throw $Message } }

Assert-True (Test-SigningMatch 'AA:BB' 'aa:bb') '同一署名が許可されませんでした。'
Assert-True (-not (Test-SigningMatch 'AA:BB' 'CC:DD')) '異なる署名が許可されました。'
Assert-True ((Get-DeviceKind '1') -eq 'Emulator') 'エミュレーター判定に失敗しました。'
Assert-True ((Get-DeviceKind '0') -eq 'Physical') '実機判定に失敗しました。'
$scriptText = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'safe-deploy.ps1')
Assert-True (-not ($scriptText -match '(?im)adb\s+uninstall|pm\s+clear|clear\s+data')) '禁止された削除コマンドが含まれています。'
Assert-True ($scriptText.Contains('install -r')) '上書きインストールが使われていません。'
Write-Host 'safe-deploy safety tests passed'
