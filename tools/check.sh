#!/bin/sh
# Run from any directory. Dependencies are explicit paths, not downloaded here.
set -eu
PROJECT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
mkdir -p "$PROJECT/.verify/classes" "$PROJECT/.verify/test-classes"
javac -encoding UTF-8 -d "$PROJECT/.verify/test-classes" \
    "$PROJECT/app/src/main/java/com/example/videoparser/NetworkPolicy.java" \
    "$PROJECT/tests/NetworkPolicyTest.java"
java -cp "$PROJECT/.verify/test-classes" com.example.videoparser.NetworkPolicyTest
if [ "$#" -ge 1 ]; then
    find "$PROJECT/app/src/main/java" -name '*.java' > "$PROJECT/.verify/sources.txt"
    if command -v aapt2 >/dev/null 2>&1; then
        mkdir -p "$PROJECT/.verify/generated"
        python3 - "$PROJECT" <<'PY'
import pathlib,sys,xml.etree.ElementTree as ET
root=pathlib.Path(sys.argv[1]); ET.register_namespace('android','http://schemas.android.com/apk/res/android')
tree=ET.parse(root/'app/src/main/AndroidManifest.xml')
tree.getroot().set('package','com.example.videoparser')
tree.write(root/'.verify/resource-manifest.xml',encoding='utf-8',xml_declaration=True)
PY
        aapt2 compile --dir "$PROJECT/app/src/main/res" -o "$PROJECT/.verify/resources.zip"
        aapt2 link -o "$PROJECT/.verify/resources.apk" -I "$1" \
            --manifest "$PROJECT/.verify/resource-manifest.xml" \
            --java "$PROJECT/.verify/generated" --min-sdk-version 29 --target-sdk-version 35 \
            "$PROJECT/.verify/resources.zip"
        find "$PROJECT/.verify/generated" -name '*.java' >> "$PROJECT/.verify/sources.txt"
    fi
    javac -encoding UTF-8 -Xlint:deprecation -cp "$1" \
        -d "$PROJECT/.verify/classes" @"$PROJECT/.verify/sources.txt"
    echo 'Android source compilation passed'
fi
if [ "$#" -ge 2 ]; then
    find "$PROJECT/app/src/main/java/com/example/videoparser/parser" -name '*.java' > "$PROJECT/.verify/parser-sources.txt"
    javac -encoding UTF-8 -cp "$2:$PROJECT/.verify/test-classes" \
        -d "$PROJECT/.verify/test-classes" @"$PROJECT/.verify/parser-sources.txt" \
        "$PROJECT/tests/ParserTest.java" "$PROJECT/tests/DualPlatformTest.java" "$PROJECT/tests/BatchParserTest.java"
    java -cp "$2:$PROJECT/.verify/test-classes" com.example.videoparser.parser.ParserTest
    java -cp "$2:$PROJECT/.verify/test-classes" com.example.videoparser.parser.DualPlatformTest
    java -cp "$2:$PROJECT/.verify/test-classes" com.example.videoparser.parser.BatchParserTest
fi
