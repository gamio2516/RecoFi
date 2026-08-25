# RecoFi development setup

This document defines the supported setup for developing RecoFi on the desktop and notebook PCs.

## Authority and safety

- GitHub `main` = source-code authority.
- Pixel 8 Room database = household-data authority.
- Google Drive / OneDrive = backup storage, never a live Room database.
- Common RecoFi keystore = development signing identity.
- Never uninstall RecoFi, clear its app data, or copy/synchronize its live Room database between PCs.
- Never commit a keystore, private key, password, or `keystore.properties`.
- Use `scripts/safe-deploy.ps1` for every real Pixel 8 update. Use Android Studio Run only with emulators.

## Environment requirements

- Android Studio Quail 3 (2026.1.3 series)
- Android Studio bundled JDK, with Java 17 compatibility
- Android SDK Platform 36 and current Build-Tools
- Gradle 9.1.0 from the checked-in wrapper
- Android Gradle Plugin 8.13.2 and Kotlin 2.3.10
- `compileSdk`/`targetSdk` 36 and `minSdk` 26
- API 36 emulator for Room migration instrumentation tests

Do not upgrade build tools or dependencies as part of machine setup.

## Establish the common development key

The installed Pixel 8 app and current desktop Debug APK have this certificate SHA-256 fingerprint:

```text
2ed5c415ee79ef9f9a812e0493eb6176ae61effd3aec4a27aa8a782294928d0d
```

The original key was identified and verified at:

```text
C:\Users\knaka\.android\debug.keystore
```

Its certificate fingerprint was verified directly with `keytool`, and a Debug APK signed from the desktop common-key configuration passed `safe-deploy.ps1 -PreflightOnly` against the Pixel 8 with the same fingerprint. This key is therefore the established RecoFi common development key. The original `debug.keystore` must remain unchanged and must not be deleted.

The verification command is retained for recovery audits. Let `keytool` prompt for the password; never place the password in the command or terminal history.

```powershell
& "$env:JAVA_HOME\bin\keytool.exe" -list -v -keystore "$env:USERPROFILE\.android\debug.keystore"
```

If `JAVA_HOME` is unset, use Android Studio's Gradle JDK `keytool.exe` by its full path. The certificate SHA-256 value, ignoring case and colons, must equal the value above. If it differs or cannot be read, stop: do not copy or replace a key, reinstall RecoFi, or remove the installed app. Store the alias and passwords in a password manager, never in Git or this document.

## Back up the key before distributing it

Use encrypted removable storage, an encrypted vault, or password-manager file storage—not a public or unencrypted shared folder.

1. Retain the source; never move or delete the only copy.
2. Choose a new timestamped backup name such as `recofi-development-20260826.jks`; never overwrite an existing backup.
3. Copy the candidate to encrypted storage.
4. Compare source and copy using `Get-FileHash -Algorithm SHA256`.
5. Run `keytool -list -v` on the copy and verify the RecoFi certificate fingerprint above.
6. Store alias and passwords separately in the password manager.

The file hash proves an exact copy; the certificate fingerprint proves the Android signing identity. Check both.

## Install the common key on both PCs

Use this location relative to each Windows profile:

```text
%USERPROFILE%\.recofi\signing\recofi-development.jks
```

If a file already exists there, do not overwrite it. First record its file hash and certificate fingerprint and make a timestamped encrypted backup. Stop if its fingerprint differs. After copying the verified common key, verify its file hash and certificate fingerprint again. Both PCs must report the RecoFi fingerprint; exact copies should also have identical file hashes.

## Add the notebook PC

Do not connect the Pixel 8 until the notebook has built its APK and all local signing checks have passed. Never delete or overwrite the notebook's existing `%USERPROFILE%\.android\debug.keystore` or any existing common-key destination.

### 1. Export from the desktop

Attach an encrypted removable drive or mount another approved encrypted transfer medium. Replace `X:` below with that medium. The destination must be empty; the command stops rather than overwriting a file.

