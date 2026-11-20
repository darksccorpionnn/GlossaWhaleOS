from pathlib import Path

ROOT = Path("GlossaWhaleOS")

def replace_once(path: str, old: str, new: str) -> None:
    file = ROOT / path
    text = file.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one matching source block, found {count}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")

replace_once(
    "phone/src/main/java/com/darkscorpion/glossasub/phone/PhoneActivity.kt",
    """        content.addView(Button(this).apply {
            this.text = "TV'ye test altyazısı gönder"
            setOnClickListener {
                if (listening) startService(Intent(this@PhoneActivity, SubtitleService::class.java).setAction(SubtitleService.ACTION_TEST))
                else Toast.makeText(this@PhoneActivity, "Önce dinlemeyi başlat.", Toast.LENGTH_SHORT).show()
            }
        }, matchWidth())""",
    """        content.addView(Button(this).apply {
            this.text = "TV'ye test altyazısı gönder"
            setOnClickListener {
                if (listening) startService(Intent(this@PhoneActivity, SubtitleService::class.java).setAction(SubtitleService.ACTION_TEST))
                else Toast.makeText(this@PhoneActivity, "Önce dinlemeyi başlat.", Toast.LENGTH_SHORT).show()
            }
        }, matchWidth())
        content.addView(Button(this).apply {
            this.text = "Görüntü yazısı çeviri sunucusunu başlat"
            setOnClickListener { startTextTranslationServer() }
        }, matchWidth())"""
)

replace_once(
    "phone/src/main/java/com/darkscorpion/glossasub/phone/PhoneActivity.kt",
    'content.addView(text("Not: İlk sürüm telefonun mikrofonunu kullanır. Telefonu TV hoparlörünü duyabileceği bir yere koy. HDMI sesini doğrudan telefona aktardığını varsaymıyoruz.", 14f))',
    'content.addView(text("Görsel altyazı modunda telefon mikrofonu kullanılmaz; TV uygulaması OCR ile okuduğu yazıyı yerel ağ üzerinden bu telefona gönderir. Sesli dinleme modu ayrı olarak kullanılabilir.", 14f))'
)

replace_once(
    "phone/src/main/java/com/darkscorpion/glossasub/phone/PhoneActivity.kt",
    """    private fun matchWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = 8
    }

    private fun startListening() {""",
    """    private fun matchWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = 8
    }

    private fun startTextTranslationServer() {
        val key = apiKey.text.toString().trim()
        if (key.isBlank()) {
            Toast.makeText(this, "Önce Gemini API anahtarını gir.", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(this, SubtitleService::class.java)
            .setAction(SubtitleService.ACTION_START_TEXT_TRANSLATION)
            .putExtra("apiKey", key)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        listening = true
        statusView.text = "Durum: Görüntü yazısı çeviri sunucusu başlatılıyor… Mikrofon kullanılmayacak."
    }

    private fun startListening() {"""
)

replace_once(
    "phone/src/main/java/com/darkscorpion/glossasub/phone/SubtitleService.kt",
    '        const val ACTION_START = "com.darkscorpion.glossasub.START"',
    '        const val ACTION_START = "com.darkscorpion.glossasub.START"\n        const val ACTION_START_TEXT_TRANSLATION = "com.darkscorpion.glossasub.START_TEXT_TRANSLATION"'
)

replace_once(
    "phone/src/main/java/com/darkscorpion/glossasub/phone/SubtitleService.kt",
    '    private var language = "en-US"',
    '    private var language = "en-US"\n    @Volatile private var translationOnly = false'
)

replace_once(
    "phone/src/main/java/com/darkscorpion/glossasub/phone/SubtitleService.kt",
    """            ACTION_START -> {
                apiKey = intent.getStringExtra("apiKey").orEmpty()
                language = intent.getStringExtra("language")?.takeIf { it.isNotBlank() } ?: "en-US"
                startForegroundCompat()
                startServer()
                startRecognition()
            }""",
    """            ACTION_START -> {
                apiKey = intent.getStringExtra("apiKey").orEmpty()
                language = intent.getStringExtra("language")?.takeIf { it.isNotBlank() } ?: "en-US"
                translationOnly = false
                startForegroundCompat(microphone = true)
                startServer()
                startRecognition()
            }
            ACTION_START_TEXT_TRANSLATION -> {
                apiKey = intent.getStringExtra("apiKey").orEmpty()
                translationOnly = true
                try { recognizer?.cancel(); recognizer?.destroy() } catch (_: Exception) { }
                recognizer = null
                startForegroundCompat(microphone = false)
                startServer()
                sendStatus("Görüntü yazısı çeviri sunucusu hazır · mikrofon kapalı", localIpv4(), pin)
            }"""
)

replace_once(
    "phone/src/main/java/com/darkscorpion/glossasub/phone/SubtitleService.kt",
    "    private fun startForegroundCompat() {",
    "    private fun startForegroundCompat(microphone: Boolean) {"
)

