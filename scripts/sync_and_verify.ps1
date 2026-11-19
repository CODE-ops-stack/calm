#Requires -Version 5.1
$ErrorActionPreference = "Continue"
$RepoUrl = "https://github.com/CODE-ops-stack/calm.git"

Write-Host "=== Swatantra v7.1 Antigravity Build, Log Capture & GitHub Sync ===" -ForegroundColor Cyan

# 1. Run Gradle build if gradlew.bat exists and capture output to error.log
if (Test-Path ".\gradlew.bat") {
    Write-Host "[BUILD] Running .\gradlew.bat assembleRelease..."
    .\gradlew.bat assembleRelease --stacktrace *>&1 | Tee-Object -Variable buildOut
    $buildOut | Out-File -FilePath "error.log" -Encoding utf8

    $unsignedApk = "app\build\outputs\apk\release\app-release-unsigned.apk"
    if (Test-Path $unsignedApk) {
        $zipalign = "C:\Users\harsh\AppData\Local\Android\Sdk\build-tools\35.0.0\zipalign.exe"
        $apksigner = "C:\Users\harsh\AppData\Local\Android\Sdk\build-tools\35.0.0\apksigner.bat"
        if (-not (Test-Path "dist")) { New-Item -ItemType Directory -Path "dist" -Force }
        & $zipalign -v -p -f 4 $unsignedApk "dist\swatantra-v7.1-release.apk"
        & $apksigner sign --ks "swatantra-release.jks" --ks-pass pass:Swatantra@710 --ks-key-alias "swatantra_key" --key-pass pass:Swatantra@710 --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true "dist\swatantra-v7.1-release.apk"

        # Verify DEX symbols including TACTICAL_IF_THEN_DIRECTIVE_V7_1
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $zip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path "dist\swatantra-v7.1-release.apk"))
        $dexEntry = $zip.Entries | Where-Object { $_.FullName -eq "classes.dex" }
        if ($dexEntry) {
            $ms = New-Object System.IO.MemoryStream
            $dexEntry.Open().CopyTo($ms)
            $ascii = [System.Text.Encoding]::ASCII.GetString($ms.ToArray())
            $requiredSymbols = @(
                "SwatantraApp", "SwatantraAdminReceiver", "AtomicDeprovisionManager",
                "FourBucketClassifier", "StrictPlayStoreAppVerifier", "PackageInstallReceiver",
                "FastOtaUpdater", "UniversalLectureInspector", "AntiGossipCallEngine",
                "DirectBootCallScreener", "SwatantraInCallService", "MonotonicDualClockLedger",
                "SwatantraWatchdogService", "MainActivity", "FamilySetupActivity",
                "SafeUrlInterceptorActivity", "PRAGMA synchronous = FULL", "HmacSHA256",
                "isStaticMetadataEducational", "DISALLOW_FACTORY_RESET", "DISALLOW_NETWORK_RESET",
                "ON_A_MISSION_COMMAND_DECK_V7_1", "TACTICAL_IF_THEN_DIRECTIVE_V7_1",
                "ParentTotpAuthenticator", "PARENT_TOTP_RFC6238_V7_1"
            )
            foreach ($sym in $requiredSymbols) {
                $present = $ascii.Contains($sym)
                Write-Host "DEX_SYMBOL [$sym] Present in classes.dex: $present"
            }
        }
        $zip.Dispose()
    }
} else {
    "Gradle wrapper not initialized yet. Run 'gradle wrapper' in Antigravity terminal." | Out-File -FilePath "error.log" -Encoding utf8
}

# 2. Calculate completion percentage from TODO.md
if (Test-Path "TODO.md") {
    $content = Get-Content "TODO.md"
    $done = ($content | Select-String -SimpleMatch "- [x]").Count
    $pending = ($content | Select-String -SimpleMatch "- [ ]").Count
    $total = $done + $pending
    $pct = if ($total -gt 0) { [math]::Round(($done / $total) * 100, 1) } else { 100 }
    Write-Host "[PROGRESS] Completed: $done / $total tasks ($pct%)" -ForegroundColor Green
}

# 3. Initialize Git & Push to https://github.com/CODE-ops-stack/calm.git
if (-not (Test-Path ".git")) {
    git init
}
git branch -M main
$remotes = git remote
if ($remotes -notcontains "origin") {
    git remote add origin $RepoUrl
} else {
    git remote set-url origin $RepoUrl
}

git add .
git commit -m "chore(antigravity): sync Swatantra v7.1 build state and error.log"
git push -u origin main
git push origin main:master
Write-Host "[SYNC COMPLETE] Pushed latest state to $RepoUrl (main & master)" -ForegroundColor Cyan
