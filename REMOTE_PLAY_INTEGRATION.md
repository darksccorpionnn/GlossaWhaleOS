# GlossaWhaleOS Remote Play + Visual Subtitle Integration

Status: implementation branch opened; integration not yet shipped.

## Target user experience

- Keep the PS4 connected to the TV by HDMI. The HDMI picture remains the picture the player watches.
- Use a separate Remote Play session only as a video source for subtitle recognition, without forwarding gamepad input from the TV app.
- Keep the physical DualShock 4 connected to the PS4 and use the player's main PSN profile for gameplay.
- Read subtitles from the Remote Play video frames, translate them to Turkish, and render the translated line in the existing GlossaSub overlay above HDMI.
- Keep phone-to-TV local pairing as the translation/configuration link where useful. It must not be required for the PS4 video stream itself.

## What the current GlossaSub code actually does

The current TV app opens the first Android TV passthrough input with `TvView`, places a subtitle `TextView` above it, and connects to the phone over HTTP Server-Sent Events on port 8787 using a six-digit PIN. The phone app currently uses Android `SpeechRecognizer` on the microphone and sends translated speech phrases to the TV. It does **not** connect to PS4 Remote Play.

The Alpha 3 patch adds ML Kit Latin OCR and a one-shot diagnostic using `PixelCopy.request(window, ...)`. That is only a window-capture experiment; it is not continuous subtitle OCR and it does not read the TV HDMI hardware layer. The diagnostic button is not present in the older Alpha 2 app currently shown on the TV.

## Remote Play implementation baseline

Use the Android TV port of Chiaki-ng as the protocol/native reference:
- https://github.com/worstenbrood/chiaki-ng-androidtv
- https://github.com/chiaki-ng/chiaki-ng

The Android TV port documents LAN console discovery, PS4/PS5 PIN registration, video streaming, Android gamepad input, and PSN account management. Its streaming path uses the Chiaki native core (C/C++, JNI, Android NDK/CMake) and sends decoded video to a `SurfaceView`. Its stream session exposes a `SurfaceView` attachment point. This is a much more suitable OCR input candidate than the old app-window PixelCopy test.

Do not copy only the Compose screen. The working Remote Play functionality depends on the native Chiaki core, JNI bindings, CMake configuration, and submodules. Keep the protocol core and JNI as a version-pinned upstream component, then implement a small GlossaWhaleOS adapter around console discovery, PIN registration, and a video-only session.

## Planned integration layers

1. **Remote Play core:** import/pin the Chiaki-ng native core and Android JNI dependencies; add local discovery, PS4 registration PIN, account ID storage, connect/disconnect, and status/log reporting.
2. **Video-only OCR session:** attach the Remote Play decoder to a dedicated surface used only for recognition. Do not route the television's physical controller input into this session. Investigate direct frame access from the decoder first; use `PixelCopy.request(surfaceView, ...)` only as a fallback and verify on the target Whale OS TV.
3. **Visual text recognition:** reuse bundled ML Kit Latin OCR; crop the lower subtitle region, throttle frame processing, reject duplicate/unstable lines, and preserve multi-line subtitle order.
4. **Translation bridge:** change the phone service from microphone-only to an explicit OCR-text translation endpoint. Reuse the existing Gemini translation function and send completed Turkish text to the TV over the existing paired event stream. Keep the API key on the phone.
5. **HDMI overlay:** retain the existing `TvView` HDMI passthrough and subtitle `TextView`. The Remote Play decoder must not replace the HDMI picture. Add a setting for OCR/Remote Play start-stop and status.
6. **Account/controller split test:** keep the Remote Play account separate from the local PS4 player account. Do not assume this works merely because the app can store multiple PSN accounts: it must be tested on the actual PS4, with both PSN users registered and a physical DS4 connected. The TV app must be configured as video-only so it cannot steal controller focus.

## Dual-account limitation to verify

Sony's PS4 Remote Play multiplayer support article says a session can involve one Xperia device and one DualShock 4 connected to the PS4 at the same time, and that both PSN IDs must be registered on the console:
https://www.sony.com/electronics/support/articles/SX365401

That is useful evidence for the desired account split, but it does not certify a third-party Remote Play client running on this Whale OS Android TV as equivalent to an Xperia device. Therefore the exact second-account + local-DS4 behavior is a hardware acceptance test, not a promised feature yet.

## Build and licensing constraints

The Chiaki-ng Android TV port states its project is licensed under AGPL-3.0-only with an OpenSSL link exception. Any integration/re-distribution must retain the required notices and make the corresponding source and modifications available under the applicable license terms. The C core also requires Android NDK/CMake and its native dependencies; simply adding a Maven dependency or drawing a pairing screen is not a Remote Play implementation.

## Acceptance criteria before a combined APK is handed over

- Build both phone and TV APKs from the integration branch.
- TV APK launches and preserves HDMI passthrough.
- PS4 is discovered or can be added manually; PIN registration succeeds.
- Remote Play video starts without sending input to PS4.
- A subtitle visible in the Remote Play video is read from its decoded frame.
- OCR output is translated and appears over the HDMI picture.
- A DualShock 4 connected directly to PS4 continues to control the main local user during the video-only Remote Play session.
- Connection loss, app exit, and HDMI input loss recover without leaving a black screen.
