#!/bin/bash
# Setup script of the Claude Code cloud environment: installs the Android SDK, so that cloud
# sessions can build, test and lint the app like the CI does.
#
# Paste the whole file into the environment's "Setup script" field (cloud environment dialog).
# The environment needs dl.google.com among its allowed domains.
#
# The environment caches the result, so the script runs again only when the environment
# changes or the cache expires. A failure never blocks the session; the details are in
# /var/log/android-sdk-setup.log.

SDK=/opt/android-sdk
# Command-line tools 23.0; newer ones are listed in dl.google.com/android/repository/repository2-3.xml.
TOOLS_ZIP=commandlinetools-linux-16111833_latest.zip
# What the Android Gradle plugin 9.3 asks for with compileSdk 37.
PACKAGES=("platforms;android-37.0" "build-tools;36.0.0" "platform-tools")
LOG=/var/log/android-sdk-setup.log

install_sdk() {
    if [ ! -x "$SDK/cmdline-tools/latest/bin/android" ]; then
        local tmp
        tmp=$(mktemp -d) || return 1
        curl -fsSL --retry 3 -o "$tmp/tools.zip" "https://dl.google.com/android/repository/$TOOLS_ZIP" || return 1
        unzip -q "$tmp/tools.zip" -d "$tmp" || return 1
        mkdir -p "$SDK/cmdline-tools" && rm -rf "$SDK/cmdline-tools/latest" || return 1
        mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest" || return 1
        rm -rf "$tmp"
    fi

    # The SDK tools run on Java, which ignores HTTPS_PROXY; pass the proxy on unless it is set already.
    local proxy="${HTTPS_PROXY:-${https_proxy:-}}"
    if [ -n "$proxy" ] && [[ "${JAVA_TOOL_OPTIONS:-}" != *https.proxyHost* ]]; then
        proxy=${proxy#*://}
        proxy=${proxy%%/*}
        proxy=${proxy##*@}
        export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dhttps.proxyHost=${proxy%:*} -Dhttps.proxyPort=${proxy##*:}"
    fi

    # --no-metrics: the Android CLI sends no usage statistics to Google.
    "$SDK/cmdline-tools/latest/bin/android" --no-metrics --sdk="$SDK" sdk install "${PACKAGES[@]}" || return 1

    # The Android Gradle plugin reads the android.home system property when neither ANDROID_HOME
    # nor local.properties points to an SDK, so no environment variable is needed.
    mkdir -p "$HOME/.gradle"
    touch "$HOME/.gradle/gradle.properties"
    grep -q '^systemProp.android.home=' "$HOME/.gradle/gradle.properties" ||
        echo "systemProp.android.home=$SDK" >> "$HOME/.gradle/gradle.properties"
    echo "export ANDROID_HOME=$SDK" > /etc/profile.d/android-sdk.sh
}

if install_sdk >> "$LOG" 2>&1; then
    echo "Android SDK is ready in $SDK"
else
    echo "Android SDK setup failed, see $LOG"
fi
exit 0
