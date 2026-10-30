#!/usr/bin/env bash
# Compiles the engine (everything except the Compose screens) against android.jar and runs tools/EngineTest.kt on a plain JVM.
# Needs: kotlinc 2.x, JDK, aapt2, android.jar (get-jar from Reginer/aosp-android-jar android-34), a real org.json
# (Debian: apt install libandroid-json-org-java). The Android jar only has throwing stubs for org.json.
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd); SRC=$ROOT/app/src/main; T=${TMPDIR:-/tmp}/aura-engine
ANDROID_JAR=${ANDROID_JAR:-$HOME/.android/android.jar}; JSON_JAR=${JSON_JAR:-/usr/share/java/json-android.jar}
rm -rf "$T"; mkdir -p "$T/gen" "$T/rcls" "$T/out"
sed 's|<manifest xmlns:android="http://schemas.android.com/apk/res/android">|<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.killswitch.aura">|' "$SRC/AndroidManifest.xml" > "$T/AndroidManifest.xml"
aapt2 compile --dir "$SRC/res" -o "$T/res.zip"
aapt2 link -o "$T/base.apk" -I "$ANDROID_JAR" --manifest "$T/AndroidManifest.xml" --java "$T/gen" --min-sdk-version 26 --target-sdk-version 34 "$T/res.zip"
javac -nowarn -d "$T/rcls" -cp "$ANDROID_JAR" "$T/gen/com/killswitch/aura/R.java"
ENGINE=$(ls "$SRC"/java/com/killswitch/aura/*.kt | grep -v -E "/(MainActivity|RingActivity)\.kt$")
kotlinc -nowarn -cp "$ANDROID_JAR:$T/rcls" -d "$T/out" $ENGINE "$ROOT/tools/EngineStubs.kt" "$ROOT/tools/EngineTest.kt"
KLIB=$(dirname "$(command -v kotlinc)")/../lib/kotlin-stdlib.jar
TZ=${TZ:-Africa/Cairo} java -Dcities="$ROOT/tools/cities.txt" -cp "$T/out:$JSON_JAR:$KLIB:$T/rcls:$ANDROID_JAR" EngineTestKt
