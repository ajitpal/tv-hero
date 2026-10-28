# tv-hero: instructions for AI coding agents

Ajit's own TV project: one Xiaomi Mi TV 4A Pro (MiTV-AXSO0, Android TV 9, ~1 GB RAM, 32-bit, 720p UI) plus his phone. Read `README.md` first, then `docs/SPEC.md` (measured hardware, decisions) and `docs/PROTOCOL.md`.

- **Layout:** `tv/` TV app (Java, home screen + guard + control server, package `com.ajit.tvhero`), `phone/` phone app (Kotlin + Compose, `com.ajit.tvhero.remote`), `tools/` ADB-over-Wi-Fi scripts and a Python API client, `debloat/` what's switched off on the TV, `docs/`.
- **Build:** `(cd tv && ./gradlew assembleRelease testReleaseUnitTest)`, `(cd phone && ./gradlew assembleRelease testReleaseUnitTest lintRelease)`. JDK 17+, SDK 34. Maven Central sometimes answers 429: wait and retry.
- **TV rules:** keep it light (no Compose on the TV, no blur/video/polling); D-pad navigation must stay deterministic; the guard reads only window changes, never screen content; every TV change is reversible (disable, never uninstall system apps) and verified with a screenshot from the TV (`tools/tv.py --screenshot`).
- **Protocol changes** go in `docs/PROTOCOL.md`, `tv/.../Api.java`, `phone/.../TvClient.kt`, `tools/tvhero_client.py` together; update the vectors in `phone/.../TvProtocolTest.kt` from the Python client.
- **Install on the TV:** `TV_HOST=<ip> tools/install-tv.sh`. After an update the guard may need one reboot to bind (the script handles it).
- **Never commit** `keystore.properties`, `*.jks`, `tools/adbkey/`, `baseline/` (device identifiers). The parent password and pairing secrets never appear in logs.
- **Priorities:** bug fixes before features; Apple-HIG-style clean UI on both apps; update README and docs with every change.
