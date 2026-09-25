#!/bin/bash
# Build a signed release APK WITHOUT Gradle / Google servers (fallback when dl.google.com is blocked).
# Uses: aapt2 → javac (JDK 17+) → d8 → zipalign → apksigner, from the AndroidIDE SDK mirror on GitHub.
# Env: DAWA_KEYSTORE_FILE (path to release.jks), DAWA_KEY_PASS (its password), VC (versionCode, required),
#      SDK (tools dir, default /var/tmp/sdk). Output: build/Dawa.apk
# NOTE: versionCode must be >= the installed one, and the next GitHub run_number should be > VC.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd); HERE=$(cd "$(dirname "$0")" && pwd)
SDK=${SDK:-/var/tmp/sdk}; BT=$SDK/build-tools/34.0.4; export TZ=UTC
: "${VC:?set VC=versionCode}"; : "${DAWA_KEYSTORE_FILE:?}"; : "${DAWA_KEY_PASS:?}"
if [ ! -f "$BT/lib/d8.jar" ]; then
  mkdir -p "$SDK" && cd "$SDK"
  curl -sL -o bt.tar.xz https://github.com/AndroidIDEOfficial/androidide-tools/releases/download/v34.0.4/build-tools-34.0.4-x86_64.tar.xz
  curl -sL -o sdk.tar.xz https://github.com/AndroidIDEOfficial/androidide-tools/releases/download/sdk/android-sdk.tar.xz
  tar -xJf sdk.tar.xz android-sdk/platforms/android-33/android.jar
  tar -xJf bt.tar.xz build-tools/34.0.4/lib/d8.jar build-tools/34.0.4/lib/apksigner.jar build-tools/34.0.4/zipalign build-tools/34.0.4/aapt2
  chmod +x build-tools/34.0.4/zipalign build-tools/34.0.4/aapt2; cd "$ROOT"
fi
JAR=$SDK/android-sdk/platforms/android-33/android.jar; CJAR=$SDK/android-34-compile.jar
# android-33 jar + the two API-34 members the code uses (compile-time only; values verified against a Gradle build)
[ -f "$CJAR" ] || python3 "$HERE/patchjar.py" "$JAR" "$CJAR"
O=$ROOT/build; rm -rf "$O"; mkdir -p $O/res/{drawable,mipmap-anydpi-v26,values,layout,xml} $O/gen $O/classes $O/dex $O/assets/www
cd "$ROOT"
cp ic_launcher_background.xml ic_launcher_foreground.xml ic_stat.xml widget_bg.xml widget_btn.xml $O/res/drawable/
cp ic_launcher.xml $O/res/mipmap-anydpi-v26/; cp strings.xml styles.xml $O/res/values/
cp widget_dose.xml $O/res/layout/; cp widget_info.xml $O/res/xml/
cp index.html sw.js manifest.json icon.svg $O/assets/www/
sed -e 's#<manifest xmlns:android="http://schemas.android.com/apk/res/android">#<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.hamza.dawa">#' \
    -E -e 's#android:name="\.([A-Za-z]+)"#android:name="com.hamza.dawa.\1"#g' AndroidManifest.xml > $O/AndroidManifest.xml
$BT/aapt2 compile --dir $O/res -o $O/res.zip
$BT/aapt2 link -o $O/base.apk -I $JAR --manifest $O/AndroidManifest.xml --java $O/gen -A $O/assets \
  --min-sdk-version 26 --target-sdk-version 34 --version-code $VC --version-name 1.0.$VC \
  --compile-sdk-version-code 34 --compile-sdk-version-name 14 $O/res.zip
javac -nowarn -encoding UTF-8 --release 8 -cp $CJAR -d $O/classes $O/gen/com/hamza/dawa/R.java *.java 2>&1 | grep -v -E "JAVA_TOOL|obsolete|To suppress|^warning|deprecat" || true
python3 "$HERE/strip_mp.py" $O/classes   # JDK 21 javac writes MethodParameters entries this d8 cannot read
java -cp $BT/lib/d8.jar com.android.tools.r8.D8 --release --min-api 26 --lib $CJAR --output $O/dex $(find $O/classes -name '*.class')
cp $O/base.apk $O/unsigned.apk; (cd $O/dex && zip -q -X $O/unsigned.apk classes.dex)
$BT/zipalign -p -f 4 $O/unsigned.apk $O/aligned.apk
printf '%s' "$DAWA_KEY_PASS" > $O/.pass
java -jar $BT/lib/apksigner.jar sign --ks "$DAWA_KEYSTORE_FILE" --ks-key-alias dawa --ks-pass file:$O/.pass --v4-signing-enabled false --out $O/Dawa.apk $O/aligned.apk
rm -f $O/.pass
java -jar $BT/lib/apksigner.jar verify --print-certs $O/Dawa.apk | grep -E "DN|SHA-256"
ls -la $O/Dawa.apk
