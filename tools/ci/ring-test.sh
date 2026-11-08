#!/usr/bin/env bash
# The "fake phone" ring test. CI runs it on an Android emulator against the OPTIMIZED apk, the one that may become the real app.
#   tools/ci/ring-test.sh <apk> <package>
# It does what a person does. It installs the app, sets a real alarm through the app's own voice-assistant door (SET_ALARM), waits, and
# checks that the phone rang it. Then it sets another alarm, restarts the phone, and checks the alarm was put back.
# HONEST LIMITS: an emulator is not a Samsung, and its audio is off. This proves "the ring starts and survives a restart", not "you hear it".
set -u
APK="${1:?need the apk path}"; PKG="${2:?need the package name}"
RESULT="${RESULT:-ring-result.txt}"; LOG="${LOG:-ring-test.log}"
: > "$RESULT"; : > "$LOG"
exec > >(tee -a "$LOG") 2>&1

log()  { echo "[$(date +%T)] $*"; }
note() { echo "$*" | tee -a "$RESULT"; }
adbx() { adb shell "$@" 2>&1 | tr -d '\r'; }

# Minutes ahead -> "hour minute lead-seconds" on the PHONE's own clock. Never less than 90 s of lead, and a clock that wraps past midnight is fine.
plan_time() {
  local h m s ahead t
  h=$(adbx date +%H); m=$(adbx date +%M); s=$(adbx date +%S)
  ahead=$(( $1 + (10#$s > 30 ? 1 : 0) ))
  t=$(( (10#$h * 60 + 10#$m + ahead) % 1440 ))
  echo "$((t / 60)) $((t % 60)) $(( ahead * 60 - 10#$s ))"
}

# Ask the app, the way a voice assistant does, for an alarm.
set_alarm() {
  adbx am start -n "$PKG/com.killswitch.aura.MainActivity" -a android.intent.action.SET_ALARM \
    --ei android.intent.extra.alarm.HOUR "$1" --ei android.intent.extra.alarm.MINUTES "$2" \
    --es android.intent.extra.alarm.MESSAGE "$3" --ez android.intent.extra.alarm.SKIP_UI true
}

# How many alarms this app has waiting inside Android's alarm manager.
pending() { adbx dumpsys alarm | grep -E "Alarm\{" | grep -c "$PKG"; }

# Wait up to ~30 s for the app to have armed something.
wait_armed() {
  local n=0 i
  for i in $(seq 1 15); do n=$(pending); [ "${n:-0}" -ge 1 ] && break; sleep 2; done
  echo "${n:-0}"
}

# Is it ringing? Either the ring service is running in the foreground, or the ring screen is the one on top.
ringing() {
  local s w
  s=$(adbx dumpsys activity services "$PKG")
  if echo "$s" | grep -q "RingService" && echo "$s" | grep -q "isForeground=true"; then echo "the ring service"; return 0; fi
  w=$(adbx dumpsys activity activities)
  if echo "$w" | grep -E "ResumedActivity" | grep -q "RingActivity"; then echo "the ring screen"; return 0; fi
  return 1
}

diagnostics() {
  log "---- what Android's alarm manager holds for $PKG"
  adbx dumpsys alarm | grep -E "Alarm\{" | grep "$PKG" | head -10
  log "---- services of $PKG"
  adbx dumpsys activity services "$PKG" | head -40
  log "---- the last of the phone's log"
  adb logcat -d -t 250 2>&1 | tr -d '\r' | tail -120
}

# 0. The parts the phone calls by name must still be in the optimized app.
phase_static() {
  local dex missing=0 c; dex=$(mktemp)
  unzip -p "$APK" 'classes*.dex' > "$dex" 2>/dev/null
  for c in AlarmReceiver BootReceiver RingService RingActivity MainActivity ClockWidget; do
    if ! grep -a -q "Lcom/killswitch/aura/$c;" "$dex"; then note "STATIC: FAIL $c is not in the optimized app"; missing=1; fi
  done
  rm -f "$dex"
  [ "$missing" -eq 0 ] && note "STATIC: PASS all 6 parts the phone calls by name are in the optimized app"
  return "$missing"
}

# 1. A real alarm rings.
phase_ring() {
  local th tm lead n waited=0 limit how
  read -r th tm lead < <(plan_time 2)
  log "setting an alarm for $th:$(printf %02d "$tm") on the phone's clock, due in about ${lead}s"
  set_alarm "$th" "$tm" "ringtest"
  n=$(wait_armed)
  if [ "$n" -lt 1 ]; then note "RING: FAIL the app never armed the alarm (nothing waiting for $PKG)"; diagnostics; return 1; fi
  note "RING: the app armed $n alarm(s)"
  limit=$(( lead + 150 ))
  while [ "$waited" -lt "$limit" ]; do
    if how=$(ringing); then
      note "RING: PASS $how started about ${waited} s after the alarm was set (it was due in ${lead} s)"
      return 0
    fi
    sleep 3; waited=$(( waited + 3 ))
  done
  note "RING: FAIL nothing rang within $(( limit - lead )) s of the set time"; diagnostics; return 1
}

# 2. An alarm survives the phone restarting.
phase_reboot() {
  local th tm lead n i
  adbx am force-stop "$PKG" > /dev/null; sleep 3
  read -r th tm lead < <(plan_time 25)
  log "setting an alarm for $th:$(printf %02d "$tm"), then restarting the phone"
  set_alarm "$th" "$tm" "reboottest"
  n=$(wait_armed)
  if [ "$n" -lt 1 ]; then note "REBOOT: FAIL the app never armed the alarm before the restart"; diagnostics; return 1; fi
  adb reboot; sleep 20; adb wait-for-device
  for i in $(seq 1 90); do [ "$(adbx getprop sys.boot_completed)" = "1" ] && break; sleep 3; done
  sleep 30   # the boot receiver needs a moment
  n=$(pending)
  if [ "${n:-0}" -ge 1 ]; then note "REBOOT: PASS $n alarm(s) were put back after the restart"; return 0; fi
  note "REBOOT: FAIL the restart wiped the alarm and the app did not put it back"; diagnostics; return 1
}

log "apk: $APK  package: $PKG"
phase_static; s=$?
adb wait-for-device
adbx input keyevent KEYCODE_WAKEUP > /dev/null; adbx wm dismiss-keyguard > /dev/null
if ! adb install -r -g "$APK" 2>&1 | tr -d '\r' | tail -3 | grep -q Success; then note "INSTALL: FAIL the optimized app would not install"; exit 1; fi
note "INSTALL: PASS"
phase_ring; r=$?
phase_reboot; b=$?
note "----"
note "SUMMARY: static=$([ $s -eq 0 ] && echo pass || echo FAIL) ring=$([ $r -eq 0 ] && echo pass || echo FAIL) reboot=$([ $b -eq 0 ] && echo pass || echo FAIL)"
[ $((s + r + b)) -eq 0 ]