```powershell
$desktopKey = "$env:USERPROFILE\.recofi\signing\recofi-development.jks"
$transferKey = 'X:\RecoFi-key-transfer\recofi-development.jks'
$transferDirectory = Split-Path -Parent $transferKey

if (-not (Test-Path -LiteralPath $desktopKey -PathType Leaf)) {
    throw "Desktop common key not found: $desktopKey"
}
if (Test-Path -LiteralPath $transferKey) {
    throw "Transfer destination already exists; it will not be overwritten: $transferKey"
}

New-Item -ItemType Directory -Path $transferDirectory -Force | Out-Null
$desktopFileHash = (Get-FileHash -LiteralPath $desktopKey -Algorithm SHA256).Hash
Copy-Item -LiteralPath $desktopKey -Destination $transferKey
$transferFileHash = (Get-FileHash -LiteralPath $transferKey -Algorithm SHA256).Hash
if ($desktopFileHash -ne $transferFileHash) { throw 'Transfer copy hash mismatch. Stop.' }

[PSCustomObject]@{
    DesktopFileHash = $desktopFileHash
    TransferFileHash = $transferFileHash
    ExactCopy = $true
}
```

Record the resulting **file** SHA-256 in the password manager or another channel separate from the key file. Do not store it by editing the repository. Safely eject the encrypted medium.

### 2. Import on the notebook without overwriting anything

Start PowerShell on the notebook, attach the encrypted medium, and use the same drive-letter substitution. If the destination exists, the procedure stops. Inspect and back it up separately; do not add `-Force`.

```powershell
$transferKey = 'X:\RecoFi-key-transfer\recofi-development.jks'
$notebookDirectory = "$env:USERPROFILE\.recofi\signing"
$notebookKey = Join-Path $notebookDirectory 'recofi-development.jks'
$expectedFileHash = Read-Host 'Enter the desktop file SHA-256 from the separate trusted record'

if (-not (Test-Path -LiteralPath $transferKey -PathType Leaf)) {
    throw "Transfer key not found: $transferKey"
}
if (Test-Path -LiteralPath $notebookKey) {
    throw "Notebook destination already exists; it will not be overwritten: $notebookKey"
}

$transferFileHash = (Get-FileHash -LiteralPath $transferKey -Algorithm SHA256).Hash
if ($transferFileHash -ne $expectedFileHash.Trim()) { throw 'Transfer-medium hash mismatch. Stop.' }

New-Item -ItemType Directory -Path $notebookDirectory -Force | Out-Null
Copy-Item -LiteralPath $transferKey -Destination $notebookKey
$notebookFileHash = (Get-FileHash -LiteralPath $notebookKey -Algorithm SHA256).Hash
if ($notebookFileHash -ne $expectedFileHash.Trim()) { throw 'Notebook copy hash mismatch. Stop.' }

[PSCustomObject]@{
    TransferFileHash = $transferFileHash
    NotebookFileHash = $notebookFileHash
    ExactCopy = $true
}
```

Do not automatically delete the transfer copy. Remove it later using the encrypted medium's approved secure-erasure process only after both PCs are fully verified and an encrypted backup remains available.

### 3. Verify the certificate on the notebook

In Android Studio Terminal, run `keytool` and enter the password only at its prompt:

```powershell
& "$env:JAVA_HOME\bin\keytool.exe" -list -v `
  -keystore "$env:USERPROFILE\.recofi\signing\recofi-development.jks"
```

The certificate SHA-256, after ignoring colons and letter case, must be exactly:

```text
2ed5c415ee79ef9f9a812e0493eb6176ae61effd3aec4a27aa8a782294928d0d
```

Stop on any mismatch. Do not connect the Pixel 8, regenerate a key, overwrite another key, uninstall RecoFi, or clear app data.

### 4. Configure and build on the notebook

Create the ignored repository-root `keystore.properties` locally as described below. Retrieve the alias and passwords from the password manager; do not transfer this file with Git or with the keystore medium. Confirm ignore and tracking state before building:

```powershell
git check-ignore -v keystore.properties
git ls-files | Select-String -Pattern '(?i)(keystore\.properties|\.jks$|\.keystore$)'
.\gradlew.bat assembleDebug
```

The tracking query must return nothing and `assembleDebug` must succeed. Inspect the built APK certificate before using a real device if desired:

```powershell
$sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { $env:ANDROID_HOME }
$buildTools = Get-ChildItem -LiteralPath (Join-Path $sdk 'build-tools') -Directory |
  Sort-Object { try { [version]$_.Name } catch { [version]'0.0' } } -Descending |
  Select-Object -First 1
