from pathlib import Path

REMOTE = Path("remote/chiaki-ng-androidtv")

def replace_once(path: str, old: str, new: str) -> None:
    # The patch strings are stored with visible \t escapes; normalize them to
    # real tabs before matching the upstream Kotlin sources.
    old = old.replace(r"\t", "\t")
    new = new.replace(r"\t", "\t")
    file = REMOTE / path
    text = file.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one matching block, found {count}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")

# Keep the native curl CA path aligned with the new applicationId.
replace_once(
    "native/CMakeLists.txt",
    'set(CURL_CA_BUNDLE "/data/data/com.chiaking.tv/files/ca-bundle.pem" CACHE STRING "Default CA bundle" FORCE)',
    'set(CURL_CA_BUNDLE "/data/data/com.darkscorpion.glossawhale.tv/files/ca-bundle.pem" CACHE STRING "Default CA bundle" FORCE)'
)

# Give the integrated app its own package so it can coexist with the earlier GlossaSub TV APK.
replace_once(
    "app/build.gradle",
    'applicationId "com.chiaking.tv"',
    'applicationId "com.darkscorpion.glossawhale.tv"'
)
replace_once(
    "app/build.gradle",
    'versionCode 38\n\t\tversionName "1.28.1-tv"',
    'versionCode 43\n\t\tversionName "1.28.1-glossawhale-alpha5"'
)
replace_once(
    "app/build.gradle",
    '    implementation libs.zxing.core',
    '    implementation libs.zxing.core\n    implementation "com.google.mlkit:text-recognition:16.0.1"'
)
replace_once(
    "app/src/main/AndroidManifest.xml",
    '    <application\n        android:allowBackup="true"',
    '    <application\n        android:usesCleartextTraffic="true"\n        android:allowBackup="true"'
)
replace_once(
    "app/src/main/res/values/strings.xml",
    '<string name="app_name">Chiaki TV</string>',
    '<string name="app_name">GlossaWhaleOS TV</string>'
)

# Do not send controller packets from this Remote Play session. The player keeps
# the physical DualShock 4 connected to the PS4's local/main account.
replace_once(
    "app/src/main/java/com/chiaking/tv/session/StreamSession.kt",
    """\t\tinput.controllerStateChangedCallback = {
\t\t\tsession?.setControllerState(it)
\t\t}""",
    """\t\tinput.controllerStateChangedCallback = {
\t\t\t// GlossaWhaleOS uses this Remote Play session as a video-only OCR feed.
\t\t\t// Gameplay input stays on the physical DualShock 4 connected to the PS4.
\t\t\tUnit
\t\t}"""
)

replace_once(
    "app/src/main/java/com/chiaking/tv/session/StreamSession.kt",
    """\t\t\t\tsurfaceTexture = surface
\t\t\t\tthis@StreamSession.surface = Surface(surfaceTexture)
\t\t\t\tsession?.setSurface(Surface(surface))""",
    """\t\t\t\tsurfaceTexture = surface
\t\t\t\tval streamSurface = Surface(surface)
\t\t\t\tthis@StreamSession.surface = streamSurface
\t\t\t\tsession?.setSurface(streamSurface)
\t\t\t\tsession?.requestIDR()"""
)

