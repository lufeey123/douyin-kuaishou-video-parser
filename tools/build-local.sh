#!/bin/sh
# Native Alpine ARM64 build without Gradle or desktop SDK binaries.
# Args: Android platform JAR, R8 JAR, Google apksig JAR.
set -eu
if [ "$#" -ne 3 ]; then
    echo 'Usage: sh tools/build-local.sh android.jar r8.jar apksig.jar' >&2
    exit 2
fi
PROJECT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
ANDROID_JAR=$(realpath "$1")
R8_JAR=$(realpath "$2")
APKSIG_JAR=$(realpath "$3")
WORK="$PROJECT/.build-native"
OUTPUT="$PROJECT/app/build/outputs/apk/debug/app-debug.apk"
mkdir -p "$WORK/classes" "$WORK/dex" "$WORK/signer" "$WORK/generated" "$(dirname "$OUTPUT")"
python3 - "$PROJECT" "$WORK" <<'PY'
import sys, pathlib, re, xml.etree.ElementTree as ET
root,work=map(pathlib.Path,sys.argv[1:])
# Keep removed UI inner classes/resources out of subsequent APKs.
for name in ('classes','dex','generated','signer'):
    for output in (work/name).rglob('*'):
        if output.is_file() and output.suffix in ('.class','.dex','.java'):
            output.unlink()
ns='http://schemas.android.com/apk/res/android'
ET.register_namespace('android',ns)
tree=ET.parse(root/'app/src/main/AndroidManifest.xml')
tree.getroot().set('package','com.example.videoparser')
config=(root/'app/build.gradle').read_text()
tree.getroot().set('{'+ns+'}versionCode',re.search(r'versionCode\s+(\d+)',config)[1])
tree.getroot().set('{'+ns+'}versionName',re.search(r"versionName\s+'([^']+)'",config)[1])
tree.getroot().find('application').set('{'+ns+'}debuggable','true')
tree.write(work/'AndroidManifest.xml',encoding='utf-8',xml_declaration=True)
PY
aapt2 compile --dir "$PROJECT/app/src/main/res" -o "$WORK/resources.zip"
aapt2 link -o "$WORK/resources.apk" -I "$ANDROID_JAR" \
    -A "$PROJECT/app/src/main/assets" \
    --java "$WORK/generated" \
    --manifest "$WORK/AndroidManifest.xml" --min-sdk-version 29 \
    --target-sdk-version 35 \
    "$WORK/resources.zip"
find "$PROJECT/app/src/main/java" -name '*.java' > "$WORK/sources.txt"
find "$WORK/generated" -name '*.java' >> "$WORK/sources.txt"
javac --release 8 -encoding UTF-8 -cp "$ANDROID_JAR" -d "$WORK/classes" @"$WORK/sources.txt"
jar cf "$WORK/classes.jar" -C "$WORK/classes" .
java -Xmx512m -cp "$R8_JAR" com.android.tools.r8.D8 --debug --min-api 29 \
    --lib "$ANDROID_JAR" --output "$WORK/dex" "$WORK/classes.jar"
python3 - "$WORK" <<'PY'
import pathlib,sys,zipfile
work=pathlib.Path(sys.argv[1])
with zipfile.ZipFile(work/'resources.apk') as src, zipfile.ZipFile(work/'unsigned.apk','w') as dst:
 for info in src.infolist(): dst.writestr(info,src.read(info.filename))
 for dex in sorted((work/'dex').glob('*.dex')): dst.write(dex,dex.name,compress_type=zipfile.ZIP_STORED)
PY
zipalign -f -p 4 "$WORK/unsigned.apk" "$WORK/aligned.apk"
if [ ! -f "$WORK/debug.p12" ]; then
    keytool -genkeypair -keystore "$WORK/debug.p12" -storetype PKCS12 \
        -storepass android -keypass android -alias androiddebugkey \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname 'CN=Android Debug,O=Local Development,C=US' -noprompt
fi
javac -encoding UTF-8 -cp "$APKSIG_JAR" -d "$WORK/signer" "$PROJECT/tools/SignApk.java"
java -cp "$APKSIG_JAR:$WORK/signer" SignApk "$WORK/debug.p12" "$WORK/aligned.apk" "$OUTPUT"
zipalign -c -p 4 "$OUTPUT"
aapt2 dump badging "$OUTPUT"
sha256sum "$OUTPUT"
