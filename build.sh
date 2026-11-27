#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
: "${sdk:?Set ANDROID_HOME to your Android SDK directory}"
tools="$sdk/build-tools/36.0.0"
android_jar="$sdk/platforms/android-37.0/android.jar"
if [[ ! -f "$android_jar" || ! -x "$tools/aapt2" ]]; then
    echo "Install Android SDK Platform 37.0 and Build Tools 36.0.0." >&2
    exit 1
fi

mkdir -p .build bin
build="$(mktemp -d "$PWD/.build/build.XXXXXXXX")"
keystore="$PWD/.build/local-debug.p12"
mkdir -p "$build/classes" "$build/dex"

"$tools/aapt2" compile --dir res -o "$build/resources.zip"
"$tools/aapt2" link -I "$android_jar" --manifest AndroidManifest.xml -A assets \
    -o "$build/base.apk" "$build/resources.zip"

sources=()
while IFS= read -r -d '' source; do sources+=("$source"); done < <(find src -name '*.java' -print0)
javac --release 17 -cp "$android_jar" -d "$build/classes" "${sources[@]}"
module_classes=()
while IFS= read -r -d '' class; do module_classes+=("$class"); done < <(find "$build/classes/com" -name '*.class' -print0)
java -cp "$tools/lib/d8.jar" com.android.tools.r8.D8 --min-api 36 --lib "$android_jar" \
    --classpath "$build/classes" --output "$build/dex" "${module_classes[@]}"
(cd "$build/dex" && zip -q "$build/base.apk" ./*.dex)

if [[ ! -f "$keystore" ]]; then
    keytool -genkeypair -keystore "$keystore" -storetype PKCS12 -storepass android \
        -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US"
fi

"$tools/zipalign" -f 4 "$build/base.apk" "$build/aligned.apk"
"$tools/apksigner" sign --ks "$keystore" --ks-pass pass:android \
    --v4-signing-enabled false --out "$build/signed.apk" "$build/aligned.apk"
"$tools/apksigner" verify --verbose "$build/signed.apk"
cp "$build/signed.apk" bin/NothingGlass.apk
echo "Built bin/NothingGlass.apk"
