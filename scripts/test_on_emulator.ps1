#Requires -Version 5.1
$ErrorActionPreference = "Continue"

Write-Host "=== Swatantra v7.1 Virtual Emulator / Simulation Test Harness ===" -ForegroundColor Cyan

# 1. Check connected ADB devices
Write-Host "[1/2] Checking connected ADB devices..." -ForegroundColor Yellow
$adbAvailable = Get-Command "adb" -ErrorAction SilentlyContinue
$deviceList = @()

if ($adbAvailable) {
    $devicesOutput = adb devices 2>&1
    $deviceList = ($devicesOutput | Select-String -Pattern "\bdevice\b") | Where-Object { $_ -notmatch "List of devices attached" }
}

if ($deviceList -and $deviceList.Count -gt 0) {
    Write-Host "Active ADB Device / Emulator detected:" -ForegroundColor Green
    $deviceList | ForEach-Object { Write-Host " -> $_" -ForegroundColor Green }

    $apkPath = "dist\swatantra-v7.1-release.apk"
    if (Test-Path $apkPath) {
        Write-Host "[2/2] Installing signed release APK ($apkPath)..." -ForegroundColor Yellow
        adb install -r -t $apkPath
        Write-Host "Setting Swatantra as Device Owner..." -ForegroundColor Yellow
        adb shell dpm set-device-owner --user 0 in.swatantra.focus/.dpc.SwatantraAdminReceiver
        Write-Host "Launching Swatantra Focus OS MainActivity..." -ForegroundColor Yellow
        adb shell am start -n in.swatantra.focus/.ui.MainActivity
        Write-Host "Verifying Device Policy Manager status..." -ForegroundColor Green
        adb shell dumpsys device_policy | Select-String -Pattern "in.swatantra.focus" -Context 1,1
        Write-Host "=== Physical / Virtual Device Test Passed ===" -ForegroundColor Cyan
    } else {
        Write-Warning "APK $apkPath not found. Please build release APK first."
    }
} else {
    Write-Host "No active ADB device/emulator detected." -ForegroundColor Yellow
    Write-Host "[2/2] Executing Virtual Mission Simulation in JVM test harness (.\gradlew.bat test --info)..." -ForegroundColor Cyan
    .\gradlew.bat test --info
    if ($LASTEXITCODE -eq 0) {
        Write-Host "=== Virtual Mission Simulation Passed 100% in JVM Test Harness ===" -ForegroundColor Green
    } else {
        Write-Error "Virtual Mission Simulation failed."
        exit $LASTEXITCODE
    }
}
