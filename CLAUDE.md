# tv-hero: instructions for AI coding agents

tv-hero: a calm, safe home screen and family controls for Android TV, run from a phone. Reference TV: Xiaomi Mi TV 4A Pro (MiTV-AXSO0, Android TV 9, ~1 GB RAM, 32-bit, 720p UI); target: any Android TV / Google TV, API 28+ (docs/PHASE3.md). Read `README.md` first, then `docs/SPEC.md` (measured hardware, decisions) and `docs/PROTOCOL.md`.

- **Layout:** one Gradle build. `adb/` plain Java ADB client used by the phone for setup/updates (Phase 3), `protocol/` plain Java shared by both apps (ports, paths, TLS, pairing maths, pinning), `tv-launcher/` TV app (Java/Views, home screen + guard + control server, package `com.ajit.tvhero`), `companion/` phone app (Kotlin + Compose, `com.ajit.tvhero.remote`), `tools/` ADB-over-Wi-Fi scripts and a Python API client, `debloat/` what's switched off on the TV, `docs/` (start with `ARCHITECTURE.md` and `SECURITY.md`).
- **Build:** `./gradlew :protocol:test :adb:test :tv-launcher:assembleRelease :tv-launcher:testReleaseUnitTest :companion:assembleRelease :companion:testReleaseUnitTest :companion:lintRelease`. JDK 17+, SDK 34. Maven Central sometimes answers 429: wait and retry.
- **TV rules:** keep it light (no Compose on the TV, no blur/video/polling); D-pad navigation must stay deterministic; the guard reads only window changes, never screen content; every TV change is reversible (disable, never uninstall system apps) and verified with a screenshot from the TV (`tools/tv.py --screenshot`).
- **Protocol changes** go in `protocol/`, `docs/PROTOCOL.md`, `tv-launcher/.../Api.java`, `companion/.../TvClient.kt`, `tools/tvhero_client.py` together; vectors in `protocol/.../PairingTest.java`, `companion/.../PairingSessionTest.kt` and `tvhero_client.py --self-test` must agree.
- **Security is a feature:** every control path shows who is using it and can be revoked; internet control stays off by default and needs Allow on the TV; nothing loosens a rule without the parent password; no debug bridge left open. See `docs/SECURITY.md`.
- **Install on the TV:** phone app → Set Up a New TV (or Settings → About → TV App to update), or from a computer `TV_HOST=<ip> tools/install-tv.sh`. After an update the guard may need one reboot to bind (the script handles it).
- **Never commit** `keystore.properties`, `*.jks`, `tools/adbkey/`, `baseline/` (device identifiers). The parent password and pairing secrets never appear in logs.
- **Priorities:** bug fixes before features; Apple-HIG-style clean UI on both apps; update README and docs with every change.

- **Public repo:** never commit home addresses, Tailscale addresses, passwords or device identifiers; `tools/check_public.py` guards the website and release notes. Releases: `docs/RELEASING.md`.
