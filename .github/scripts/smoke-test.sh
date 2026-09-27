#!/usr/bin/env bash
# Drives ASCII Studio on an emulator. Every step prints the visible UI texts and a small
# screenshot (base64 JPEG between BEGIN/END IMAGE markers) so the run can be reviewed from the log.
set -u

PKG=cz.svoby93.asciistudio
APK=app/build/outputs/apk/debug/app-debug.apk
OUT=smoke
UI=.github/scripts/ui.py
failures=0
mkdir -p "$OUT"

dump() {
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1
  rm -f "$OUT/$1.xml"
  adb pull /sdcard/window.xml "$OUT/$1.xml" >/dev/null 2>&1 || true
}

alive() {
  if ! adb shell pidof "$PKG" >/dev/null 2>&1; then
    echo "!!! $PKG is not running after step $1"
    failures=$((failures + 1))
  fi
}

screen() { # name [seconds to wait first]
  sleep "${2:-3}"
  adb exec-out screencap -p > "$OUT/$1.png"
  dump "$1"
  echo "===== SCREEN $1 ====="
  python3 "$UI" summary "$OUT/$1.xml"
  echo "===== BEGIN IMAGE $1 ====="
  python3 "$UI" encode "$OUT/$1.png"
  echo "===== END IMAGE $1 ====="
  alive "$1"
}

tap() { # label [fallback-x fallback-y]
  dump current
  local target
  target=$(python3 "$UI" find "$OUT/current.xml" "$1")
  if [ -z "$target" ] && [ $# -ge 3 ]; then
    echo "(tapping '$1' at fallback position $2 $3)"
    target="$2 $3"
  fi
  if [ -z "$target" ]; then
    echo "!!! could not find '$1' on screen"
    failures=$((failures + 1))
    return 1
  fi
  adb shell input tap $target
  sleep 1
}

adb install -r "$APK" || exit 1
adb shell pm grant "$PKG" android.permission.CAMERA
adb shell settings put system accelerometer_rotation 0
adb logcat -c

echo "### Home"
adb shell am start -W -n "$PKG/.MainActivity"
screen 01-home 6

echo "### Share a photo into the editor"
curl -sSfL -o "$OUT/photo.jpg" https://raw.githubusercontent.com/opencv/opencv/4.x/samples/data/fruits.jpg \
  || python3 "$UI" sample "$OUT/photo.jpg"
adb shell content insert --uri content://media/external/images/media \
  --bind _display_name:s:ascii-smoke.jpg --bind mime_type:s:image/jpeg --bind relative_path:s:Pictures/
ID=$(adb shell content query --uri content://media/external/images/media --projection _id:_display_name \
  | grep "ascii-smoke.jpg" | sed -n 's/.*_id=\([0-9]*\).*/\1/p' | head -n 1)
echo "MediaStore id: ${ID:-none}"
adb shell content write --uri "content://media/external/images/media/$ID" < "$OUT/photo.jpg"
adb shell am start -W -a android.intent.action.SEND -t image/jpeg \
  --eu android.intent.extra.STREAM "content://media/external/images/media/$ID" \
  --grant-read-uri-permission -n "$PKG/.MainActivity"
screen 02-editor 8

echo "### Editor controls"
tap "Tone" && screen 03-tone 3
tap "Colors" && tap "Photo colors" && screen 04-photo-colors 3
tap "Style" && tap "Braille" && screen 05-braille 3
tap "Detailed" && tap "Mixed" && screen 06-outlines 3
tap "Show original" && screen 07-original 3
tap "Show original"
tap "Export" && screen 08-export-sheet 3
adb shell input keyevent KEYCODE_BACK
sleep 1

echo "### Landscape editor"
adb shell settings put system user_rotation 1
screen 09-landscape 5
adb shell settings put system user_rotation 0
sleep 3

echo "### Back home"
adb shell input keyevent KEYCODE_BACK
screen 10-home-continue 4

echo "### Live camera"
tap "Live ASCII camera" && screen 11-camera 10
tap "Take photo" 540 2140
screen 12-captured 8

echo "### Czech and dark theme"
adb shell cmd uimode night yes
adb shell cmd locale set-app-locales "$PKG" --locales cs
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/.MainActivity"
screen 13-home-cs-dark 6
tap "Pokračovat v úpravách" && screen 14-editor-cs-dark 6

adb logcat -d -b crash > "$OUT/crash.txt"
if [ -s "$OUT/crash.txt" ]; then
  echo "===== CRASH LOG ====="
  cat "$OUT/crash.txt"
  failures=$((failures + 1))
fi
echo "===== APP LOG ====="
adb logcat -d | grep -iE "asciistudio|AndroidRuntime|FATAL|CameraX|ImageDecoder" | tail -n 200

echo "Smoke test finished with $failures problem(s)"
exit "$failures"
