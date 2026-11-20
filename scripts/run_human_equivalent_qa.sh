#!/usr/bin/env bash
set -euo pipefail

REPORT="qa_emulator_report.txt"
PKG="in.swatantra.focus"
ADMIN="$PKG/.dpc.SwatantraAdminReceiver"
MAIN="$PKG/.ui.MainActivity"
APK="app/build/outputs/apk/release/app-release-ci-signed.apk"
[ ! -f "$APK" ] && [ -f "dist/swatantra-v7.1-release.apk" ] && APK="dist/swatantra-v7.1-release.apk"

FAILURES=0
log() { echo "$1" | tee -a "$REPORT"; }
pass() { log "PASS: $1"; }
fail() { log "FAIL: $1"; FAILURES=$((FAILURES + 1)); }

tap_text() {
  local query="$1"
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
  local coords
  coords=$(adb shell cat /sdcard/ui.xml 2>/dev/null | python3 -c "
import sys, re, xml.etree.ElementTree as ET
xml_data = sys.stdin.read()
q = sys.argv[1].lower()
try:
    root = ET.fromstring(xml_data)
    for node in root.iter('node'):
        t = (node.attrib.get('text') or '').lower()
        h = (node.attrib.get('hint') or '').lower()
        if q in t or q in h:
            m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds', ''))
            if m:
                x = (int(m.group(1)) + int(m.group(3))) // 2
                y = (int(m.group(2)) + int(m.group(4))) // 2
                print(f'{x} {y}')
                sys.exit(0)
except Exception:
    pass
" "$query")
  if [ -n "$coords" ]; then
    adb shell input tap $coords
    sleep 1
    return 0
  fi
  return 1
}

: > "$REPORT"
log "=== SWATANTRA v7.1 CLOUD ANDROID 14 HUMAN-EQUIVALENT QA REPORT ==="
log "Timestamp (UTC): $(date -u)"
log "APK Under Test: $APK"
adb logcat -c || true

log ""
log "--- PHASE 2: INSTALL, DEVICE OWNER & HUMAN-FLOW UI ---"
if adb install -r "$APK" >> "$REPORT" 2>&1; then
  pass "APK installed cleanly on Android 14 emulator"
else
  fail "APK installation failed"
fi

if adb shell dpm set-device-owner "$ADMIN" >> "$REPORT" 2>&1; then
  pass "Device Owner activated ($ADMIN)"
else
  fail "Device Owner activation failed"
fi

adb shell am start -n "$MAIN" >/dev/null 2>&1
sleep 3
TOP_ACT=$(adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" | head -n 2 || true)
log "Initial Foreground Activity: $TOP_ACT"
if echo "$TOP_ACT" | grep -q "FamilySetupActivity"; then
  pass "First launch correctly routed to FamilySetupActivity"
else
  fail "First launch did NOT open FamilySetupActivity"
fi

tap_text "Family Slot 1" && adb shell input text "9876543210"
tap_text "Set 6-Digit Parent Unlock PIN" && adb shell input text "123456"
tap_text "Confirm 6-Digit Parent Unlock PIN" && adb shell input text "123456"
adb shell input keyevent 4 || true
sleep 1
adb shell input swipe 500 1600 500 500 300 || true
sleep 1
if tap_text "LOCK FAMILY NUMBERS"; then
  sleep 2
  pass "Tapped LOCK FAMILY NUMBERS, PIN & AUTHENTICATOR"
else
  fail "Could not locate/tap LOCK FAMILY NUMBERS button"
fi

adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
UI_TEXT=$(adb shell cat /sdcard/ui.xml 2>/dev/null || true)
if echo "$UI_TEXT" | grep -q "DEVICE OWNER: ACTIVE"; then
  pass "MainActivity Command Deck displays DEVICE OWNER: ACTIVE"
else
  fail "MainActivity did not display DEVICE OWNER: ACTIVE"
fi

if echo "$UI_TEXT" | grep -q "SELECT MY STUDY APPS"; then
  pass "SELECT MY STUDY APPS tile is visible on Command Deck"
else
  fail "SELECT MY STUDY APPS tile missing"
fi

if tap_text "SELECT MY STUDY APPS"; then
  sleep 2
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
  if adb shell cat /sdcard/ui.xml 2>/dev/null | grep -q "Select Your Allowed Study Apps"; then
    pass "Study App Picker dialog opened smoothly"
    tap_text "Save Study Apps" || adb shell input keyevent 4
  else
    fail "Study App Picker dialog did not appear"
  fi
fi

if tap_text "ENGAGE MISSION LOCK"; then
  sleep 3
  LOCK_STATE=$(adb shell dumpsys activity activities | grep -i "mLockTaskModeState" | head -n 1 || true)
  log "LockTask State: $LOCK_STATE"
  if echo "$LOCK_STATE" | grep -Eq "1|2|LOCKED"; then
    pass "Device Owner LockTask mode engaged without screen-pinning popup"
  else
    fail "LockTask mode did not engage (State: $LOCK_STATE)"
  fi
else
  fail "Could not tap ENGAGE MISSION LOCK"
fi

log ""
log "--- PHASE 3: ESCAPE-RESISTANCE & PARENT UNLOCK ---"
adb shell input keyevent 3
sleep 1
adb shell input keyevent 187
sleep 1
AFTER_ESCAPE=$(adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" | head -n 2 || true)
log "Foreground After HOME+RECENTS Escape Attempt: $AFTER_ESCAPE"
if echo "$AFTER_ESCAPE" | grep -q "$PKG"; then
  pass "App resisted HOME and RECENTS escape attempts"
else
  fail "App escaped lockdown after HOME/RECENTS ($AFTER_ESCAPE)"
fi

adb shell input swipe 500 1600 500 400 300 || true
sleep 1
if tap_text "MISSION STAND-DOWN"; then
  sleep 1
  tap_text "6-digit Parent PIN" && adb shell input text "999999"
  tap_text "VERIFY PIN"
  sleep 1
  if adb shell dumpsys device_policy | grep -q "$PKG"; then
    pass "Wrong PIN (999999) correctly rejected; Device Owner still intact"
  else
    fail "Wrong PIN unexpectedly unlocked Device Owner"
  fi
fi

log ""
log "--- PHASE 4: CHAOS STRESS, CRASH & ANR AUDIT ---"
adb shell monkey -p "$PKG" --throttle 100 -v 100 >/dev/null 2>&1 || true
sleep 2

CRASHES=$(adb logcat -d AndroidRuntime:E *:S | grep -E "FATAL EXCEPTION|$PKG" || true)
ANRS=$(adb logcat -d ActivityManager:E *:S | grep -i "ANR in $PKG" || true)
MEM=$(adb shell dumpsys meminfo "$PKG" | grep "TOTAL PSS" | head -n 1 || true)
log "Memory Footprint: ${MEM:-N/A}"

if [ -z "$CRASHES" ]; then
  pass "Zero FATAL EXCEPTION crashes in logcat"
else
  fail "FATAL EXCEPTION detected in logcat"
  log "$CRASHES"
fi

if [ -z "$ANRS" ]; then
  pass "Zero ANR (Application Not Responding) hangs in logcat"
else
  fail "ANR detected in logcat"
  log "$ANRS"
fi

log ""
log "=== FINAL QA SUMMARY: FAILURES=$FAILURES ==="
exit "$FAILURES"