replace_once(
    "phone/src/main/java/com/darkscorpion/glossasub/phone/SubtitleService.kt",
    '.setContentText("Oyun sesini dinleyip altyazı gönderiyor")',
    '.setContentText(if (microphone) "Oyun sesini dinleyip altyazı gönderiyor" else "Görüntü yazısı için çeviri sunucusu açık")'
)

replace_once(
    "phone/src/main/java/com/darkscorpion/glossasub/phone/SubtitleService.kt",
    """        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else startForeground(1, notification)""",
    """        if (Build.VERSION.SDK_INT >= 29 && microphone) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else startForeground(1, notification)"""
)

replace_once(
    "phone/src/main/java/com/darkscorpion/glossasub/phone/SubtitleService.kt",
    """            while (true) { val h = reader.readLine() ?: break; if (h.isEmpty()) break }""",
    """            val headers = HashMap<String, String>()
            while (true) {
                val h = reader.readLine() ?: break
                if (h.isEmpty()) break
                val separator = h.indexOf(':')
                if (separator > 0) headers[h.substring(0, separator).trim().lowercase()] = h.substring(separator + 1).trim()
            }"""
)

replace_once(
    "phone/src/main/java/com/darkscorpion/glossasub/phone/SubtitleService.kt",
    """            if (!path.startsWith("/events")) {
                writeHttp(socket, 404, "text/plain", "Not found")
                socket.close(); return
            }
""",
    ""
)

replace_once(
    "phone/src/main/java/com/darkscorpion/glossasub/phone/SubtitleService.kt",
    """            if (query["pin"] != pin) {
                writeHttp(socket, 403, "text/plain", "Wrong pairing code")
                socket.close(); return
            }
            socket.soTimeout = 0""",
    """            if (query["pin"] != pin) {
                writeHttp(socket, 403, "text/plain", "Wrong pairing code")
                socket.close(); return
            }
            if (path.startsWith("/translate")) {
                if (parts.firstOrNull() != "POST") {
                    writeHttp(socket, 405, "text/plain", "POST required")
                    socket.close(); return
                }
                if (!translationOnly || apiKey.isBlank()) {
                    writeHttp(socket, 409, "application/json", JSONObject().put("ok", false).put("error", "Text translation server is not active").toString())
                    socket.close(); return
                }
                val contentLength = headers["content-length"]?.toIntOrNull() ?: -1
                if (contentLength !in 1..8192) {
                    writeHttp(socket, 400, "application/json", JSONObject().put("ok", false).put("error", "Invalid request length").toString())
                    socket.close(); return
                }
                // The TV client sends ASCII-safe JSON (non-ASCII characters escaped as \uXXXX).
                // This keeps HTTP byte length equal to the number of characters read here.
                val body = CharArray(contentLength)
                var bodyRead = 0
                while (bodyRead < contentLength) {
                    val count = reader.read(body, bodyRead, contentLength - bodyRead)
                    if (count < 0) break
                    bodyRead += count
                }
                if (bodyRead != contentLength) {
                    writeHttp(socket, 400, "application/json", JSONObject().put("ok", false).put("error", "Incomplete request body").toString())
                    socket.close(); return
                }
                val source = JSONObject(String(body)).optString("text").trim()
                if (source.isBlank() || source.length > 1200) {
                    writeHttp(socket, 400, "application/json", JSONObject().put("ok", false).put("error", "Text must contain 1-1200 characters").toString())
                    socket.close(); return
                }
                val translated = GeminiTranslator.translate(apiKey, source).ifBlank { source }
                broadcastEvent("subtitle", JSONObject().put("text", translated).put("duration", 5000).put("replace", true))
                sendStatus("Görüntü yazısı çevrildi: \${source.take(90)}", localIpv4(), pin)
                writeHttp(socket, 200, "application/json", JSONObject().put("ok", true).put("translated", translated).toString())
                socket.close(); return
            }
            if (!path.startsWith("/events")) {
                writeHttp(socket, 404, "text/plain", "Not found")
                socket.close(); return
            }
            socket.soTimeout = 0"""
)

replace_once(
    "phone/src/main/java/com/darkscorpion/glossasub/phone/SubtitleService.kt",
    'val prompt = "Translate this spoken English video-game dialogue into natural, concise Turkish subtitle text. " +',
    'val prompt = "Translate this English video-game subtitle or dialogue into natural, concise Turkish subtitle text. " +'
)

replace_once(
    "phone/src/main/AndroidManifest.xml",
    '    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />',
    '    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />\n    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />'
)

replace_once(
    "phone/src/main/AndroidManifest.xml",
    'android:foregroundServiceType="microphone" />',
    'android:foregroundServiceType="microphone|dataSync" />'
)

print("Alpha 4 phone text-translation changes applied.")