# Replace the visible Remote Play surface with a hidden decoder surface, HDMI passthrough,
# OCR sampling and a Turkish subtitle overlay.
replace_once(
    "app/src/main/java/com/chiaking/tv/ui/screens/stream/StreamScreen.kt",
    """\t\tBoxWithConstraints(modifier = Modifier.fillMaxSize().clipToBounds()) {
\t\t\tval vprofile = session.connectInfo.videoProfile
\t\t\tval vAspect = if(vprofile.height > 0) vprofile.width.toFloat() / vprofile.height else 16f / 9f
\t\t\tval sAspect = maxWidth / maxHeight
\t\t\t// The video buffer always stretches to fill the SurfaceView bounds, so the
\t\t\t// display mode is implemented purely by sizing the view (like VideoView does).
\t\t\tval (vw, vh) = when(displayMode)
\t\t\t{
\t\t\t\tPreferences.DisplayMode.FIT ->
\t\t\t\t\tif(vAspect > sAspect) maxWidth to (maxWidth / vAspect)
\t\t\t\t\telse (maxHeight * vAspect) to maxHeight
\t\t\t\tPreferences.DisplayMode.ZOOM ->
\t\t\t\t\tif(vAspect > sAspect) (maxHeight * vAspect) to maxHeight
\t\t\t\t\telse maxWidth to (maxWidth / vAspect)
\t\t\t\tPreferences.DisplayMode.STRETCH -> maxWidth to maxHeight
\t\t\t}
\t\t\tAndroidView(
\t\t\t\tfactory = { context ->
\t\t\t\t\tSurfaceView(context).also {
\t\t\t\t\t\tsession.attachToSurfaceView(it)
\t\t\t\t\t}
\t\t\t\t},
\t\t\t\tmodifier = Modifier
\t\t\t\t\t.size(vw, vh)
\t\t\t\t\t.align(Alignment.Center)
\t\t\t)
\t\t}""",
    """\t\tGlossaWhaleLayer(session = session)"""
)

replace_once(
    "app/src/main/java/com/chiaking/tv/ui/screens/stream/StreamScreen.kt",
    r"""\t\t\tis StreamStateQuit -> {
\t\t\t\tval quit = state as StreamStateQuit
\t\t\t\tif(session.disconnecting)
\t\t\t\t\t{}
\t\t\t\telse if(quit.reason.isError)
\t\t\t\t\tquitDialog = quit
\t\t\t\telse
\t\t\t\t\tonQuit()
\t\t\t}""",
    r"""\t\t\tis StreamStateQuit -> {
\t\t\t\tval quit = state as StreamStateQuit
\t\t\t\t// Always show the quit reason, even if Chiaki has marked the session disconnecting.
\t\t\t\tquitDialog = quit
\t\t\t}"""
)

