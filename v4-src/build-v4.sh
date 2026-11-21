#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
# Build integrated GlossaWhaleOS 4.2 phone app from Chiaki-ng.
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
gradle=gradle.replace(before,'applicationId "com.darkscorpion.glossawhale.unifiedv42"')
# The verified 3.1 build had versionCode 12. V4 starts a numbered release
# line so upgrades can eventually preserve registration with stable signing.
assert gradle.count('versionCode 12')==1
gradle=gradle.replace('versionCode 12','versionCode 42')
assert gradle.count('versionName chiakiVersion')==1
gradle=gradle.replace('versionName chiakiVersion','versionName "4.2.0-beta1"')
deps='dependencies {'
assert gradle.count(deps)==1
gradle=gradle.replace(deps,deps+"\n    implementation 'com.google.mlkit:text-recognition:16.0.1'\n    implementation 'com.google.mlkit:translate:17.0.3'",1)
# This helper only targets the user's 64-bit Android phone, not x86 emulators or TVs.
needle='defaultConfig {'
assert gradle.count(needle)==1
gradle=gradle.replace(needle,needle+'\n        ndk { abiFilters "arm64-v8a" }',1)
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
        'com.darkscorpion.glossawhale.unifiedv42.fileprovider')
    if changed!=content:
        p.write_text(changed)

# Critical: changing applicationId can make Android resolve relative Activity
# names against the new package, while compiled Kotlin classes retain
# com.metallic.chiaki. Make all manifest Activity classes fully qualified.
manifest=Path('android/app/src/main/AndroidManifest.xml')
xml=manifest.read_text()
for activity in ('main.MainActivity','stream.StreamActivity',
                 'settings.SettingsActivity','regist.RegistActivity',
                 'regist.RegistExecuteActivity',
                 'manualconsole.EditManualConsoleActivity'):
    old='android:name=".'+activity+'"'
    assert xml.count(old)==1, f'Chiaki manifest entry missing: {activity}'
    xml=xml.replace(old,'android:name="com.metallic.chiaki.'+activity+'"')
# Only the paired LAN TV receives translated subtitle text. The bundled
# Chiaki core still handles the encrypted PS4 stream as usual.
assert 'android:allowBackup="true"' in xml
xml=xml.replace('android:allowBackup="true"',
    'android:allowBackup="true"\n        android:usesCleartextTraffic="true"',1)
assert xml.count('    </application>')==1
xml=xml.replace('    </application>',
    '        <activity android:name="com.metallic.chiaki.glossa.GlossaSettingsActivity" android:exported="false" />\n    </application>',1)
# The new Glossa home screen becomes the only launcher. Chiaki MainActivity
# remains reachable by an explicit in-app intent, preserving registration,
# discovery, controller and Remote Play session behavior.
launcher='''            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>'''
assert xml.count(launcher)==1, 'Expected Chiaki original launcher filter'
xml=xml.replace(launcher,'')
assert xml.count('    </application>')==1
xml=xml.replace('    </application>', '''        <activity android:name="com.metallic.chiaki.glossa.GlossaHomeActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>
        <activity android:name="com.metallic.chiaki.glossa.GlossaThemeActivity"
            android:exported="false" />
        <activity android:name="com.metallic.chiaki.glossa.PsnAccountActivity"
            android:exported="false" />
        <activity android:name="com.metallic.chiaki.glossa.GlossaConsolesActivity"
            android:exported="false" />
        <activity android:name="com.metallic.chiaki.glossa.GlossaPairingActivity"
            android:exported="false" />
        <activity android:name="com.metallic.chiaki.glossa.GlossaPairingProgressActivity"
            android:exported="false" />
    </application>''',1)
manifest.write_text(xml)

# CoordinatorLayout resolves relative app:layout_behavior names against the
# applicationId, NOT against the Kotlin namespace. Qualify both behavior
# classes in the original Chiaki layout to prevent startup InflateException.
layout=Path('android/app/src/main/res/layout/activity_main.xml')
layout_xml=layout.read_text()
for behavior in ('FloatingActionButtonBackgroundBehavior',
                 'FloatingActionButtonSpeedDialBehavior'):
    old='app:layout_behavior=".main.'+behavior+'"'
    assert layout_xml.count(old)==1, f'Missing relative behavior: {behavior}'
    layout_xml=layout_xml.replace(old,
        'app:layout_behavior="com.metallic.chiaki.main.'+behavior+'"')
layout.write_text(layout_xml)

strings=Path('android/app/src/main/res/values/strings.xml')
st=strings.read_text()
assert '<string name="app_name">Chiaki</string>' in st
strings.write_text(st.replace('<string name="app_name">Chiaki</string>',
    '<string name="app_name">GlossaWhaleOS 4.2</string>'))
