#!/usr/bin/env bash
# Store screenshots of the Android app on an emulator (GitHub Actions, workflow 2ecure-store-screenshots.yml).
# Runs inside reactivecircus/android-emulator-runner. Needs app.aab and bundletool.jar in the working directory and
# MAESTRO_USERNAME, MAESTRO_PASSWORD, MAESTRO_RECOVERY_KEY in the environment (repository secrets, masked in the log).
# Output: shots/<lang>/<NN-name>.png; on failure an encrypted debug bundle (see the workflow).
set -euo pipefail
HERE=.github/scripts/store-screenshots
APP=com.twoecure.messenger
MAESTRO=~/.maestro/bin/maestro

echo "== Chrome without its first-run screens (the sign-in page opens in a Chrome custom tab)"
adb shell am set-debug-app --persistent com.android.chrome
adb shell 'echo "chrome --disable-fre --no-default-browser-check --no-first-run" > /data/local/tmp/chrome-command-line'
adb shell am start -n com.android.chrome/com.google.android.apps.chrome.Main >/dev/null
sleep 5

echo "== Install the app from the bundle (APKs for this emulator, signed with a throwaway key)"
keytool -genkeypair -keystore ci.keystore -storepass android -keypass android -alias ci -keyalg RSA -keysize 2048 \
  -validity 30 -dname "CN=2ecure CI" >/dev/null 2>&1
# One universal APK (every language and CPU type, about 335 MB): a device-specific set would carry only the
# emulator's system language, and the German pictures would show English texts.
java -jar bundletool.jar build-apks --bundle=app.aab --output=app.apks --mode=universal --overwrite \
  --ks=ci.keystore --ks-pass=pass:android --ks-key-alias=ci --key-pass=pass:android
unzip -o -q app.apks universal.apk
adb install -r universal.apk
adb shell pm grant $APP android.permission.POST_NOTIFICATIONS || true

echo "== Status bar: demo mode (09:41, full battery, Wi-Fi and mobile full, no notification icons)"
adb shell svc wifi enable || true
adb shell settings put system time_12_24 24  # 09:50, not 9:50 AM, as in the iOS pictures
adb shell settings put global sysui_demo_allowed 1
demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
demo enter
demo clock -e hhmm 0941
demo battery -e level 100 -e plugged false -e powersave false
demo network -e wifi show -e level 4 -e fully true
demo network -e mobile show -e level 4 -e datatype none -e fully true
demo notifications -e visible false

mkdir -p shots debug
step() { # step <name> <flow> [env...]: run a flow; on failure keep Maestro's output for the debug bundle
  local name=$1 flow=$2; shift 2
  echo "== $name"
  if ! "$MAESTRO" test --format NOOP "$@" "$HERE/$flow"; then
    adb exec-out screencap -p > "debug/$name-failed.png" || true
    cp -r ~/.maestro/tests debug/maestro-tests 2>/dev/null || true
    return 1
  fi
}

adb shell cmd locale set-app-locales $APP --locales en-US
step sign-in login.yaml
adb shell am force-stop com.android.chrome  # nothing to fall back to behind the app
step warm-up warmup.yaml
for lang in en de; do
  [ $lang = de ] && adb shell cmd locale set-app-locales $APP --locales de-CH
  # Maestro writes pictures only below its output folder: relative names there, then collected into shots/<lang>/.
  rm -rf "out-$lang"; mkdir -p "out-$lang" "shots/$lang"
  step "pictures-$lang" shots.yaml -e SHOTS="$lang" --test-output-dir "$PWD/out-$lang"
  find "out-$lang" ~/.maestro/tests -path "*/$lang/*" -name "0*.png" -newer app.apks -exec cp {} "shots/$lang/" \;
done
step sign-out logout.yaml || echo "sign-out failed: remove the «2ecure» CI device of the demo account by hand"
ls -l shots/*