helper = r'''// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens.stream

import android.content.Context
import android.graphics.Bitmap
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.media.tv.TvView
import android.net.Uri
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.chiaking.tv.session.StreamSession
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

@Composable
internal fun GlossaWhaleLayer(session: StreamSession)
{
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("glossa_whale", Context.MODE_PRIVATE) }
    var captureView by remember { mutableStateOf<TextureView?>(null) }
    var hdmiAvailable by remember { mutableStateOf(false) }
    var hdmiStatus by remember { mutableStateOf("HDMI görüntüsü aranıyor…") }
    var subtitleText by remember { mutableStateOf("") }
    var phoneIp by remember { mutableStateOf(prefs.getString("phone_ip", "") ?: "") }
    var phonePin by remember { mutableStateOf(prefs.getString("phone_pin", "") ?: "") }
    var showPhoneDialog by remember { mutableStateOf(phoneIp.isBlank() || phonePin.length != 6) }
    var translationStatus by remember { mutableStateOf("OCR çevirisi beklemede") }
    var candidateText by remember { mutableStateOf("") }
    var candidateCount by remember { mutableStateOf(0) }
    var lastSentText by remember { mutableStateOf("") }
    var blankCount by remember { mutableStateOf(0) }
    val inFlight = remember { AtomicBoolean(false) }
    val scope = rememberCoroutineScope()
    val recognizer: TextRecognizer = remember {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    DisposableEffect(recognizer) {
        onDispose { recognizer.close() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // This TextureView receives the Chiaki-decoded Remote Play picture but is kept
        // almost transparent. The HDMI TvView below remains the player's visible picture.
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    isFocusable = false
                    isClickable = false
                    alpha = 0.015f
                    session.attachToTextureView(this)
                    captureView = this
                }
            },
            modifier = Modifier.fillMaxSize().alpha(0.015f)
        )

        AndroidView(
            factory = { ctx ->
                createHdmiView(ctx) { status, available ->
                    hdmiStatus = status
                    hdmiAvailable = available
                }
            },
            modifier = Modifier.fillMaxSize().zIndex(1f)
        )

        Text(
            text = hdmiStatus,
            color = Color.White,
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(14.dp)
                .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .zIndex(2f)
        )

        TextButton(
            onClick = { showPhoneDialog = true },
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).zIndex(3f)
        ) {
            Text("Telefon bağlantısı", color = Color.White)
        }

        if (subtitleText.isNotBlank()) {
            Text(
                text = subtitleText,
                color = Color.White,
                fontSize = 25.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .padding(start = 48.dp, end = 48.dp, bottom = 58.dp)
                    .background(Color.Black.copy(alpha = 0.82f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .zIndex(4f)
            )
        }

        Text(
            text = translationStatus,
            color = Color.White.copy(alpha = 0.78f),
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).zIndex(3f)
        )
    }

    LaunchedEffect(subtitleText) {
        if (subtitleText.isNotBlank()) {
            delay(5200L)
            subtitleText = ""
        }
    }

    LaunchedEffect(captureView, phoneIp, phonePin) {
        if (captureView == null || phoneIp.isBlank() || phonePin.length != 6)
            return@LaunchedEffect

        translationStatus = "PS4 görüntüsünden altyazı aranıyor…"
        while (isActive) {
            delay(850L)
            val view = captureView ?: continue
            if (!inFlight.compareAndSet(false, true))
                continue

            val frame = try { view.getBitmap(960, 540) } catch (_: Exception) { null }
            if (frame == null || frame.width < 80 || frame.height < 80) {
                frame?.recycle()
                inFlight.set(false)
                continue
            }

            // Game subtitles are normally in the lower part of the 16:9 picture.
            val cropX = frame.width / 20
            val cropY = (frame.height * 0.55f).toInt()
            val cropWidth = frame.width - (cropX * 2)
            val cropHeight = frame.height - cropY
            val crop = Bitmap.createBitmap(frame, cropX, cropY, cropWidth, cropHeight)
            frame.recycle()

            recognizer.process(InputImage.fromBitmap(crop, 0))
                .addOnSuccessListener(ContextCompat.getMainExecutor(context)) { result ->
                    val detected = result.text.trim().replace('\n', ' ').replace(Regex("\\s+"), " ")
                    if (detected.length < 4 || detected.count { it.isLetter() } < 3) {
                        blankCount++
                        if (blankCount >= 3) {
                            candidateText = ""
                            candidateCount = 0
                            lastSentText = ""
                        }
                    } else {
                        blankCount = 0
                        if (detected == candidateText)
                            candidateCount++
                        else {
                            candidateText = detected
                            candidateCount = 1
                        }

                        // Require the same OCR line twice to avoid sending transient frame noise.
                        if (candidateCount >= 2 && detected != lastSentText) {
                            lastSentText = detected
                            translationStatus = "Türkçeye çevriliyor…"
                            scope.launch {
                                val translated = sendOcrText(phoneIp, phonePin, detected)
                                if (!translated.isNullOrBlank()) {
                                    subtitleText = translated
                                    translationStatus = "Türkçe altyazı aktif"
                                } else {
                                    lastSentText = ""
                                    translationStatus = "Telefon/API bağlantısı başarısız"
                                }
                            }
                        }
                    }
                }
                .addOnFailureListener(ContextCompat.getMainExecutor(context)) {
                    translationStatus = "OCR okuma hatası"
                }
                .addOnCompleteListener(ContextCompat.getMainExecutor(context)) {
                    crop.recycle()
                    inFlight.set(false)
                }
        }
    }

    if (showPhoneDialog) {
        AlertDialog(
            onDismissRequest = { if (phoneIp.isNotBlank() && phonePin.length == 6) showPhoneDialog = false },
            title = { Text("GlossaWhaleOS telefon bağlantısı") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Telefonda “Görüntü yazısı çeviri sunucusunu başlat” seçeneğini aç. Buraya telefonun IP adresini ve ekrandaki 6 haneli kodu gir.")
                    TextField(
                        value = phoneIp,
                        onValueChange = { phoneIp = it.trim() },
                        label = { Text("Telefon IP adresi") },
                        singleLine = true
                    )
                    TextField(
                        value = phonePin,
                        onValueChange = { phonePin = it.filter(Char::isDigit).take(6) },
                        label = { Text("6 haneli bağlantı kodu") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (phoneIp.isNotBlank() && phonePin.length == 6) {
                        prefs.edit().putString("phone_ip", phoneIp)
                            .putString("phone_pin", phonePin).apply()
                        showPhoneDialog = false
                        translationStatus = "Telefon bağlantısı kaydedildi"
                    }
                }) { Text("Kaydet") }
            },
            dismissButton = {
                TextButton(onClick = { showPhoneDialog = false }) { Text("Daha sonra") }
            }
        )
    }
}

private fun createHdmiView(context: Context, onStatus: (String, Boolean) -> Unit): TvView
{
    val tvView = TvView(context).apply {
        isFocusable = false
        isFocusableInTouchMode = false
        setCallback(object : TvView.TvInputCallback() {
            override fun onConnectionFailed(inputId: String?) {
                onStatus("HDMI bağlantısı başarısız", false)
            }
            override fun onVideoAvailable(inputId: String?) {
                onStatus("HDMI hazır · Remote Play OCR etkin", true)
            }
            override fun onVideoUnavailable(inputId: String?, reason: Int) {
                onStatus("HDMI görüntüsü bekleniyor…", false)
            }
        })
    }

    try {
        val manager = context.getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager
        val passthrough = manager.tvInputList.filter { info ->
            try { info.isPassthroughInput } catch (_: Exception) { false }
        }
        if (passthrough.isEmpty()) {
            onStatus("TV HDMI geçiş kaynağı bulunamadı", false)
        } else {
            val selected = passthrough.firstOrNull {
                it.loadLabel(context).toString().contains("HDMI 1", ignoreCase = true)
            } ?: passthrough.first()
            val uri: Uri = TvContract.buildChannelUriForPassthroughInput(selected.id)
            tvView.tune(selected.id, uri)
        }
    } catch (error: Exception) {
        onStatus("HDMI açılamadı: " + (error.message ?: error.javaClass.simpleName), false)
    }
    return tvView
}

private suspend fun sendOcrText(host: String, pin: String, text: String): String? =
    withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val body = asciiJsonBody(text)
            connection = (URL("http://" + host + ":8787/translate?pin=" + pin).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 25000
                doOutput = true
                useCaches = false
                setFixedLengthStreamingMode(body.size)
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Connection", "close")
            }
            connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299)
                return@withContext null
            JSONObject(response).optString("translated").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

private fun asciiJsonBody(text: String): ByteArray
{
    val escaped = StringBuilder()
    for (ch in text) {
        when (ch) {
            '"' -> escaped.append("\\\"")
            '\\' -> escaped.append("\\\\")
            '\n' -> escaped.append("\\n")
            '\r' -> escaped.append("\\r")
            '\t' -> escaped.append("\\t")
            else -> {
                if (ch.code in 0x20..0x7e)
                    escaped.append(ch)
                else
                    escaped.append("\\u").append(ch.code.toString(16).padStart(4, '0'))
            }
        }
    }
    return ("{\"text\":\"" + escaped + "\"}").toByteArray(Charsets.US_ASCII)
}
'''
(REMOTE / "app/src/main/java/com/chiaking/tv/ui/screens/stream/GlossaWhaleLayer.kt").write_text(helper, encoding="utf-8")

