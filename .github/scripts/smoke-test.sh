#!/usr/bin/env bash
# Drives ASCII Studio on an emulator. Every step prints the visible UI texts and a small
# screenshot (base64 JPEG between BEGIN/END IMAGE markers) so the run can be reviewed from the log.
set -u

PKG=cz.svoby93.asciistudio
APK=app/build/outputs/apk/debug/app-debug.apk
OUT=smoke
UI=.github/scripts/ui.py
MEDIA=content://media/external/images/media
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
adb shell am start -W -n "$PKG/.MainActivity"
screen 01-home 6
alive home

echo "### Pick the photo with the system photo picker"
tap "Choose a photo" && screen 02-photo-picker 6
if grep -q "No photos" "$OUT/02-photo-picker.xml" 2>/dev/null; then
  echo "(photo picker is still indexing, trying again)"
  adb shell input keyevent KEYCODE_BACK
  sleep 15
  tap "Choose a photo" && screen 02-photo-picker-retry 8
fi
tap "Photo taken" "ascii-smoke" 180 760
screen 03-editor 8
alive editor
app_log

echo "### Editor controls"
tap "Tone" && screen 04-tone 3
tap "Colors" && tap "Photo colors" && screen 05-photo-colors 3
tap "Style" && tap "Braille" && screen 06-braille 3
tap "Detailed" && tap "Mixed" && screen 07-outlines 3
tap "Show original" && screen 08-original 3
tap "Show original"
tap "Export" && screen 09-export-sheet 3
adb shell input keyevent KEYCODE_BACK
sleep 2

echo "### Landscape editor"
adb shell wm user-rotation lock 1 || adb shell settings put system user_rotation 1
screen 10-landscape 5
adb shell wm user-rotation lock 0 || adb shell settings put system user_rotation 0
sleep 3
alive landscape

echo "### Back home"
adb shell input keyevent KEYCODE_BACK
screen 11-home-continue 4

echo "### Share the photo from another app"
if [ -n "${ID:-}" ]; then
  # `am start` does not move EXTRA_STREAM into the ClipData like startActivity() does,
  # so the URI is passed as data as well; otherwise the read grant would not apply.
  adb shell am start -W -a android.intent.action.SEND -t image/jpeg -d "$MEDIA/$ID" \
    --eu android.intent.extra.STREAM "$MEDIA/$ID" --grant-read-uri-permission -n "$PKG/.MainActivity"
  screen 12-shared 8
  app_log
  adb shell input keyevent KEYCODE_BACK
  sleep 2
fi

echo "### Live camera"
adb shell am start -W -n "$PKG/.MainActivity"
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
adb shell am start -W -n "$PKG/.MainActivity"
screen 15-home-cs-dark 6
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