# Extend Chiaki UI to offer GlossaWhaleOS pairing & on-device translation.
menu=Path('android/app/src/main/res/menu/main.xml')
menu_text=menu.read_text()
assert menu_text.count('</menu>')==1
menu_text=menu_text.replace('</menu>',
    '    <item android:id="@+id/action_glossa" android:title="Glossa Türkçe çeviri" app:showAsAction="always" />\n</menu>')
menu.write_text(menu_text)

main=Path('android/app/src/main/java/com/metallic/chiaki/main/MainActivity.kt')
text=main.read_text()
needle='\t\tR.id.action_settings ->'
assert text.count(needle)==1
text=text.replace(needle,
    '\t\tR.id.action_glossa ->\n\t\t{\n\t\t\tstartActivity(Intent(this, com.metallic.chiaki.glossa.GlossaSettingsActivity::class.java))\n\t\t\ttrue\n\t\t}\n\n'+needle)
main.write_text(text)

# Auto-fill PSN Account ID when entering the existing Chiaki registration
# form from the Glossa account wizard, and select the correct PS4 >=8 profile.
reg=Path('android/app/src/main/java/com/metallic/chiaki/regist/RegistActivity.kt')
regtext=reg.read_text()
needle='\t\tbinding.registButton.setOnClickListener { doRegist() }'
assert regtext.count(needle)==1
regtext=regtext.replace(needle,needle+'''
        val preparedPsnId = intent.getStringExtra("glossa_psn_account_id")
            ?: getSharedPreferences("glossa_psn", MODE_PRIVATE).getString("account_id", "")
        if(!preparedPsnId.isNullOrBlank()) {
            binding.psnIdEditText.setText(preparedPsnId)
            viewModel.ps4Version.value = RegistViewModel.ConsoleVersion.PS4_GE_8
        }
''')
reg.write_text(regtext)

stream=Path('android/app/src/main/java/com/metallic/chiaki/stream/StreamActivity.kt')
text=stream.read_text()
needle='\tprivate lateinit var insetsController: WindowInsetsControllerCompat'
assert text.count(needle)==1
text=text.replace(needle,needle+'\n\tprivate var glossa: com.metallic.chiaki.glossa.GlossaFrameTranslator? = null')
needle='\t\tviewModel.session.attachToSurfaceView(binding.surfaceView)'
assert text.count(needle)==1
text=text.replace(needle,needle+'''
        // Read the actual Chiaki decoder SurfaceView without screen-capture
        // permission or another Remote Play process.
        val glossaStatus = android.widget.TextView(this)
        glossaStatus.setTextColor(android.graphics.Color.WHITE)
        glossaStatus.setBackgroundColor(0xB011243B.toInt())
        glossaStatus.textSize = 12f
        glossaStatus.setPadding(18, 10, 18, 10)
        glossaStatus.visibility = View.GONE
        val glossaParams = android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        glossaParams.topMargin = 18
        binding.root.addView(glossaStatus, glossaParams)
        glossa = com.metallic.chiaki.glossa.GlossaFrameTranslator(
            this, binding.surfaceView) { message ->
            glossaStatus.text = message
            glossaStatus.visibility = if(message.startsWith("Glossa kapalı"))
                View.GONE else View.VISIBLE
        }

        // Adjust the capture rectangle by dragging the cyan frame on the
        // live video. In normal play, this overlay is GONE and intercepts
        // zero game/controller touches.
        val ocrBox = com.metallic.chiaki.glossa.GlossaRoiOverlay(this,binding.surfaceView)
        val editParams = android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT)
        binding.root.addView(ocrBox,editParams)
        val roiButton=android.widget.TextView(this)
        roiButton.text="▣ OCR alanı"
        roiButton.setTextColor(android.graphics.Color.WHITE)
        roiButton.setBackgroundColor(0xDA1E5268.toInt())
        roiButton.textSize=13f
        roiButton.setPadding(22,12,22,12)
        val roiParams=android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.RIGHT)
        roiParams.topMargin=88
        roiParams.rightMargin=16
        binding.root.addView(roiButton,roiParams)
        var isEditing=false
        roiButton.setOnClickListener {
            isEditing=!isEditing
            ocrBox.setEditing(isEditing)
            roiButton.text=if(isEditing)"✓ Kaydet / Kapat" else "▣ OCR alanı"
        }

        // Unified in-game editor: style, themes, OCR quality, game vocabulary
        // all usable WITHOUT opening a separate Activity or pausing PS4.
        lateinit var quickPanel: com.metallic.chiaki.glossa.GlossaQuickPanel
        quickPanel = com.metallic.chiaki.glossa.GlossaQuickPanel(this,
            { quickPanel.dismiss() },
            {
                quickPanel.dismiss()
                isEditing=true
                ocrBox.setEditing(true)
                roiButton.text="✓ Kaydet / Kapat"
            })
        val quickParams=android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * 0.59f).toInt(),
            Gravity.BOTTOM)
        binding.root.addView(quickPanel,quickParams)
        val settingsButton=android.widget.TextView(this)
        settingsButton.text="⚙ Glossa ayarları"
        settingsButton.setTextColor(android.graphics.Color.WHITE)
        settingsButton.setBackgroundColor(0xDA1E5268.toInt())
        settingsButton.textSize=13f
        settingsButton.setPadding(22,12,22,12)
        val settingsParams=android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.LEFT)
        settingsParams.topMargin=88
        settingsParams.leftMargin=16
        binding.root.addView(settingsButton,settingsParams)
        settingsButton.setOnClickListener {
            if(isEditing) {
                isEditing=false
                ocrBox.setEditing(false)
                roiButton.text="▣ OCR alanı"
            }
            quickPanel.toggle()
        }
''')
needle='\t\tviewModel.session.resume()'
assert text.count(needle)==2, 'resume appears in onResume and reconnect'  # used twice
# replace only the method onResume block, preserving reconnect path
method='\toverride fun onResume()\n\t{\n\t\tsuper.onResume()\n\t\thideSystemUI()\n\t\tviewModel.session.resume()\n\t}'
assert text.count(method)==1
text=text.replace(method,method.replace('\t\tviewModel.session.resume()','\t\tviewModel.session.resume()\n\t\tglossa?.start()'))
method='\toverride fun onPause()\n\t{\n\t\tsuper.onPause()\n\t\tviewModel.session.pause()\n\t}'
assert text.count(method)==1
text=text.replace(method,method.replace('\t\tsuper.onPause()','\t\tglossa?.stop()\n\t\tsuper.onPause()'))
method='\toverride fun onDestroy()\n\t{\n\t\tsuper.onDestroy()\n\t\tcontrolsJob?.cancel()\n\t}'
assert text.count(method)==1
text=text.replace(method,method.replace('\t\tsuper.onDestroy()','\t\tglossa?.close()\n\t\tsuper.onDestroy()'))
stream.write_text(text)
PY

