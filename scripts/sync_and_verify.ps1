#Requires -Version 5.1
$ErrorActionPreference = "Continue"
$RepoUrl = "https://github.com/CODE-ops-stack/calm.git"

Write-Host "=== Swatantra v7.1 Antigravity Build, Log Capture & GitHub Sync ===" -ForegroundColor Cyan

# 1. Run Gradle build if gradlew.bat exists and capture output to error.log
if (Test-Path ".\gradlew.bat") {
    Write-Host "[BUILD] Running .\gradlew.bat assembleRelease..."
    .\gradlew.bat assembleRelease --stacktrace *>&1 | Tee-Object -Variable buildOut
    $buildOut | Out-File -FilePath "error.log" -Encoding utf8
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
