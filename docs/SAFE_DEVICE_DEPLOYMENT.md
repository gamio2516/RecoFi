# RecoFi実機更新の安全手順

## なぜ専用の署名鍵が必要か

Androidの既定Debug鍵はWindowsユーザーごとの`%USERPROFILE%\.android\debug.keystore`です。PCを替えると別の証明書になり、既存RecoFiへ上書きできません。署名不一致時にアプリを削除すると、Roomを含むアプリ専用データも削除されます。

## RecoFi開発用keystoreの管理

1. まず現在Pixel 8に入っているRecoFiの署名と一致する鍵を基準鍵にする。現在のデスクトップDebug鍵で正常に上書きできている場合は、その`debug.keystore`を安全に複製してRecoFi専用鍵として保管する。実データ入りアプリと異なる新規鍵へ切り替えてはいけない。
2. keystore本体、alias、store password、key passwordをパスワード管理庫または暗号化された外部保管へ保存する。
3. 新しいPCでは同じkeystoreをGit管理外のローカルフォルダへ復元する。
4. `keystore.properties`へローカルパスと秘密情報を設定する。keystore、properties、秘密鍵をGitHubへcommitしない。
5. 新PCの最初の実機更新前に、安全デプロイスクリプトを`-PreflightOnly`で実行し、既存APKと候補APKのSHA-256証明書が一致することを確認する。

既存アプリと一致する秘密鍵を失っている場合、新しい鍵を作っても上書きはできません。その場合も既存アプリを削除せず、RecoFi内で検証済みバックアップを確保してから対応方針を決めます。

現在のプロジェクトは秘密情報をリポジトリへ持ち込まず、デプロイ時にAPKの実署名を比較します。専用keystoreをGradleのDebug signingConfigへ接続する場合も、値はGit管理外の`keystore.properties`からのみ読み込んでください。

`keystore.properties`の形式は次のとおりです（実値はパスワード管理庫から設定します）。

```properties
storeFile=C:/secure/recofi-development.jks
storePassword=...
keyAlias=recofi-development
keyPassword=...
```

このファイルが存在するPCではDebug buildへ専用署名が適用されます。未設定時はビルドに警告が表示され、安全デプロイスクリプトが既存アプリとの署名一致を最終確認します。

## 実機更新

先にRecoFiの「バックアップと復元」で最新バックアップの成功を確認します。その後、Android Studio Terminalから実行します。

```powershell
.\gradlew.bat assembleDebug
.\scripts\safe-deploy.ps1 `
  -TargetSerial "実機serial" `
  -TargetKind Physical `
  -ExpectedModel "Pixel 8" `
  -BackupConfirmed `
  -PreflightOnly
```

表示されたmodel、serial、既存APKと更新APKのfingerprintを確認後、`-PreflightOnly`だけを外して再実行します。

```powershell
.\scripts\safe-deploy.ps1 `
  -TargetSerial "実機serial" `
  -TargetKind Physical `
  -ExpectedModel "Pixel 8" `
  -BackupConfirmed
```

このスクリプトが行うインストールは`adb install -r`だけです。署名不一致、端末不一致、既存RecoFiなし、バックアップ未確認では停止します。`adb uninstall`、`pm clear`、Clear Dataは実装していません。

エミュレーターの場合も`-TargetKind Emulator`を明示し、実機と同じコマンドで使い分けます。
