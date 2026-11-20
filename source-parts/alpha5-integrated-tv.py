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
    'versionCode 39\n\t\tversionName "1.28.1-glossawhale-alpha1"'
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

# Rename the phone-side app label, retaining its original application ID.
phone_manifest = Path("GlossaWhaleOS/phone/src/main/AndroidManifest.xml")
phone_text = phone_manifest.read_text(encoding="utf-8")
if 'android:label="GlossaSub Telefon"' in phone_text:
    phone_manifest.write_text(phone_text.replace('android:label="GlossaSub Telefon"', 'android:label="GlossaWhaleOS Telefon"', 1), encoding="utf-8")

print("Integrated Chiaki HDMI/OCR layer applied.")
