#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

echo "=== Building Nothing Blur Panels LSPosed module ==="

BUILD_ENV="${BUILD_ENV:-$DIR/build_env}"
ANDROID_JAR="${ANDROID_JAR:-$BUILD_ENV/android.jar}"
R8_JAR="${R8_JAR:-$BUILD_ENV/r8.jar}"
SIGNER_JAR="${SIGNER_JAR:-$BUILD_ENV/uber-apk-signer.jar}"
AAPT2_BIN="${AAPT2_BIN:-$BUILD_ENV/aapt2}"
JAVA_BIN="${JAVA_BIN:-java}"
JAVAC_BIN="${JAVAC_BIN:-javac}"

if [ -f "$BUILD_ENV/jdk/bin/java" ]; then
    JAVA_BIN="$BUILD_ENV/jdk/bin/java"
    JAVAC_BIN="$BUILD_ENV/jdk/bin/javac"
fi

rm -rf bin/classes bin/dex bin/compiled_res
mkdir -p bin/classes bin/dex bin/compiled_res

echo "[1/5] Compiling resources (aapt2)..."
if command -v aapt2 >/dev/null 2>&1; then
    AAPT2_CMD="aapt2"
else
    AAPT2_CMD="$AAPT2_BIN"
fi

"$AAPT2_CMD" compile --dir res -o bin/compiled_res/
"$AAPT2_CMD" link -I "$ANDROID_JAR" --manifest AndroidManifest.xml bin/compiled_res/*.flat -o bin/app-base.apk

echo "[2/5] Compiling Java sources..."
"$JAVAC_BIN" --release 11 -cp "$ANDROID_JAR" -d bin/classes $(find src -name "*.java")

echo "[3/5] Desugaring & compiling to DEX (D8)..."
"$JAVA_BIN" -cp "$R8_JAR" com.android.tools.r8.D8 \
    --min-api 36 \
    --classpath bin/classes \
    --lib "$ANDROID_JAR" \
    --output bin/dex \
    $(find bin/classes/com -name "*.class")

echo "[4/5] Packaging APK..."
cp bin/app-base.apk bin/unaligned.apk
(cd bin/dex && zip -u ../unaligned.apk classes.dex)
zip -u bin/unaligned.apk assets/xposed_init

echo "[5/5] Aligning & Signing APK..."
cd bin
"$JAVA_BIN" -jar "$SIGNER_JAR" -a unaligned.apk --allowResign -o .

mv unaligned-aligned-debugSigned.apk NothingBlurPanels.apk
rm -f unaligned.apk app-base.apk
echo "=== Success! Output: bin/NothingBlurPanels.apk ==="
