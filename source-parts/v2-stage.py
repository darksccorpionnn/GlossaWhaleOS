"""Assemble the new, deliberately split GlossaWhaleOS 2.0 applications.

The original alpha2 project contributes a working Android Gradle wrapper and
module configuration. The old phone/TV source is replaced, NOT mixed into V2.
The native Chiaki project is not built or linked into the TV receiver.
"""
from pathlib import Path
import re
import shutil

PROJECT = Path("GlossaWhaleOS")
ASSETS = Path("v2-src")

for module in ("phone", "tv"):
    app = PROJECT / module
    if not app.is_dir():
        raise SystemExit(f"missing original Android module: {app}")

    java_root = app / "src/main/java"
    if java_root.exists():
        shutil.rmtree(java_root)
    package = f"com/darkscorpion/glossasub/{module}"
    destination = java_root / package
    destination.mkdir(parents=True)

    for source in (ASSETS / module).glob("*.java"):
        shutil.copyfile(source, destination / source.name)

    common = (ASSETS / "common/GlossaUI.java").read_text()
    (destination / "GlossaUI.java").write_text(
        common.replace("__MODULE__", module), encoding="utf-8"
    )

    # Keep the existing Gradle plugin resolution proven in the previous builds.
    gradle_path = app / "build.gradle.kts"
    gradle = gradle_path.read_text()
    new_id = f"com.darkscorpion.glossawhale.{module}.v2"
    gradle, n_id = re.subn(
        r'applicationId\s*=\s*"[^"]+"',
        f'applicationId = "{new_id}"', gradle, count=1
    )
    if n_id != 1:
        raise SystemExit(f"{module}: expected one applicationId")
    gradle, n_version = re.subn(r'versionCode\s*=\s*\d+',
        'versionCode = 200', gradle, count=1)
    gradle, n_name = re.subn(r'versionName\s*=\s*"[^"]+"',
        'versionName = "2.0.0-alpha1"', gradle, count=1)
    if n_version != 1 or n_name != 1:
        raise SystemExit(f"{module}: Gradle version values not found")
    gradle, n_target = re.subn(r'targetSdk\s*=\s*\d+',
        'targetSdk = 32', gradle, count=1)
    if n_target != 1:
        raise SystemExit(f"{module}: targetSdk not found")
    if module == "phone":
        for dependency in (
            "com.google.mlkit:text-recognition:16.0.1",
            "com.google.mlkit:translate:17.0.3",
        ):
            if dependency not in gradle:
                if "dependencies {" in gradle:
                    gradle = gradle.replace(
                        "dependencies {",
                        'dependencies {\n    implementation("' + dependency + '")', 1
                    )
                else:
                    gradle += '\ndependencies {\n    implementation("' + dependency + '")\n}\n'
        if "com.google.mlkit:translate:17.0.3" not in gradle:
            raise SystemExit("Missing offline translation dependency")
    gradle_path.write_text(gradle, encoding="utf-8")
    print(module, new_id, "Gradle configured")

phone_manifest = r'''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <queries>
        <package android:name="com.playstation.remoteplay" />
    </queries>
    <application
        android:label="GlossaWhaleOS 2 Telefon"
        android:theme="@android:style/Theme.Material.NoActionBar"
        android:usesCleartextTraffic="true"
        android:allowBackup="false"
        android:supportsRtl="true">
        <activity
            android:name="com.darkscorpion.glossasub.phone.PhoneActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <service
            android:name="com.darkscorpion.glossasub.phone.OcrCaptureService"
            android:exported="false"
            android:foregroundServiceType="mediaProjection" />
    </application>
</manifest>
'''

tv_manifest = r'''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-feature android:name="android.software.leanback" android:required="false" />
    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <application
        android:label="GlossaWhaleOS 2 TV"
        android:theme="@android:style/Theme.Material.NoActionBar"
        android:usesCleartextTraffic="true"
        android:allowBackup="false"
        android:supportsRtl="true">
        <activity
            android:name="com.darkscorpion.glossasub.tv.TvActivity"
            android:screenOrientation="landscape"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>
        <activity
            android:name="com.darkscorpion.glossasub.tv.HdmiActivity"
            android:screenOrientation="landscape"
            android:exported="false" />
        <service
            android:name="com.darkscorpion.glossasub.tv.SubtitleReceiverService"
            android:exported="false"
            android:foregroundServiceType="dataSync" />
    </application>
</manifest>
'''

(PROJECT / "phone/src/main/AndroidManifest.xml").write_text(phone_manifest, encoding="utf-8")
(PROJECT / "tv/src/main/AndroidManifest.xml").write_text(tv_manifest, encoding="utf-8")
print("GlossaWhaleOS 2.0: fresh phone and TV source installed; no legacy native decoder")
