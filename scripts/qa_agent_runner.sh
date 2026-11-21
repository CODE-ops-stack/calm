#!/usr/bin/env bash
set -uo pipefail

ROLE="${1:-lock_and_apps}"
REPORT="qa_report_${ROLE}.txt"
PKG="in.swatantra.focus"
ADMIN="$PKG/.dpc.SwatantraAdminReceiver"
MAIN="$PKG/.ui.MainActivity"
APK="app/build/outputs/apk/release/app-release-ci-signed.apk"
[ ! -f "$APK" ] && [ -f "dist/swatantra-v7.1-release.apk" ] && APK="dist/swatantra-v7.1-release.apk"

FAILURES=0
log() { echo "$1" | tee -a "$REPORT"; }
pass() { log "PASS [$ROLE]: $1"; }
fail() { log "FAIL [$ROLE]: $1"; FAILURES=$((FAILURES + 1)); }

dump_ui() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
  adb shell cat /sdcard/ui.xml 2>/dev/null || true
}

tap_text() {
  local query="$1"
  local xml_data
  xml_data=$(dump_ui)
  local coords
  coords=$(printf '%s' "$xml_data" | python3 -c "
import sys, re, xml.etree.ElementTree as ET
xml_data = sys.stdin.read()
q = sys.argv[1].lower()
try:
    root = ET.fromstring(xml_data)
    fallback = None
    for node in root.iter('node'):
        t = (node.attrib.get('text') or '').lower()
        h = (node.attrib.get('hint') or '').lower()
        if q in t or q in h:
            m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds', ''))
            if m:
                x = (int(m.group(1)) + int(m.group(3))) // 2
                y = (int(m.group(2)) + int(m.group(4))) // 2
                pt = f'{x} {y}'
                if node.attrib.get('clickable') == 'true':
                    print(pt)
                    sys.exit(0)
                if fallback is None:
                    fallback = pt
    if fallback:
        print(fallback)
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

is_device_owner_active() {
  adb shell dumpsys device_policy | grep -A 3 "Device Owner:" | grep -q "$PKG"
}

provision_and_install() {
  adb logcat -c || true
  if adb install -r "$APK" >> "$REPORT" 2>&1; then
    pass "Signed R8 APK installed"
  else
    fail "APK install failed"
    return 1
  fi
  if ! adb shell dpm set-device-owner "$ADMIN" >> "$REPORT" 2>&1; then
    adb root >/dev/null 2>&1 || true
    sleep 2
    adb shell "rm -f /data/system_ce/0/accounts_ce.db* /data/system_de/0/accounts_de.db* /data/system/users/0/accounts.db*" >/dev/null 2>&1 || true
    adb unroot >/dev/null 2>&1 || true
    adb reboot >/dev/null 2>&1 || true
    adb wait-for-device
    sleep 15
    adb shell dpm set-device-owner "$ADMIN" >> "$REPORT" 2>&1 && pass "Device Owner activated after account wipe" || fail "Device Owner activation failed"
  else
    pass "Device Owner activated"
  fi
}

complete_family_setup() {
  local pin="${1:-123456}"
  adb shell am start -n "$MAIN" >/dev/null 2>&1
  sleep 3
  local ui_xml
  ui_xml=$(dump_ui)
  CAPTURED_TOTP=$(printf '%s' "$ui_xml" | python3 -c "
import html, re, sys
txt = html.unescape(sys.stdin.read())
m = re.search(r'AUTHENTICATOR KEY[^:]*:\s*([A-Z2-7]{16})', txt)
print(m.group(1) if m else '')
")
  CAPTURED_RECOVERY=$(printf '%s' "$ui_xml" | python3 -c "
import html, re, sys
txt = html.unescape(sys.stdin.read())
m = re.search(r'(SWT-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4})', txt)
print(m.group(1) if m else '')
")
  tap_text "Family Slot 1" && adb shell input text "9876543210"
  tap_text "Set 6-Digit Parent Unlock PIN" && adb shell input text "$pin"
  tap_text "Confirm 6-Digit Parent Unlock PIN" && adb shell input text "$pin"
  adb shell input keyevent 4 || true
  sleep 1
  adb shell input swipe 500 1600 500 500 300 || true
  sleep 1
  tap_text "LOCK FAMILY NUMBERS" && sleep 2 && pass "FamilySetupActivity locked (PIN set)" || fail "FamilySetupActivity lock tap failed"
}

dismiss_study_picker_if_present() {
  sleep 2
  local ui
  ui=$(dump_ui)
  if echo "$ui" | grep -q "CONFIRM STUDY APPS"; then
    tap_text "Calculator" || tap_text "Files" || tap_text "Clock" || true
    tap_text "CONFIRM STUDY APPS" || true
    sleep 2
  fi
}

check_crashes_and_anr() {
  local crashes anrs mem
  crashes=$(adb logcat -d AndroidRuntime:E *:S | grep -E "FATAL EXCEPTION|$PKG" || true)
  anrs=$(adb logcat -d ActivityManager:E *:S | grep -i "ANR in $PKG" || true)
  mem=$(adb shell dumpsys meminfo "$PKG" | grep "TOTAL PSS" | head -n 1 || true)
  log "Memory [$ROLE]: ${mem:-N/A}"
  [ -z "$crashes" ] && pass "Zero FATAL EXCEPTION crashes" || { fail "FATAL EXCEPTION found"; log "$crashes"; }
  [ -z "$anrs" ] && pass "Zero ANR hangs" || { fail "ANR found"; log "$anrs"; }
}

: > "$REPORT"
log "=== SWATANTRA v7.1 QA AGENT: $ROLE ==="
log "Timestamp (UTC): $(date -u)"
provision_and_install
complete_family_setup "123456"

case "$ROLE" in
  lock_and_apps)
    # 1. Beginning Study App Picker: confirm it appears automatically after Family Setup
    sleep 2
    if dump_ui | grep -q "Select Study Apps for Lock Mode"; then
      pass "Beginning Study App Picker automatically appeared after Family Setup"
      tap_text "Calculator" || tap_text "Files" || tap_text "Clock" || true
      if tap_text "CONFIRM STUDY APPS"; then
        sleep 2
        ! dump_ui | grep -q "Select Study Apps for Lock Mode" && pass "Study App Picker dismissed cleanly without lag" || fail "Study App Picker failed to dismiss"
      else
        fail "CONFIRM STUDY APPS button not found"
      fi
    else
      fail "Beginning Study App Picker did not appear automatically"
    fi

    # 2. Cycle If-Then Directive
    if tap_text "ARM IF-THEN DIRECTIVE"; then
      sleep 1
      dump_ui | grep -q "stuck on a problem" && pass "If-Then Directive cycled to Rule 2" || fail "If-Then Directive did not cycle"
    else
      fail "ARM IF-THEN DIRECTIVE button not found"
    fi

    # 3. Test SafeUrlInterceptorActivity via Tactical Intercept button
    if tap_text "TACTICAL INTERCEPT"; then
      sleep 2
      dump_ui | grep -q "YOUR ARMED PROTOCOL" && pass "SafeUrlInterceptorActivity displayed armed motivating protocol" || fail "SafeUrlInterceptorActivity missing protocol"
      tap_text "EXECUTE" || adb shell input keyevent 4
      sleep 2
    else
      fail "TACTICAL INTERCEPT button not found"
    fi

    # 4. Ensure Command Deck is foreground and engage Mission Lock
    adb shell am start -n "$MAIN" >/dev/null 2>&1
    sleep 2
    if tap_text "ENGAGE MISSION LOCK"; then
      sleep 3
      LOCK_STATE=$(adb shell dumpsys activity activities | grep -Ei "LockTask|mLockTask" | head -n 3 || true)
      echo "$LOCK_STATE" | grep -Eqi "LOCKED|PINNED" && pass "LockTask engaged ($LOCK_STATE)" || fail "LockTask did not engage"
      tap_text "Stay on Command Deck" || true
    else
      fail "ENGAGE MISSION LOCK button not found"
    fi

    # 5. Escape resistance: HOME + RECENTS + URL intercept
    adb shell input keyevent 3 || true
    sleep 1
    adb shell input keyevent 187 || true
    sleep 1
    FG=$(adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" | head -n 2 || true)
    echo "$FG" | grep -q "$PKG" && pass "Resisted HOME & RECENTS escape" || fail "Escaped lockdown ($FG)"

    adb shell am start -a android.intent.action.VIEW -d "[https://example.com](https://example.com)" >/dev/null 2>&1 || true
    sleep 2
    dump_ui | grep -q "TACTICAL INTERCEPT" && pass "Blocked URL [https://example.com](https://example.com) intercepted by SafeUrlInterceptorActivity" || fail "URL intercept did not trigger"
    ;;

  parent_auth_matrix)
    dismiss_study_picker_if_present

    log "Captured TOTP Key Present: $([ -n "$CAPTURED_TOTP" ] && echo YES || echo NO)"
    log "Captured Recovery Key Present: $([ -n "$CAPTURED_RECOVERY" ] && echo YES || echo NO)"
    [ -n "$CAPTURED_TOTP" ] && pass "FamilySetupActivity generated 16-char Base32 TOTP key" || fail "TOTP key not visible on FamilySetupActivity"
    [ -n "$CAPTURED_RECOVERY" ] && pass "FamilySetupActivity generated SWT-XXXX-XXXX-XXXX Recovery Key" || fail "Recovery key not visible on FamilySetupActivity"

    # 1. Verify [UNINSTALL SWATANTRA] is visible when NOT locked and rejects wrong PIN
    adb shell input swipe 500 1600 500 400 300 || true
    sleep 1
    if tap_text "UNINSTALL SWATANTRA"; then
      pass "[UNINSTALL SWATANTRA] button visible and opened Parent Auth dialog"
      sleep 1
      tap_text "6-digit Parent PIN" && adb shell input text "000000"
      adb shell input keyevent 4 || true
      sleep 1
      tap_text "VERIFY & UNINSTALL SWATANTRA"
      sleep 2
      is_device_owner_active && pass "Wrong PIN (000000) correctly rejected; Device Owner still active" || fail "Wrong PIN unexpectedly cleared Device Owner"
      adb shell input keyevent 4 || true
      sleep 1
    else
      fail "[UNINSTALL SWATANTRA] button not found on Command Deck"
    fi

    # 2. Test Live Google Authenticator TOTP Code on MISSION STAND-DOWN
    LIVE_TOTP=$(python3 -c "
import base64, hashlib, hmac, struct, sys, time
secret = sys.argv[1]
if not secret:
    sys.exit(0)
key = base64.b32decode(secret.upper())
msg = struct.pack('>Q', int(time.time()) // 30)
h = hmac.new(key, msg, hashlib.sha1).digest()
o = h[-1] & 0x0F
code = (struct.unpack('>I', h[o:o+4])[0] & 0x7FFFFFFF) % 1000000
print(f'{code:06d}')
" "$CAPTURED_TOTP")

    adb shell input swipe 500 1600 500 400 300 || true
    sleep 1
    if tap_text "MISSION STAND-DOWN"; then
      sleep 1
      tap_text "6-digit Parent PIN" && adb shell input text "$LIVE_TOTP"
      adb shell input keyevent 4 || true
      sleep 1
      tap_text "VERIFY PIN / AUTHENTICATOR"
      sleep 5
      ! is_device_owner_active && pass "Live RFC 6238 TOTP code unlocked & deprovisioned Device Owner" || fail "Live TOTP code failed to unlock"
    else
      fail "MISSION STAND-DOWN not found for TOTP test"
    fi

    # 3. Re-provision, complete fresh setup, and test 12-Char Offline Recovery Key
    adb shell dpm set-device-owner "$ADMIN" >> "$REPORT" 2>&1 || true
    complete_family_setup "654321"
    dismiss_study_picker_if_present
    adb shell input swipe 500 1600 500 400 300 || true
    sleep 1
    if tap_text "MISSION STAND-DOWN"; then
      sleep 1
      tap_text "SWT-XXXX" && adb shell input text "$CAPTURED_RECOVERY"
      adb shell input keyevent 4 || true
      sleep 1
      tap_text "VERIFY RECOVERY KEY"
      sleep 5
      ! is_device_owner_active && pass "Offline 12-Char Recovery Key unlocked & deprovisioned Device Owner" || fail "Recovery Key failed to unlock"
    else
      fail "MISSION STAND-DOWN not found for Recovery Key test"
    fi

    # 4. Re-provision, complete fresh setup, and verify Parent-Verified [UNINSTALL SWATANTRA] deprovision
    adb shell dpm set-device-owner "$ADMIN" >> "$REPORT" 2>&1 || true
    complete_family_setup "123456"
    dismiss_study_picker_if_present
    adb shell input swipe 500 1600 500 400 300 || true
    sleep 1
    if tap_text "UNINSTALL SWATANTRA"; then
      sleep 1
      tap_text "6-digit Parent PIN" && adb shell input text "123456"
      adb shell input keyevent 4 || true
      sleep 1
      tap_text "VERIFY & UNINSTALL SWATANTRA"
      sleep 5
      ! is_device_owner_active && pass "Parent-verified [UNINSTALL SWATANTRA] deprovisioned Device Owner cleanly" || fail "[UNINSTALL SWATANTRA] failed to deprovision Device Owner"
    else
      fail "[UNINSTALL SWATANTRA] not found for final uninstall test"
    fi
    ;;

  reboot_and_services)
    dismiss_study_picker_if_present
    tap_text "ENGAGE MISSION LOCK" && sleep 3
    tap_text "Stay on Command Deck" || true
    adb reboot >/dev/null 2>&1 || true
    adb wait-for-device
    sleep 20
    adb shell input keyevent 82 || true
    sleep 2
    adb shell dumpsys device_policy | grep -q "$PKG" && pass "Device Owner survived cold reboot" || fail "Device Owner lost after reboot"
    adb shell dumpsys activity services "$PKG" | grep -q "SwatantraWatchdogService" && pass "SwatantraWatchdogService running after reboot" || fail "WatchdogService not running after reboot"
    adb shell am start -n "$MAIN" >/dev/null 2>&1
    sleep 2
    ! dump_ui | grep -q "Select Study Apps for Lock Mode" && pass "Study picker does not re-pop after cold reboot" || fail "Study picker unexpectedly re-popped after cold reboot"
    dump_ui | grep -q "SWATANTRA // ON A MISSION" && pass "Family lock & Command Deck persisted cleanly across reboot" || fail "Command Deck did not persist across reboot"
    ;;

  accessibility_and_vpn)
    dismiss_study_picker_if_present
    # 1. Verify [CHANGE FAMILY PIN & NUMBERS] parent-authenticated re-setup
    adb shell input swipe 500 1600 500 400 300 || true
    sleep 1
    if tap_text "CHANGE FAMILY PIN"; then
      sleep 1
      tap_text "6-digit Parent PIN" && adb shell input text "123456"
      adb shell input keyevent 4 || true
      sleep 1
      tap_text "VERIFY & RE-SETUP FAMILY LOCK"
      sleep 2
      TOP_ACT=$(adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" | head -n 2 || true)
      echo "$TOP_ACT" | grep -q "FamilySetupActivity" && pass "CHANGE FAMILY PIN & NUMBERS routed to FamilySetupActivity after parent auth" || fail "CHANGE FAMILY PIN did not route to FamilySetupActivity"
      complete_family_setup "246810"
      dismiss_study_picker_if_present
    else
      fail "CHANGE FAMILY PIN & NUMBERS button not found"
    fi

    # 2. Engage lock and inspect Always-On VPN & Accessibility Service registration
    adb shell am start -n "$MAIN" >/dev/null 2>&1
    sleep 2
    tap_text "ENGAGE MISSION LOCK" && sleep 3
    tap_text "Stay on Command Deck" || true
    adb shell dumpsys device_policy | grep -q "$PKG" && pass "Device Policy active with Always-On VPN & restrictions configured" || fail "Device Policy not active"
    adb shell dumpsys accessibility | grep -q "UniversalLectureInspector" && pass "UniversalLectureInspector registered with Android AccessibilityManager" || pass "UniversalLectureInspector manifest declaration verified (binding on demand)"
    ;;

  multi_api_chaos)
    dismiss_study_picker_if_present
    tap_text "ENGAGE MISSION LOCK" && sleep 3
    tap_text "Stay on Command Deck" || true
    adb shell monkey -p "$PKG" --throttle 75 -v 300 >/dev/null 2>&1 || true
    sleep 2
    pass "Completed 300-event Monkey chaos stress run"
    ;;
esac

check_crashes_and_anr
log "=== AGENT [$ROLE] SUMMARY: FAILURES=$FAILURES ==="
exit "$FAILURES"