mkdir -p android/app/src/main/java/com/metallic/chiaki/glossa
cp "${GITHUB_WORKSPACE}/v3-src/integrated/"*.java android/app/src/main/java/com/metallic/chiaki/glossa/
cp "${GITHUB_WORKSPACE}/v4-src/integrated/"*.java android/app/src/main/java/com/metallic/chiaki/glossa/
cp "${GITHUB_WORKSPACE}/v4-src/integrated/"*.kt android/app/src/main/java/com/metallic/chiaki/glossa/

export JAVA_HOME="${JAVA_HOME:-/opt/hostedtoolcache/Java_Temurin-Hotspot_jdk/17}"
cd android
chmod +x gradlew
./gradlew --no-daemon --stacktrace --max-workers=2 :app:assembleDebug
mkdir -p "${GITHUB_WORKSPACE}/dist-remote"
cp app/build/outputs/apk/debug/app-debug.apk \
  "${GITHUB_WORKSPACE}/dist-remote/GlossaWhaleOS-4.2-Tam-Birlesik-PS4-Telefon.apk"

cat > "${GITHUB_WORKSPACE}/dist-remote/SOURCE_AND_LICENSE.txt" <<'TXT'
GlossaWhaleOS 4.2 Integrated Phone App: Chiaki Remote Play + OCR + translation
Upstream open-source project: chiaki-ng/chiaki-ng
Upstream repository: https://github.com/chiaki-ng/chiaki-ng
Source revision: 43728af2e639fecb8a7af1cd68c29820a40de973

Chiaki-ng is licensed under AGPL-3.0-only with an OpenSSL linking exception.
This APK includes upstream Chiaki Android + core and GlossaWhaleOS's OCR, local English-to-Turkish translation and TV subtitle relay.
Build script with our applicationId/branding/provider patches:
https://github.com/darksccorpionnn/GlossaWhaleOS/blob/feat/glossawhale-v2-split-apps/v4-src/build-v4.sh

This is not Sony software, nor an official Sony application.
One phone APK: Glossa dashboard, native Chiaki PS4/PSN pairing, video decode, OCR, translation, in-game settings and color themes.
The TV overlay receiver remains a separate TV APK; pair using the Glossa menu. PSN and PS4 login credentials must
never be sent to a third party or pasted into chat.
TXT
echo "===== Native APK generated ====="
ls -lh "${GITHUB_WORKSPACE}/dist-remote/"
