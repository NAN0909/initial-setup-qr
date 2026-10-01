#!/usr/bin/env bash
# 初期設定ヘルパー APK ビルドスクリプト（Gradle 不要）
# 必要: JDK 17+, aapt2, dalvik-exchange(dx), zipalign, apksigner, tools/android-36.jar
# 使い方: ./build.sh            → dist/helper-<version>.apk を作る
#        KEYSTORE_PASS=... ./build.sh  （鍵のパスワードは環境変数か keys/keystore.pass で渡す）
set -euo pipefail
cd "$(dirname "$0")"

ANDROID_JAR=tools/android-36.jar      # javac 用（API 36）
LINK_JAR=tools/android-34.jar         # aapt2 link 用（Debian 版 aapt2 が読める上限）
KEYSTORE=keys/helper-release.jks
KEY_ALIAS=helper
PASS="${KEYSTORE_PASS:-$(cat keys/keystore.pass 2>/dev/null || true)}"
[ -n "$PASS" ] || { echo "署名鍵のパスワードがありません（KEYSTORE_PASS か keys/keystore.pass）"; exit 1; }
[ -f "$KEYSTORE" ] || { echo "署名鍵 $KEYSTORE がありません。README の手順で作成してください。"; exit 1; }

VERSION_NAME=$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' app/AndroidManifest.xml)
VERSION_CODE=$(sed -n 's/.*android:versionCode="\([^"]*\)".*/\1/p' app/AndroidManifest.xml)
OUT=dist/helper-${VERSION_NAME}.apk
BUILD=build
rm -rf "$BUILD"; mkdir -p "$BUILD/res" "$BUILD/classes" "$BUILD/gen" dist

echo "[1/6] aapt2 compile"
aapt2 compile --dir app/res -o "$BUILD/res.zip"

echo "[2/6] aapt2 link"
aapt2 link -o "$BUILD/base.apk" -I "$LINK_JAR" \
  --manifest app/AndroidManifest.xml -R "$BUILD/res.zip" \
  --java "$BUILD/gen" --auto-add-overlay

echo "[3/6] javac"
find app/src "$BUILD/gen" -name '*.java' > "$BUILD/sources.txt"
javac -source 8 -target 8 -Xlint:-options -nowarn -encoding UTF-8 \
  -bootclasspath "$ANDROID_JAR" -classpath "$ANDROID_JAR" \
  -d "$BUILD/classes" @"$BUILD/sources.txt"

echo "[4/6] dex"
dalvik-exchange --dex --min-sdk-version=29 --output="$BUILD/classes.dex" "$BUILD/classes"

echo "[5/6] package + zipalign"
cp "$BUILD/base.apk" "$BUILD/unaligned.apk"
( cd "$BUILD" && zip -q -j unaligned.apk classes.dex )
zipalign -f -p 4 "$BUILD/unaligned.apk" "$BUILD/aligned.apk"

echo "[6/6] sign (v1+v2+v3)"
apksigner sign --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" --ks-pass "pass:$PASS" --key-pass "pass:$PASS" \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out "$OUT" "$BUILD/aligned.apk"
apksigner verify --verbose "$OUT" | head -5

python3 tools/checksums.py "$OUT" > "dist/helper-${VERSION_NAME}.checksums.json"
echo
echo "出力: $OUT  (versionCode=$VERSION_CODE)"
cat "dist/helper-${VERSION_NAME}.checksums.json"
