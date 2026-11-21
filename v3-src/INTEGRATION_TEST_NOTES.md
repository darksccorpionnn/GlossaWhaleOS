# GlossaWhaleOS 3.0 – unified phone experiment

## Actual integration (implemented)

One Android phone APK is built from the pinned chiaki-ng Android Remote Play
source with Glossa-specific source files inserted at compile time.

- **PS4 connection:** unchanged Chiaki native C core, login and pairing UI.
- **Video source:** the actual `SurfaceView` created in
  `com.metallic.chiaki.stream.StreamActivity`. The bridge calls Android
  `PixelCopy.request(surfaceView, 640×360 Bitmap)`. This is *not* an HDMI
  framebuffer or MediaProjection screen recording.
- **OCR:** cropped lower-center region (~8–92% width, 55–96% height)
  passed to bundled ML Kit Latin text recognition.
- **Translation:** English→Turkish using ML Kit on-device translation.
  The translation model is downloaded on first use.
- **Low-latency safeguards:** one outstanding frame operation, ~290 ms
  scan interval, reusable text translation cache, duplicate rejection,
  non-blocking network executor, stale translation suppression, and
  status displaying end-to-end elapsed time (capture to TV response).
- **TV delivery:** HTTP POST to existing GlossaWhaleOS TV receiver at
  `http://<private-ip>:8799/subtitle?pin=<six-digit-pin>`. The phone
  only sends translated text; the TV's HDMI video stays unchanged.
- **Config:** Chiaki toolbar action “Glossa Türkçe çeviri” opens the
  integrated TV-pairing/settings page and test-subtitle action.
- **Lifecycle:** capture starts only after the user enables Glossa and
  opens a PS4 stream, stops on app pause/stream exit and releases ML Kit
  resources when the stream Activity is destroyed.

## Initial hardware test

1. Install unified APK on the Android phone. It has a **different package ID**
   from the existing working Chiaki and V2 phone APKs, allowing a safe test.
2. Open Glossa settings in the Chiaki toolbar; enter TV IP + PIN from the
   currently running GlossaWhaleOS TV receiver. Activate translation.
3. Send test subtitle. Confirm TV overlays it above the PS4 HDMI picture.
4. In the new phone APK pair the PS4 using the secondary PSN account and
   the fresh PS4 8-digit Add Device code; do not reuse a screenshot of PIN.
   The old app's registration is not automatically imported.
5. Start the PS4 stream. Open an English-subtitled RDR 2 scene and observe
   Glossa's status on the phone and the TV subtitles.
6. If video-copy fails, report displayed PixelCopy result code. If the model
   cannot download, connect phone to the internet and retry.
7. Measure actual capture-to-TV time on the phone's stream overlay. This is
   only a pipeline metric, not a precise measurement of PS4→TV subtitle
   latency. Compare camera video of PS4 English and TV Turkish to estimate
   display delay when needed.

## Limitations requiring hardware verification

- CI build only checks compilation/APK packaging, not that PixelCopy can
  read a hardware-decoded SurfaceView on this specific POCO device.
  Some hardware paths may return `ERROR_SOURCE_NO_DATA`.
- Current heuristic scans the bottom-central image region. Some games
  place captions elsewhere; their region needs adjustment.
- ML Kit OCR and translation performance/accuracy vary by device and model.
  Zero-latency translation is impossible; 100–300 ms is **not guaranteed**.
- The main UI still resembles upstream Chiaki with an integrated Glossa
  settings panel; later visual refinement can be done without rewriting the
  successfully paired Chiaki protocol.
- This build should be considered a **real-device integration test**, not a
  fully validated final release.

## Licensing and sources

Chiaki-ng copyright and AGPL-3.0-only with OpenSSL linking exception apply
to the upstream components, which remain source-available. The build script
pins the upstream revision. Glossa bridge sources are under `v3-src/integrated/`.
The TV-side receiver remains in `v2-src/tv/` and is not rebuilt here.
