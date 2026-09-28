#!/usr/bin/env bash
# Drives ASCII Studio on an emulator. Every step prints the visible UI texts and a small
# screenshot (base64 JPEG between BEGIN/END IMAGE markers) so the run can be reviewed from the log.
set -u

PKG=com.asciistudio
# The application ID differs from the code namespace, so the activity needs its full class name.
ACTIVITY="$PKG/cz.svoby93.asciistudio.MainActivity"
APK=app/build/outputs/apk/debug/app-debug.apk
OUT=smoke
UI=.github/scripts/ui.py
MEDIA=content://media/external/images/media
failures=0
mkdir -p "$OUT"

dump() { # name: saves the UI tree. Slow emulators show "<app> isn't responding" dialogs; they are closed first.
  local target
  for _ in 1 2 3; do
    adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1
    rm -f "$OUT/$1.xml"
    adb pull /sdcard/window.xml "$OUT/$1.xml" >/dev/null 2>&1 || return 0
    grep -q "t responding" "$OUT/$1.xml" || return 0
    if grep -q "ASCII Studio isn" "$OUT/$1.xml"; then
      echo "!!! ASCII Studio is not responding"
      failures=$((failures + 1))
    fi
    target=$(python3 "$UI" find "$OUT/$1.xml" "Wait")
    [ -n "$target" ] || return 0
    echo "(closing a system dialog, $(python3 "$UI" summary "$OUT/$1.xml"))"
    adb shell input tap $target
    sleep 3
  done
}

alive() {
  if ! adb shell pidof "$PKG" >/dev/null 2>&1; then
    echo "!!! $PKG is not running after step $1"
    failures=$((failures + 1))
  fi
}

screen() { # name [seconds to wait first]
  sleep "${2:-3}"
  dump "$1"
  adb exec-out screencap -p > "$OUT/$1.png"
  echo "===== SCREEN $1 ====="
  python3 "$UI" summary "$OUT/$1.xml"
  echo "===== BEGIN IMAGE $1 ====="
  python3 "$UI" encode "$OUT/$1.png"
  echo "===== END IMAGE $1 ====="
}