& (Join-Path $buildTools.FullName 'apksigner.bat') verify --print-certs `
  '.\outputs\CardMemo-debug.apk'
```

The candidate certificate must equal the RecoFi fingerprint above.

### 5. Pixel 8 preflight only

Only after all previous checks pass, connect the Pixel 8, create and verify a current in-app backup, and run `safe-deploy.ps1` with `-PreflightOnly` and the exact serial:

```powershell
.\scripts\safe-deploy.ps1 `
  -TargetSerial '<Pixel-8-serial>' `
  -TargetKind Physical `
  -ExpectedModel 'Pixel 8' `
  -BackupConfirmed `
  -PreflightOnly
```

Both `Installed APK SHA-256` and `Candidate APK SHA-256` must equal the RecoFi fingerprint. `PreflightOnly` must complete successfully. Do not install anything during notebook onboarding. A real update is permitted only in a later, intentional operation after this signature match has been recorded.

## Configure `keystore.properties`

Create `keystore.properties` in the repository root of each PC with secrets retrieved from the password manager:

```properties
storeFile=C:/Users/<WindowsUser>/.recofi/signing/recofi-development.jks
storePassword=<password-manager-value>
keyAlias=<keytool-reported-alias>
keyPassword=<password-manager-value>
```

Do not transfer this file through Git, chat, email, or a cloud source folder. `.gitignore` excludes `keystore.properties`, `*.jks`, and `*.keystore`. Verify that no secret is tracked:

```powershell
git check-ignore -v keystore.properties
git ls-files | Select-String -Pattern '(?i)(keystore\.properties|\.jks$|\.keystore$)'
```

The second command must return nothing. The common key is also outside the repository.

## Set up and validate a PC

```powershell
git clone <repository-url> C:\Users\<WindowsUser>\RecoFi
cd C:\Users\<WindowsUser>\RecoFi
git switch main
git pull --ff-only origin main
git status
```

Install the verified key, create the ignored `keystore.properties`, open the project, and run Gradle Sync. Then run:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat testReleaseUnitTest
.\gradlew.bat assembleDebug
```

On the dedicated API 36 emulator, run the migration tests as instrumentation tests:

```powershell
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=jp.knaka.cardmemo.storage.RoomMigration2To3Test"
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=jp.knaka.cardmemo.storage.RoomMigration3To4Test"
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=jp.knaka.cardmemo.storage.RoomMigration4To5Test"
```

Each command must report one passed test. The v4-to-v5 test includes foreign-key integrity verification. Never use the real-data Pixel 8 for migration tests.

Before the first Pixel 8 update from either PC, create and verify a current RecoFi backup and run:

```powershell
.\scripts\safe-deploy.ps1 `
  -TargetSerial '<Pixel-8-serial>' `
  -TargetKind Physical `
  -ExpectedModel 'Pixel 8' `
  -BackupConfirmed `
  -PreflightOnly
```

Installed and candidate fingerprints must match. Only then omit `-PreflightOnly` to perform the in-place `adb install -r` update. The script never uninstalls or clears data and stops on a signature mismatch.

## One person using two PCs

GitHub `main` is the only code authority. Never split uncommitted changes between PCs.

At the start of work:

```powershell
git switch main
git pull --ff-only origin main
git status
```

Start only when the tree is clean and local `main` matches `origin/main`. If another PC has unfinished work, finish, test, commit, and push it there rather than recreating uncommitted edits.

At the end of work:

1. Run tests appropriate to the change and `assembleDebug`.
2. Review `git status` and the complete diff.
3. Commit only intended files and push `main`.
4. Fetch and confirm local `main` equals `origin/main` and the tree is clean.

Pull that commit successfully before continuing on the other PC.

## Future multi-person workflow

The one-person/two-PC workflow may currently use `main` directly. When contributors are added, migrate to `feature/...` branches, Pull Requests, review, and merge into protected `main`. Share the development key only with authorized developers through encrypted secret storage; never attach it to a Pull Request or place it in GitHub history.