# Reduce OCR frame allocations and sampling load for low-memory TV devices.
replace_once(
    "app/src/main/java/com/chiaking/tv/ui/screens/stream/GlossaWhaleLayer.kt",
    "getBitmap(960, 540)",
    "getBitmap(800, 450)"
)
replace_once(
    "app/src/main/java/com/chiaking/tv/ui/screens/stream/GlossaWhaleLayer.kt",
    "delay(850L)",
    "delay(1200L)"
)

# Rename the phone-side app label, retaining its original application ID.
phone_manifest = Path("GlossaWhaleOS/phone/src/main/AndroidManifest.xml")
phone_text = phone_manifest.read_text(encoding="utf-8")
if 'android:label="GlossaSub Telefon"' in phone_text:
    phone_manifest.write_text(phone_text.replace('android:label="GlossaSub Telefon"', 'android:label="GlossaWhaleOS Telefon"', 1), encoding="utf-8")


# Persist fatal Java/Kotlin crashes so a TV that silently returns to its launcher
# can show the actual stack trace the next time the user opens GlossaWhaleOS.
replace_once(
    "app/src/main/java/com/chiaking/tv/MainActivity.kt",
    """        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)""",
    """        super.onCreate(savedInstanceState)
        val crashFile = java.io.File(filesDir, "glossa-last-crash.txt")
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                crashFile.writeText(
                    "GlossaWhaleOS fatal crash\\nThread: " + thread.name +
                    "\\nDevice: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL +
                    "\\nAndroid: " + android.os.Build.VERSION.RELEASE +
                    "\\nTime: " + java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date()) +
                    "\\n\\n" + android.util.Log.getStackTraceString(error)
                )
            } catch (_: Exception) {}
            previousHandler?.uncaughtException(thread, error)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)"""
)
replace_once(
    "app/src/main/java/com/chiaking/tv/MainActivity.kt",
    """                        Navigation()
                        ImportSettingsDialog(settingsTransfer)""",
    """                        Navigation()
                        ImportSettingsDialog(settingsTransfer)
                        val crashFile = java.io.File(filesDir, "glossa-last-crash.txt")
                        val previousCrashState = androidx.compose.runtime.remember {
                            androidx.compose.runtime.mutableStateOf(
                                try { crashFile.takeIf { it.exists() }?.readText() } catch (_: Exception) { null }
                            )
                        }
                        val previousCrashText = previousCrashState.value.orEmpty()
                        if (previousCrashText.isNotBlank()) {
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = { },
                                title = { androidx.compose.material3.Text("Önceki oturum raporu") },
                                text = {
                                    androidx.compose.foundation.layout.Column {
                                        androidx.compose.material3.Text(
                                            previousCrashText.takeLast(3500),
                                            maxLines = 18
                                        )
                                    }
                                },
                                confirmButton = {
                                    androidx.compose.material3.TextButton(onClick = {
                                        try { crashFile.delete() } catch (_: Exception) {}
                                        previousExitDiagnostic = null
                                        previousCrashState.value = null
                                    }) { androidx.compose.material3.Text("Kaydı kapat") }
                                }
                            )
                        }"""
)