tap() { # label... [x y]: taps the first label found, or the fallback position when given
  local labels=() fallback=""
  while [ $# -gt 0 ]; do
    if [ $# -eq 2 ] && [[ "$1" =~ ^[0-9]+$ ]] && [[ "$2" =~ ^[0-9]+$ ]]; then
      fallback="$1 $2"
      break
    fi
    labels+=("$1")
    shift
  done
  dump current
  local target
  target=$(python3 "$UI" find "$OUT/current.xml" "${labels[@]}")
  if [ -z "$target" ] && [ -n "$fallback" ]; then
    echo "(tapping '${labels[0]}' at fallback position $fallback)"
    target="$fallback"
  fi
  if [ -z "$target" ]; then
    echo "!!! could not find '${labels[*]}' on screen"
    failures=$((failures + 1))
    return 1
  fi
  adb shell input tap $target
  sleep 1
}

app_log() {
  echo "----- app log -----"
  adb logcat -d -s AsciiStudio:V | tail -n 40
}

adb install -r "$APK" || exit 1
adb shell pm grant "$PKG" android.permission.CAMERA
adb shell settings put system accelerometer_rotation 0
adb logcat -c

# The gallery photo goes in first: the photo picker indexes new media in the background.
echo "### Put a photo into the gallery"
curl -sSfL -o "$OUT/photo.jpg" https://raw.githubusercontent.com/opencv/opencv/4.x/samples/data/fruits.jpg \
  || python3 "$UI" sample "$OUT/photo.jpg"
adb push "$OUT/photo.jpg" /sdcard/Pictures/ascii-smoke.jpg >/dev/null
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Pictures/ascii-smoke.jpg >/dev/null
sleep 4
adb shell content query --uri "$MEDIA" --projection _id:_display_name:_size:is_pending | grep ascii-smoke
ID=$(adb shell content query --uri "$MEDIA" --projection _id:_display_name \
  | grep "ascii-smoke.jpg" | sed -n 's/.*_id=\([0-9]*\).*/\1/p' | head -n 1)
echo "MediaStore id: ${ID:-none}"

echo "### Home"
adb shell am start -W -n "$ACTIVITY"
screen 01-home 6
alive home

echo "### About and privacy policy"
tap "About" && tap "Privacy policy" && screen 01-privacy-policy 2
adb shell input keyevent KEYCODE_BACK
sleep 1

echo "### System photo picker"
tap "Choose a photo" && screen 02-photo-picker 6
if grep -q "No photos" "$OUT/02-photo-picker.xml" 2>/dev/null; then
  # The picker indexes new media in the background and on emulators often shows nothing yet.
  # Opening and closing it still proves the app launches the system picker correctly.
  echo "(the emulator's photo picker has not indexed the photo yet; selection skipped)"
  adb shell input keyevent KEYCODE_BACK
  sleep 2
else
  tap "Photo taken" "ascii-smoke" 180 760
  screen 03-picked 8
  app_log
  adb shell input keyevent KEYCODE_BACK
  sleep 2
fi
alive picker

echo "### Share the photo from another app"
if [ -z "${ID:-}" ]; then
  echo "!!! the test photo did not reach MediaStore"
  failures=$((failures + 1))
fi
# `am start` does not move EXTRA_STREAM into the ClipData like startActivity() does,
# so the URI is passed as data as well; otherwise the read grant would not apply.
adb shell am start -W -a android.intent.action.SEND -t image/jpeg -d "$MEDIA/$ID" \
  --eu android.intent.extra.STREAM "$MEDIA/$ID" --grant-read-uri-permission -n "$ACTIVITY"
screen 04-editor 8
alive editor
app_log

echo "### Editor controls"
tap "Tone" && screen 05-tone 3
tap "Colors" && tap "Photo colors" && screen 06-photo-colors 3
tap "Style" && tap "Braille" && screen 07-braille 3
tap "Detailed" && tap "Mixed" && screen 08-outlines 3
tap "Show original" && screen 09-original 3
tap "Show original"
tap "Export" && screen 10-export-sheet 3
adb shell input keyevent KEYCODE_BACK
sleep 2

echo "### Landscape editor"
adb shell wm user-rotation lock 1 || adb shell settings put system user_rotation 1
screen 11-landscape 5
adb shell wm user-rotation lock 0 || adb shell settings put system user_rotation 0
sleep 3
alive landscape

echo "### Back home"
adb shell input keyevent KEYCODE_BACK
screen 12-home-continue 4

echo "### Live camera"
adb shell am start -W -n "$ACTIVITY"
sleep 2
tap "Live ASCII camera" && screen 13-camera 10
tap "Take photo" 540 2140
screen 14-captured 8
alive capture
adb shell input keyevent KEYCODE_BACK
sleep 2

echo "### Czech and dark theme"
adb shell cmd uimode night yes
adb shell cmd locale set-app-locales "$PKG" --locales cs
adb shell am force-stop "$PKG"
adb shell am start -W -n "$ACTIVITY"
screen 15-home-cs-dark 6
tap "O aplikaci" && tap "Zásady ochrany soukromí" && screen 15-privacy-policy-cs 2
adb shell input keyevent KEYCODE_BACK
sleep 1
tap "Pokračovat v úpravách" && screen 16-editor-cs-dark 6
alive czech

adb logcat -d -b crash > "$OUT/crash.txt"
if [ -s "$OUT/crash.txt" ]; then
  echo "===== CRASH LOG ====="
  cat "$OUT/crash.txt"
  failures=$((failures + 1))
fi
app_log

echo "Smoke test finished with $failures problem(s)"
exit "$failures"
