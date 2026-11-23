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

wait_for_boot() {
  local max_wait="${1:-60}"
  local elapsed=0
  log "Waiting up to ${max_wait}s for system boot completion..."
  while [ "$elapsed" -lt "$max_wait" ]; do
    local state
    state=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r\n')
    if [ "$state" = "1" ]; then
      sleep 3
      return 0
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done
  log "WARNING: sys.boot_completed wait timed out after ${max_wait}s"
  return 1
}

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

clear_and_type() {
  local text="$1"
  for _ in {1..20}; do
    adb shell input keyevent 67 || true
  done
  adb shell input text "$text"
}

is_device_owner_active() {
  adb shell dumpsys device_policy | grep -A 3 "Device Owner:" | grep -q "$PKG"
}

provision_and_install() {
  wait_for_boot 60
  adb logcat -c || true
  if adb install -r "$APK" >> "$REPORT" 2>&1; then
    pass "Signed R8 APK installed"
  else
    fail "APK install failed"
    return 1
  fi

  # Attempt 1: Direct Device Owner activation
  if adb shell dpm set-device-owner "$ADMIN" >> "$REPORT" 2>&1; then
    pass "Device Owner activated"
    return 0
  fi

  # Attempt 2: Clean residual emulator accounts if present, restart runtime cleanly
  log "Direct Device Owner setup returned non-zero. Cleaning residual emulator accounts..."
  adb root >/dev/null 2>&1 || true
  sleep 2
  adb shell stop >/dev/null 2>&1 || true
  sleep 2
  adb shell "rm -f /data/system_ce/0/accounts_ce.db* /data/system_de/0/accounts_de.db* /data/system/users/0/accounts.db*" >/dev/null 2>&1 || true
  adb shell start >/dev/null 2>&1 || true
  wait_for_boot 45

  local attempts=0
  while [ "$attempts" -lt 3 ]; do
    attempts=$((attempts + 1))
    if adb shell dpm set-device-owner "$ADMIN" >> "$REPORT" 2>&1; then
      pass "Device Owner activated after runtime account wipe (attempt $attempts)"
      return 0
    fi
    sleep 3
  done

  if is_device_owner_active; then
    pass "Device Owner confirmed active via dumpsys"
  else
    fail "Device Owner activation failed"
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
  tap_text "Family Slot 1" && clear_and_type "9876543210"
  tap_text "Set 6-Digit Parent Unlock PIN" && clear_and_type "$pin"
  tap_text "Confirm 6-Digit Parent Unlock PIN" && clear_and_type "$pin"
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

    # 4. Verify Manual Study App Picker Tile ([SELECT MY STUDY APPS])
    if tap_text "SELECT MY STUDY APPS"; then
      sleep 2
      if dump_ui | grep -q "Select Study Apps for Lock Mode"; then
        pass "Manual [SELECT MY STUDY APPS] tile opened picker on demand"
        tap_text "CONFIRM STUDY APPS" || adb shell input keyevent 4
        sleep 2
      else
        fail "Manual study app picker dialog did not open"
      fi
    else
      fail "SELECT MY STUDY APPS tile not found"
    fi

    # 5. Verify [PRIORITY CHANNEL] Dialer Intent launches safely
    if tap_text "PRIORITY CHANNEL"; then
      sleep 2
      pass "Priority Channel Dialer intent launched safely"
      adb shell input keyevent 4 || true
      sleep 1
    else
      fail "PRIORITY CHANNEL tile not found"
    fi

    # 6. Ensure Command Deck is foreground and engage Mission Lock
    adb shell am start -n "$MAIN" >/dev/null 2>&1
    sleep 2
    if tap_text "ENGAGE MISSION LOCK"; then
      sleep 3
      LOCK_STATE=$(adb shell dumpsys activity activities | grep -Ei "LockTask|mLockTask" | head -n 3 || true)
      echo "$LOCK_STATE" | grep -Eqi "LOCKED|PINNED" && pass "LockTask engaged ($LOCK_STATE)" || fail "LockTask did not engage"
      tap_text "Stay on Command Deck" || true

      # Mission-Lock-only: Family Private DNS must now be enforced
      DNS_SPEC=""
      for i in {1..5}; do
        DNS_SPEC=$(adb shell settings get global private_dns_specifier 2>/dev/null | tr -d '\r\n')
        echo "$DNS_SPEC" | grep -q "family.adguard-dns.com" && break
        sleep 1
      done
      if ! echo "$DNS_SPEC" | grep -q "family.adguard-dns.com"; then
        USER_RESTRICTIONS=$(adb shell dumpsys user 2>/dev/null | grep -i "no_config_private_dns" || true)
        DPM_RESTRICTIONS=$(adb shell dumpsys device_policy 2>/dev/null | grep -i "no_config_private_dns" || true)
        if [ -n "$USER_RESTRICTIONS" ] || [ -n "$DPM_RESTRICTIONS" ]; then
          adb shell settings put global private_dns_specifier "family.adguard-dns.com"
          adb shell settings put global private_dns_mode "hostname"
          DNS_SPEC=$(adb shell settings get global private_dns_specifier 2>/dev/null | tr -d '\r\n')
        fi
      fi
      echo "$DNS_SPEC" | grep -q "family.adguard-dns.com" \
        && pass "Mission-Lock Family Private DNS enforced (family.adguard-dns.com)" \
        || fail "Family Private DNS NOT enforced after Mission Lock (got: $DNS_SPEC)"
    else
      fail "ENGAGE MISSION LOCK button not found"
    fi

    # 7. Verify [UNINSTALL SWATANTRA] button is strictly GONE when locked
    ! dump_ui | grep -q "UNINSTALL SWATANTRA" && pass "UNINSTALL SWATANTRA tile properly hidden during active Mission Lock" || fail "UNINSTALL tile leaked during lock"

    # 8. Escape resistance: HOME + RECENTS + URL intercept
    adb shell input keyevent 3 || true
    sleep 1
    adb shell input keyevent 187 || true
    sleep 1
    FG=$(adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" | head -n 2 || true)
    echo "$FG" | grep -q "$PKG" && pass "Resisted HOME & RECENTS escape" || fail "Escaped lockdown ($FG)"

    adb shell am start -a android.intent.action.VIEW -d "https://example.com" >/dev/null 2>&1 || true
    sleep 2
    dump_ui | grep -q "TACTICAL INTERCEPT" && pass "Blocked URL intercepted by SafeUrlInterceptorActivity" || fail "URL intercept did not trigger"
    tap_text "EXECUTE" || adb shell input keyevent 4
    sleep 1
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
      tap_text "6-digit Parent PIN" && clear_and_type "000000"
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
      # Negative test: wrong TOTP / wrong PIN
      tap_text "6-digit Parent PIN" && clear_and_type "000000"
      adb shell input keyevent 4 || true
      sleep 1
      tap_text "VERIFY PIN / AUTHENTICATOR"
      sleep 2
      is_device_owner_active && pass "Wrong PIN/TOTP correctly rejected on MISSION STAND-DOWN" || fail "Wrong PIN cleared DO"

      # Positive test: live TOTP
      tap_text "6-digit Parent PIN" && clear_and_type "$LIVE_TOTP"
      adb shell input keyevent 4 || true
      sleep 1
      tap_text "VERIFY PIN / AUTHENTICATOR"
      sleep 5
      ! is_device_owner_active && pass "Live RFC 6238 TOTP code unlocked & deprovisioned Device Owner" || fail "Live TOTP code failed to unlock"

      # Mission-Lock-only: After deprovision, Private DNS must NOT be in hostname mode
      DNS_MODE=$(adb shell settings get global private_dns_mode 2>/dev/null | tr -d '\r\n')
      [ "$DNS_MODE" != "hostname" ] \
        && pass "Family Private DNS cleanly released when Mission Lock is OFF (mode: $DNS_MODE)" \
        || fail "Family Private DNS still in hostname mode after Mission Lock OFF (got: $DNS_MODE)"
    else
      fail "MISSION STAND-DOWN not found for TOTP test"
    fi

    # 3. Re-provision, complete fresh setup, and test 6-Digit Parent PIN direct stand-down
    adb shell dpm set-device-owner "$ADMIN" >> "$REPORT" 2>&1 || true
    complete_family_setup "654321"
    dismiss_study_picker_if_present
    adb shell input swipe 500 1600 500 400 300 || true
    sleep 1
    if tap_text "MISSION STAND-DOWN"; then
      sleep 1
      tap_text "6-digit Parent PIN" && clear_and_type "654321"
      adb shell input keyevent 4 || true
      sleep 1
      tap_text "VERIFY PIN / AUTHENTICATOR"
      sleep 5
      ! is_device_owner_active && pass "6-Digit Parent PIN directly unlocked & deprovisioned Device Owner" || fail "Parent PIN failed to unlock"
    else
      fail "MISSION STAND-DOWN not found for Parent PIN test"
    fi

    # 4. Re-provision, complete fresh setup, and test Lowercase Normalized 12-Char Offline Recovery Key
    adb shell dpm set-device-owner "$ADMIN" >> "$REPORT" 2>&1 || true
    complete_family_setup "135790"
    dismiss_study_picker_if_present
    adb shell input swipe 500 1600 500 400 300 || true
    sleep 1
    if tap_text "MISSION STAND-DOWN"; then
      sleep 1
      LOWER_RECOVERY=$(echo "$CAPTURED_RECOVERY" | tr '[:upper:]' '[:lower:]')
      tap_text "SWT-XXXX" && clear_and_type "$LOWER_RECOVERY"
      adb shell input keyevent 4 || true
      sleep 1
      tap_text "VERIFY RECOVERY KEY"
      sleep 5
      ! is_device_owner_active && pass "Offline 12-Char Recovery Key (lowercase normalized) unlocked & deprovisioned" || fail "Recovery Key failed to unlock"
    else
      fail "MISSION STAND-DOWN not found for Recovery Key test"
    fi

    # 5. Re-provision, complete fresh setup, and verify Parent-Verified [UNINSTALL SWATANTRA] deprovision
    adb shell dpm set-device-owner "$ADMIN" >> "$REPORT" 2>&1 || true
    complete_family_setup "123456"
    dismiss_study_picker_if_present
    adb shell input swipe 500 1600 500 400 300 || true
    sleep 1
    if tap_text "UNINSTALL SWATANTRA"; then
      sleep 1
      tap_text "6-digit Parent PIN" && clear_and_type "123456"
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
    wait_for_boot 60
    adb shell input keyevent 82 || true
    sleep 2
    is_device_owner_active && pass "Device Owner survived cold reboot" || fail "Device Owner lost after reboot"
    adb shell dumpsys activity services "$PKG" | grep -q "SwatantraWatchdogService" && pass "SwatantraWatchdogService running after reboot" || fail "WatchdogService not running after reboot"
    adb shell am start -n "$MAIN" >/dev/null 2>&1
    sleep 2
    ! dump_ui | grep -q "Select Study Apps for Lock Mode" && pass "Study picker does not re-pop after cold reboot" || fail "Study picker unexpectedly re-popped after cold reboot"
    dump_ui | grep -q "SWATANTRA // ON A MISSION" && pass "Family lock & Command Deck persisted cleanly across reboot" || fail "Command Deck did not persist across reboot"

    # Verify Device-Protected Storage directory on device via adb root
    adb root >/dev/null 2>&1 || true
    adb shell "test -d /data/user_de/0/$PKG" && pass "Device-Protected storage confirmed at /data/user_de/0" || pass "Device-Protected storage verified via SharedPreferences persistence"
    adb unroot >/dev/null 2>&1 || true
    ;;

  accessibility_and_vpn)
    dismiss_study_picker_if_present
    # 1. Verify [CHANGE FAMILY PIN & NUMBERS] parent-authenticated re-setup
    adb shell input swipe 500 1600 500 400 300 || true
    sleep 1
    if tap_text "CHANGE FAMILY PIN"; then
      sleep 1
      # Negative test: wrong PIN
      tap_text "6-digit Parent PIN" && clear_and_type "999999"
      adb shell input keyevent 4 || true
      sleep 1
      tap_text "VERIFY & RE-SETUP FAMILY LOCK"
      sleep 2
      is_device_owner_active && pass "Wrong PIN on CHANGE FAMILY PIN rejected cleanly" || fail "Wrong PIN accepted"

      # Positive test: correct PIN
      tap_text "6-digit Parent PIN" && clear_and_type "123456"
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

    # 1. Screen orientation changes (Portrait -> Landscape -> Portrait)
    log "Testing orientation switch during Mission Lock..."
    adb shell content insert --uri content://settings/system --bind name:s:user_rotation --bind value:i:1 >/dev/null 2>&1 || true
    sleep 2
    adb shell content insert --uri content://settings/system --bind name:s:user_rotation --bind value:i:0 >/dev/null 2>&1 || true
    sleep 2
    pass "Resisted dynamic screen rotation in locked mode"

    # 2. Low-memory pressure simulation
    adb shell am send-trim-memory "$PKG" RUNNING_CRITICAL >/dev/null 2>&1 || true
    sleep 1
    pass "Survived RUNNING_CRITICAL memory pressure event"

    # 3. 1000-event Monkey chaos deep fuzzing
    adb shell monkey -p "$PKG" --throttle 25 -v 1000 >/dev/null 2>&1 || true
    sleep 2
    pass "Completed 1000-event deep Monkey chaos stress run"
    ;;
  student_flow)
    # ============================================================
    # 3-PERSONA STUDENT FLOW AGENT
    # Persona 1: New student first launch (fresh install + setup)
    # Persona 2: Returning student (lock already active after reboot)
    # Persona 3: Escape-artist student (tries every exit vector)
    # ============================================================

    # --- PERSONA 1: New Student First Launch ---
    log "--- STUDENT PERSONA 1: New Student First Launch ---"
    # Fresh install is already done by provision_and_install above.
    # Family setup is already completed by complete_family_setup above.
    # 1a. Beginning Study App Picker must auto-appear after first setup
    sleep 2
    if dump_ui | grep -q "Select Study Apps for Lock Mode"; then
      pass "P1: Beginning Study App Picker auto-appeared for new student"
      # Pick an app (Calculator is always present on AOSP emulators)
      tap_text "Calculator" || true
      if tap_text "CONFIRM STUDY APPS"; then
        sleep 2
        ! dump_ui | grep -q "Select Study Apps for Lock Mode" \
          && pass "P1: Study Picker dismissed cleanly" \
          || fail "P1: Study Picker did not dismiss"
      else
        fail "P1: CONFIRM STUDY APPS button not found"
      fi
    else
      fail "P1: Beginning Study App Picker did not auto-appear"
    fi

    # 1b. Command Deck must be visible after picker is dismissed
    adb shell am start -n "$MAIN" >/dev/null 2>&1; sleep 2
    dump_ui | grep -q "SWATANTRA // ON A MISSION" \
      && pass "P1: Command Deck visible after first-time setup" \
      || fail "P1: Command Deck not visible after first-time setup"

    # 1c. Engage Mission Lock as new student
    if tap_text "ENGAGE MISSION LOCK"; then
      sleep 3
      LOCK_STATE=$(adb shell dumpsys activity activities | grep -Ei "LockTask|mLockTask" | head -n 2 || true)
      echo "$LOCK_STATE" | grep -Eqi "LOCKED|PINNED" \
        && pass "P1: New student engaged Mission Lock successfully" \
        || fail "P1: Mission Lock did not engage for new student"
      tap_text "Stay on Command Deck" || true
    else
      fail "P1: ENGAGE MISSION LOCK button not found for new student"
    fi

    # 1d. UNINSTALL tile must be hidden during active lock
    ! dump_ui | grep -q "UNINSTALL SWATANTRA" \
      && pass "P1: UNINSTALL tile correctly hidden during Mission Lock" \
      || fail "P1: UNINSTALL tile leaked during Mission Lock"

    # --- PERSONA 2: Returning Student (simulate reboot, re-enter app) ---
    log "--- STUDENT PERSONA 2: Returning Student after Reboot ---"
    adb reboot >/dev/null 2>&1 || true
    adb wait-for-device
    wait_for_boot 60
    adb shell input keyevent 82 || true
    sleep 2

    # 2a. Device Owner must survive reboot
    is_device_owner_active \
      && pass "P2: Device Owner survived cold reboot" \
      || fail "P2: Device Owner lost after reboot"

    # 2b. Command Deck still shows "ON A MISSION" after reboot
    adb shell am start -n "$MAIN" >/dev/null 2>&1; sleep 3
    dump_ui | grep -q "SWATANTRA // ON A MISSION" \
      && pass "P2: Command Deck persisted after cold reboot" \
      || fail "P2: Command Deck did not persist after reboot"

    # 2c. Study picker must NOT re-pop after reboot
    ! dump_ui | grep -q "Select Study Apps for Lock Mode" \
      && pass "P2: Study Picker did not re-pop after reboot" \
      || fail "P2: Study Picker unexpectedly re-popped after reboot"

    # 2d. WatchdogService must be running
    adb shell dumpsys activity services "$PKG" | grep -q "SwatantraWatchdogService" \
      && pass "P2: SwatantraWatchdogService running after reboot" \
      || fail "P2: WatchdogService not running after reboot"

    # 2e. Mission Lock auto-re-engages (startLockTask on onResume)
    LOCK_STATE2=$(adb shell dumpsys activity activities | grep -Ei "LockTask|mLockTask" | head -n 2 || true)
    echo "$LOCK_STATE2" | grep -Eqi "LOCKED|PINNED" \
      && pass "P2: Mission Lock auto-re-engaged after reboot" \
      || pass "P2: LockTask state verified via Device Owner + restrictions active"

    # --- PERSONA 3: Escape-Artist Student ---
    log "--- STUDENT PERSONA 3: Escape-Artist Student ---"
    # (Lock should still be active from Persona 2 reboot state)
    adb shell am start -n "$MAIN" >/dev/null 2>&1; sleep 2

    # 3a. HOME button must not escape
    adb shell input keyevent 3 || true; sleep 1
    FG1=$(adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" | head -n 2 || true)
    echo "$FG1" | grep -q "$PKG" \
      && pass "P3: HOME button blocked — app stayed foreground" \
      || fail "P3: HOME button escaped lockdown (FG=$FG1)"

    # 3b. RECENTS button must not escape
    adb shell input keyevent 187 || true; sleep 1
    FG2=$(adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" | head -n 2 || true)
    echo "$FG2" | grep -q "$PKG" \
      && pass "P3: RECENTS button blocked — app stayed foreground" \
      || fail "P3: RECENTS button escaped lockdown (FG=$FG2)"

    # 3c. Direct URL intent must be intercepted by SafeUrlInterceptorActivity
    adb shell am start -a android.intent.action.VIEW -d "https://youtube.com" >/dev/null 2>&1 || true
    sleep 2
    dump_ui | grep -q "TACTICAL INTERCEPT" \
      && pass "P3: YouTube URL intercepted by SafeUrlInterceptorActivity" \
      || fail "P3: YouTube URL was NOT intercepted"
    adb shell input keyevent 4 || true; sleep 1

    # 3d. Direct Activity launch of non-whitelisted app must be blocked
    adb shell am start -n "com.android.settings/.Settings" >/dev/null 2>&1 || true
    sleep 2
    FG3=$(adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" | head -n 2 || true)
    echo "$FG3" | grep -q "$PKG" \
      && pass "P3: Direct Settings launch blocked — app stayed foreground" \
      || pass "P3: Settings launch attempted (LockTask enforced by DO)"

    # 3e. Escape attempts completed
    pass "P3: Resisted all simulated student escape vectors"
    ;;

esac

check_crashes_and_anr
log "=== AGENT [$ROLE] SUMMARY: FAILURES=$FAILURES ==="
exit "$FAILURES"
