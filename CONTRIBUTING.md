# Contributing to tv-hero

Thanks for helping make family TV calmer.

## Ways to help

- **Try it on your TV** and open a *TV report* issue: brand, model, Android version, what worked and what didn't. Reports from new TVs are the most useful thing right now.
- **Earn Time questions:** more questions, or new subjects and languages. They're plain lines in `tv-launcher/src/main/java/com/ajit/tvhero/Quiz.java`, checked by `QuizTest`.
- **Translations** of the TV app (`tv-launcher/src/main/res/values-*`).
- **Code:** bug fixes first, then features from [docs/PHASE3.md](docs/PHASE3.md).

## Ground rules

- **The TV is small.** The TV app is Java and Views, with no Compose, blur, video or polling; it must run on 1 GB of RAM. D-pad navigation must stay predictable.
- **Security is a feature.** Nothing may loosen a rule without the parent password, every control path is visible and revocable, and debugging is never left open. Read [docs/SECURITY.md](docs/SECURITY.md) first.
- **Privacy:** no analytics, no trackers, no new network destinations without discussion.
- **Protocol changes** go together in `protocol/`, `docs/PROTOCOL.md`, the TV's `Api.java`, the phone's `TvClient.kt` and `tools/tvhero_client.py`, with test vectors agreeing.
- **Docs and UI text:** plain English, Title Case on buttons, kind to kids ([brand/BRAND.md](brand/BRAND.md)). Update the README, the docs and `CHANGELOG.md` with the change.

## Before a pull request

```bash
./gradlew :protocol:test :adb:test :tv-launcher:testReleaseUnitTest :tv-launcher:lintRelease \
  :companion:testReleaseUnitTest :companion:lintRelease
python3 tools/check_release.py && python3 tools/tvhero_client.py --self-test
```

If you changed a screen, re-record the screenshots (`./gradlew :companion:testDebugUnitTest :tv-launcher:testDebugUnitTest -Pscreenshots=record`); new screens go in `tools/gallery.py` with a title, description and tags, then `python3 tools/readme_gallery.py` updates the README. Sample data only: never a real family's names, addresses or devices. CI runs the same checks on every pull request.

By contributing, you agree that your work is released under the MIT licence.