print("Integrated Chiaki HDMI/OCR layer applied.")


# Alpha 6: prefer a discovered local PS4 path over PSN holepunch.
# This removes the internet/PSN relay from same-LAN sessions when the console
# has already been registered and its local address is visible.
replace_once(
    "app/src/main/java/com/chiaking/tv/connect/RemoteConnectController.kt",
    """import com.chiaking.tv.common.MacAddress""",
    """import com.chiaking.tv.common.MacAddress
import com.chiaking.tv.common.DiscoveredDisplayHost
import com.chiaking.tv.common.LastSeenHosts
import com.metallic.chiaki.lib.DiscoveryHost"""
)
replace_once(
    "app/src/main/java/com/chiaking/tv/connect/RemoteConnectController.kt",
    """\t\t\t\tconnectingText = context.getString(R.string.remote_refreshing_token)""",
    """\t\t\t\t// If this PSN console is also visible on the local network, use its
\t\t\t\t// saved registration keys with the LAN address instead of creating a
\t\t\t\t// PSN holepunch session. The local path avoids internet relay churn.
\t\t\t\tval mappedMac = preferences.remoteHostMacs.firstOrNull {
\t\t\t\t\tit.startsWith(device.duid + "|")
\t\t\t\t}?.substringAfter('|')
\t\t\t\tif(mappedMac != null)
\t\t\t\t{
\t\t\t\t\tval localTarget = withContext(Dispatchers.IO) {
\t\t\t\t\t\tval mac = try { MacAddress(mappedMac) } catch (_: Exception) { null }
\t\t\t\t\t\tif(mac == null) null else {
\t\t\t\t\t\t\tval registered = database.registeredHostDao().getByMac(mac)
\t\t\t\t\t\t\tval discovered = LastSeenHosts.hosts.value[mac]
\t\t\t\t\t\t\tif(registered != null && registered.psnAccountId == accountId &&
\t\t\t\t\t\t\t\tdiscovered != null && discovered.state == DiscoveryHost.State.READY &&
\t\t\t\t\t\t\t\t!discovered.hostAddr.isNullOrBlank())
\t\t\t\t\t\t\t\tregistered to discovered
\t\t\t\t\t\t\telse null
\t\t\t\t\t\t}
\t\t\t\t\t}
\t\t\t\t\tif(localTarget != null)
\t\t\t\t\t{
\t\t\t\t\t\ttry
\t\t\t\t\t\t{
\t\t\t\t\t\t\tval localSession = StreamManager.start(context, preferences, logManager,
\t\t\t\t\t\t\t\tDiscoveredDisplayHost(localTarget.first, localTarget.second))
\t\t\t\t\t\t\tif(localSession != null)
\t\t\t\t\t\t\t{
\t\t\t\t\t\t\t\tLog.i(TAG, "PSN console is visible on LAN; using direct local connection")
\t\t\t\t\t\t\t\tconnectingText = null
\t\t\t\t\t\t\t\tonStreamReady(localSession)
\t\t\t\t\t\t\t\treturn@launch
\t\t\t\t\t\t\t}
\t\t\t\t\t\t}
\t\t\t\t\t\tcatch(localError: Exception)
\t\t\t\t\t\t{
\t\t\t\t\t\t\tLog.w(TAG, "Local connection attempt failed; falling back to PSN", localError)
\t\t\t\t\t\t}
\t\t\t\t\t}
\t\t\t\t}
\t\t\t\tconnectingText = context.getString(R.string.remote_refreshing_token)"""
)

