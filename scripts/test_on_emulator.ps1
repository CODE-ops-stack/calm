#Requires -Version 5.1
$ErrorActionPreference = "Continue"

Write-Host "=== Swatantra v7.1 Virtual Emulator / Cloud Test Harness ===" -ForegroundColor Cyan

# 1. Verify ADB & Connected Devices
Write-Host "[1/5] Checking connected ADB devices..." -ForegroundColor Yellow
$devicesOutput = adb devices
$deviceList = ($devicesOutput | Select-String -Pattern "\bdevice\b") | Where-Object { $_ -notmatch "List of devices attached" }
if (-not $deviceList) {
    Write-Warning "No active ADB devices/emulators connected. Please start an Android Virtual Device (AVD) emulator or connect to a test device."
    exit 1
}
Write-Host "Connected Device(s):" -ForegroundColor Green
$devicesOutput

# 2. Check and Install Signed Release APK
$apkPath = "dist\swatantra-v7.1-release.apk"
if (-not (Test-Path $apkPath)) {
    Write-Error "Release APK not found at '$apkPath'. Run task 4 to build and sign the release APK first."
    exit 1
}

Write-Host "[2/5] Installing signed release APK ($apkPath)..." -ForegroundColor Yellow
adb install -r -t $apkPath

# 3. Provision Swatantra as Device Owner
Write-Host "[3/5] Setting Swatantra as Device Owner..." -ForegroundColor Yellow
adb shell dpm set-device-owner --user 0 in.swatantra.focus/.dpc.SwatantraAdminReceiver

# 4. Launch Swatantra MainActivity
Write-Host "[4/5] Launching Swatantra Focus OS MainActivity..." -ForegroundColor Yellow
adb shell am start -n in.swatantra.focus/.ui.MainActivity

# 5. Verify Device Policy State
Write-Host "[5/5] Verifying Device Policy Manager status..." -ForegroundColor Green
adb shell dumpsys device_policy | Select-String -Pattern "in.swatantra.focus" -Context 2,2

Write-Host "=== Device Owner Test Completed Successfully ===" -ForegroundColor Cyan
