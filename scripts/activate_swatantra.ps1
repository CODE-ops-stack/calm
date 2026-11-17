#Requires -Version 5.1
$ErrorActionPreference = "Stop"

Write-Host "=== Swatantra v7.1 Zero-Harm Pre-Flight & Device Owner Activator ===" -ForegroundColor Cyan

$devices = (adb devices | Select-String -Pattern "\tdevice$").Count
if ($devices -ne 1) {
    Write-Error "Connect exactly 1 Android device with USB Debugging authorized (found $devices)."
    exit 1
}

$policyDump = adb shell dumpsys device_policy
if ($policyDump -match "com\.samsung\.android\.kgclient|DeviceLockController") {
    Write-Warning "SAFETY ABORT: Active Samsung Knox Guard / Carrier EMI lock detected. Skipping Device Owner to avoid financing lock conflict."
    exit 1
}

$brand = (adb shell getprop ro.product.brand).Trim().ToLower()
if ($brand -match "xiaomi|redmi|poco") {
    $miuiSec = (adb shell getprop persist.security.adbinput).Trim()
    if ($miuiSec -ne "1") {
        Write-Warning "XIAOMI DETECTED: Complete these 3 steps on the phone first:"
        Write-Host "  1. Enable 'USB debugging (Security settings)' and disable 'MIUI optimization' in Developer Options."
        Write-Host "  2. Sign out of Mi Account in Settings -> Mi Account."
        Write-Host "  3. Turn on Airplane Mode and re-run this script."
        exit 1
    }
}

$usersOutput = adb shell pm list users
$userIds = [regex]::Matches($usersOutput, 'UserInfo\{(\d+):') | ForEach-Object { $_.Groups[1].Value }
foreach ($uid in $userIds) {
    if ($uid -ne "0") {
        Write-Host "[CLEANUP] Removing non-primary/clone user profile ID: $uid"
        adb shell am stop-user -f $uid | Out-Null
        adb shell pm remove-user $uid | Out-Null
    }
}

$acctDump = adb shell dumpsys account
$authPkgs = [regex]::Matches($acctDump, 'ComponentInfo\{([a-zA-Z0-9_\.]+)/') |
    ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique |
    Where-Object { $_ -notmatch '^com\.android\.|^android$|^com\.google\.android\.gms$|^com\.google\.android\.gsf$|^in\.swatantra\.focus$' }

try {
    foreach ($pkg in $authPkgs) { adb shell pm disable-user --user 0 $pkg | Out-Null }
    adb shell dpm set-device-owner --user 0 in.swatantra.focus/.dpc.SwatantraAdminReceiver
    Write-Host "[SUCCESS] Swatantra v7.1 provisioned as Device Owner!" -ForegroundColor Green
} finally {
    foreach ($pkg in $authPkgs) { adb shell pm enable --user 0 $pkg | Out-Null }
}

adb shell dumpsys deviceidle whitelist +in.swatantra.focus | Out-Null
adb shell cmd appops set in.swatantra.focus RUN_IN_BACKGROUND allow
adb shell cmd appops set in.swatantra.focus RUN_ANY_IN_BACKGROUND allow
adb shell cmd appops set in.swatantra.focus SCHEDULE_EXACT_ALARM allow
adb shell cmd appops set in.swatantra.focus SYSTEM_ALERT_WINDOW allow
adb shell cmd appops set in.swatantra.focus AUTO_REVOKE_PERMISSIONS_IF_UNUSED ignore
adb shell settings put global adb_allowed_connection_time 0