# Always enable Chiaki's verbose session log, and persist the exact host quit reason.
replace_once(
    "app/src/main/java/com/chiaking/tv/MainActivity.kt",
    """        preferences = Preferences(applicationContext)""",
    """        preferences = Preferences(applicationContext)
        preferences.logVerbose = true
        collectPreviousExitDiagnostic()"""
)
replace_once(
    "app/src/main/java/com/chiaking/tv/MainActivity.kt",
    """    private var coopPickerController: CoopAccountPickerController? = null""",
    """    private var coopPickerController: CoopAccountPickerController? = null
    private var previousExitDiagnostic: String? = null"""
)
replace_once(
    "app/src/main/java/com/chiaking/tv/MainActivity.kt",
    """    override fun onResume()
    {""",
    """    private fun collectPreviousExitDiagnostic()
    {
        if(android.os.Build.VERSION.SDK_INT < 30)
            return
        try
        {
            val store = getSharedPreferences("glossa_exit_diagnostics", MODE_PRIVATE)
            val lastSeen = store.getLong("last_seen_exit_timestamp", 0L)
            val manager = getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val info = manager.getHistoricalProcessExitReasons(packageName, 0, 10)
                .firstOrNull { it.timestamp > lastSeen }
            if(info != null)
            {
                val signalName = when(info.status) {
                    4 -> "SIGILL (illegal instruction)"
                    5 -> "SIGTRAP (trace trap)"
                    6 -> "SIGABRT (abort)"
                    7 -> "SIGBUS (bus error)"
                    11 -> "SIGSEGV (invalid memory access)"
                    else -> "signal/status " + info.status
                }
                var tombstoneText = "Native tombstone: not available"
                if(android.os.Build.VERSION.SDK_INT >= 31 &&
                    info.reason == android.app.ApplicationExitInfo.REASON_CRASH_NATIVE)
                {
                    try {
                        info.traceInputStream?.use { stream ->
                            val bytes = stream.readBytes().take(2 * 1024 * 1024).toByteArray()
                            val readable = StringBuilder()
                            val run = StringBuilder()
                            for(byteValue in bytes) {
                                val value = byteValue.toInt() and 0xff
                                if(value in 32..126 || value == 10 || value == 13 || value == 9) {
                                    run.append(value.toChar())
                                } else {
                                    if(run.length >= 4) readable.append(run).append('\\\\n')
                                    run.setLength(0)
                                }
                            }
                            if(run.length >= 4) readable.append(run).append('\\\\n')
                            tombstoneText = "Native tombstone bytes: " + bytes.size +
                                "\\\\nReadable native trace strings:\\\\n" +
                                readable.toString().takeLast(12000)
                        }
                    } catch(traceError: Exception) {
                        tombstoneText = "Native tombstone read failed: " + traceError.toString()
                    }
                }
                val reportText =
                    "Android process exit report" +
                    "\\\\nReason code: " + info.reason +
                    "\\\\nDescription: " + (info.description?.toString() ?: "(none)") +
                    "\\\\nStatus/signal: " + info.status + " / " + signalName +
                    "\\\\nImportance: " + info.importance +
                    "\\\\nPSS/RSS kB: " + info.pss + " / " + info.rss +
                    "\\\\nProcess: " + info.processName +
                    "\\\\nTime: " + java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                        .format(java.util.Date(info.timestamp)) +
                    "\\\\n\\\\n" + tombstoneText
                previousExitDiagnostic = reportText
                try {
                    val logDir = java.io.File(filesDir, "session_logs")
                    logDir.mkdirs()
                    val stamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", java.util.Locale.US)
                        .format(java.util.Date(info.timestamp))
                    java.io.File(logDir, "chiaki_session_" + stamp + "_native-crash.log").writeText(reportText)
                } catch(logError: Exception) {
                    android.util.Log.e("GlossaWhaleOS", "Could not save native crash report", logError)
                }
                store.edit().putLong("last_seen_exit_timestamp", info.timestamp).apply()
            }
        }
        catch(error: Exception)
        {
            android.util.Log.e("GlossaWhaleOS", "Could not read previous process exit report", error)
        }
    }

    override fun onResume()
    {"""
)
replace_once(
    "app/src/main/java/com/chiaking/tv/MainActivity.kt",
    """                        val previousCrashText = previousCrashState.value.orEmpty()""",
    """                        val previousCrashText = previousCrashState.value.orEmpty().let { crash ->
                            val exit = previousExitDiagnostic
                            if (exit.isNullOrBlank()) crash
                            else if (crash.isBlank()) exit
                            else crash + "\\n\\n" + exit
                        }"""
)

