# GlossaWhaleOS 2.1 - LAN discovery

- Phone sends the Chiaki PS4 UDP discovery packet to port 987 with protocol version 00020020. It accepts local PS4 discovery responses only, showing the console name, IP, state, and firmware version. It does not wake, register, or stream.
- Phone also broadcasts `GLOSSA_TV_DISCOVERY v1` to UDP 8798.
- TV receiver answers from port 8798 when started, and automatically starts on opening the TV app if overlay permission has been granted.
- A valid local IP and the TV's existing 6-digit pairing PIN are still required for text sending. The PIN is intentionally **not** included in unauthenticated discovery replies. Phone saves this pairing PIN after first user entry, and checks the TV's authenticated /health endpoint automatically on subsequent scans. No PIN appears in the UDP discovery announcement.
- Discovery is non-destructive, requires both devices on the same LAN without client isolation, and should complete within about 4.2 seconds.
- This version is **not a Remote Play client**. Next stages require a real Chiaki-based PS4 registration/session/decoder and secure PSN Account ID acquisition, tested on a physical phone.
- HDMI overlay visibility is device/firmware-dependent and not confirmed on Hauzen WhaleOS.
- Build the pair with `.github/workflows/v2-build.yml`.

- V2.1 uses distinct Android package IDs (.v21) so old per-run debug signing keys do not block installation over v2.0. Old apps may be uninstalled manually after testing.
