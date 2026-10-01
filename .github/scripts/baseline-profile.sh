#!/usr/bin/env bash
# Generates the Baseline Profile on the running emulator, then compares cold starts with and
# without it. The timings and the profiles are printed between markers at the end of the log, so
# that they can be taken from the log when artifacts are out of reach.
set -euo pipefail

PROFILES=app/src/release/generated/baselineProfiles

# The emulator renders in software. On a small screen with the same layout in dp, drawing does not
# hide the time the code takes, and the journey runs faster.
adb shell wm size 360x800
adb shell wm density 140

./gradlew :app:generateBaselineProfile --stacktrace \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile

# An emulator is fine for comparing the two, but Macrobenchmark refuses it unless told otherwise.
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest --stacktrace \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR

find baselineprofile/build/outputs -name '*benchmarkData.json' | while read -r result; do
  echo "===== BEGIN BENCHMARK $(basename "$result") ====="
  cat "$result"
  echo
  echo "===== END BENCHMARK ====="
done

# Last, in long lines, so that the profiles fit into the end of the log that tools fetch.
for profile in "$PROFILES"/*.txt; do
  name=$(basename "$profile")
  echo "$name: $(wc -l < "$profile") rules, $(grep -c '^L[^;]*;$' "$profile" || true) classes"
  echo "===== BEGIN PROFILE $name ====="
  gzip -9 -c "$profile" | base64 -w 1000
  echo "===== END PROFILE $name ====="
done
