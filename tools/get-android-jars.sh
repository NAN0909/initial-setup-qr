#!/usr/bin/env bash
# tools/android-36.jar と tools/android-34.jar を取得する（Sable/android-platforms の android.jar）。
# Android SDK が手元にある場合は $ANDROID_HOME/platforms/android-36/android.jar をコピーしてもよい。
set -euo pipefail
cd "$(dirname "$0")"
tmp=$(mktemp -d)
git clone --depth 1 --filter=blob:none --sparse https://github.com/Sable/android-platforms "$tmp/ap"
( cd "$tmp/ap" && git sparse-checkout set android-34 android-36 )
cp "$tmp/ap/android-34/android.jar" android-34.jar
cp "$tmp/ap/android-36/android.jar" android-36.jar
rm -rf "$tmp"
ls -la android-3*.jar
