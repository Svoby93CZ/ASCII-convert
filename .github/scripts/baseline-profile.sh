#!/usr/bin/env bash
# Generates the Baseline Profile on the running emulator, then compares cold starts with and
# without it. The profiles and the timings are printed between markers, so that they can be taken
# from the job log when artifacts are out of reach.
set -euo pipefail

PROFILES=app/src/release/generated/baselineProfiles

./gradlew :app:generateBaselineProfile --stacktrace \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile

for profile in "$PROFILES"/*.txt; do
  name=$(basename "$profile")
  echo "$name: $(wc -l < "$profile") rules, $(grep -c '^L[^;]*;$' "$profile" || true) classes"
  echo "===== BEGIN PROFILE $name ====="
  gzip -9 -c "$profile" | base64 -w 76
  echo "===== END PROFILE $name ====="
done

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
