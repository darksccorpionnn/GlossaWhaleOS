#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
# Build a separately installable PS4 Remote Play companion from Chiaki-ng.
# Chiaki-ng's original source and licenses remain at the pinned upstream repository.
set -euo pipefail
CHI_SHA="43728af2e639fecb8a7af1cd68c29820a40de973"
mkdir -p /tmp/glossa-engine
git clone --filter=blob:none --no-checkout https://github.com/chiaki-ng/chiaki-ng.git /tmp/glossa-engine/chiaki
cd /tmp/glossa-engine/chiaki
git checkout "$CHI_SHA"
git submodule update --init --depth=1 \
  third-party/nanopb third-party/gf-complete third-party/jerasure third-party/curl \
  android/app/src/main/cpp/oboe

python3 - <<'PY'
from pathlib import Path
app=Path('android/app/build.gradle')
gradle=app.read_text()
before='applicationId "com.metallic.chiaki"'
assert gradle.count(before)==1, 'Expected official Chiaki Android application ID'
gradle=gradle.replace(before,'applicationId "com.darkscorpion.glossawhale.ps4remote"')
app.write_text(gradle)

# Avoid a FileProvider collision with regular Chiaki when both apps are installed.
src=Path('android/app/src/main')
for p in src.rglob('*'):
    if not p.is_file() or p.suffix not in ('.xml','.kt','.java'):
        continue
    try:
        content=p.read_text()
    except UnicodeError:
        continue
    changed=content.replace('com.metallic.chiaki.fileprovider',
        'com.darkscorpion.glossawhale.ps4remote.fileprovider')
    if changed!=content:
        p.write_text(changed)

strings=Path('android/app/src/main/res/values/strings.xml')
st=strings.read_text()
assert '<string name="app_name">Chiaki</string>' in st
strings.write_text(st.replace('<string name="app_name">Chiaki</string>',
    '<string name="app_name">Glossa PS4 • Chiaki</string>'))
PY

export JAVA_HOME="${JAVA_HOME:-/opt/hostedtoolcache/Java_Temurin-Hotspot_jdk/17}"
cd android
chmod +x gradlew
./gradlew --no-daemon --stacktrace --max-workers=2 :app:assembleDebug
mkdir -p "${GITHUB_WORKSPACE}/dist-remote"
cp app/build/outputs/apk/debug/app-debug.apk \
  "${GITHUB_WORKSPACE}/dist-remote/GlossaWhaleOS-PS4-Chiaki-Remote-Motoru.apk"

cat > "${GITHUB_WORKSPACE}/dist-remote/SOURCE_AND_LICENSE.txt" <<'TXT'
GlossaWhaleOS PS4 Remote Engine (separately installable helper APK)
Upstream open-source project: chiaki-ng/chiaki-ng
Upstream repository: https://github.com/chiaki-ng/chiaki-ng
Source revision: 43728af2e639fecb8a7af1cd68c29820a40de973

Chiaki-ng is licensed under AGPL-3.0-only with an OpenSSL linking exception.
This APK includes the upstream Chiaki Android app and native Remote Play core.
Build script with our applicationId/branding/provider patches:
https://github.com/darksccorpionnn/GlossaWhaleOS/blob/feat/glossawhale-v2-split-apps/source-parts/build-chiaki-phone-engine.sh

This is not Sony software, nor an official Sony application.
GlossaWhaleOS's primary phone app launches this separately installed companion.
It has not been merged into a single APK. PSN and PS4 login credentials must
never be sent to a third party or pasted into chat.
TXT
echo "===== Native APK generated ====="
ls -lh "${GITHUB_WORKSPACE}/dist-remote/"