# Make the report dialog explicitly dismissible in Compose state. This avoids
# relying on a non-observable Activity property to control dialog visibility.
replace_once(
    "app/src/main/java/com/chiaking/tv/MainActivity.kt",
    """                        if (previousCrashText.isNotBlank()) {""",
    """                        val reportVisible = androidx.compose.runtime.remember {
                            androidx.compose.runtime.mutableStateOf(previousCrashText.isNotBlank())
                        }
                        if (reportVisible.value) {"""
)
replace_once(
    "app/src/main/java/com/chiaking/tv/MainActivity.kt",
    """                                onDismissRequest = { },""",
    """                                onDismissRequest = { reportVisible.value = false },"""
)
replace_once(
    "app/src/main/java/com/chiaking/tv/MainActivity.kt",
    """                                        previousCrashState.value = null""",
    """                                        previousCrashState.value = null
                                        reportVisible.value = false"""
)
replace_once(
    "app/src/main/java/com/chiaking/tv/session/StreamSession.kt",
    """\t\t\tis QuitEvent ->
\t\t\t{
\t\t\t\tval elapsed = SystemClock.elapsedRealtime() - startTime""",
    """\t\t\tis QuitEvent ->
\t\t\t{
\t\t\t\tval quitReport = "Time: " +
\t\t\t\t\tjava.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date()) +
\t\t\t\t\t"\\nReason: " + event.reason.toString() +
\t\t\t\t\t"\\nReason detail: " + (event.reasonString ?: "(none)") +
\t\t\t\t\t"\\nConnected before quit: " + connected + "\\n"
\t\t\t\ttry {
\t\t\t\t\tjava.io.File(logManager.baseDir.parentFile, "glossa-last-session-quit.txt").appendText(quitReport)
\t\t\t\t} catch (_: Exception) {}
\t\t\t\tandroid.util.Log.e("GlossaWhaleOS", quitReport)
\t\t\t\tval elapsed = SystemClock.elapsedRealtime() - startTime"""
)
print("Alpha 6 local-first and diagnostic patches appended.")
