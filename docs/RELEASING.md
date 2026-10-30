# Releasing tv-hero

Everything lives in one public repo, **`ajitpal/tv-hero`**: the code, the signed releases, `update.json` and the website at **https://ajitpal.github.io/tv-hero/**.

## How a release works

1. Bump `versionCode`/`versionName` in `tv-launcher/build.gradle` and `companion/build.gradle`, and add a `## X.Y.Z` section to `CHANGELOG.md`.
2. `python3 tools/check_release.py vX.Y.Z` checks the versions and changelog agree.
3. Commit, then `git tag vX.Y.Z && git push origin main vX.Y.Z`.
4. **Release** (`.github/workflows/release.yml`) builds and tests both apps, signs them with the release key from the repo secrets, checks the signing certificate (`309705ca…9124`), and publishes a GitHub release with:
   - `tv-hero-phone-X.Y.Z.apk`, `tv-hero-tv-X.Y.Z.apk`
   - `update.json` (versions, sizes, SHA-256)
   - release notes from the changelog
5. The same run rebuilds and deploys the website.

The phone app's **Software Update** reads `https://github.com/ajitpal/tv-hero/releases/latest/download/update.json` (falling back to the old `tv-hero-releases` repo for phones on 2.7.0). It updates itself, and installs the TV app over the home Wi-Fi, after checking each file's size, SHA-256, package, version and the pinned release certificate.

## One-time setup

1. **Secrets** (Settings → Secrets and variables → Actions):
   - `KEYSTORE_BASE64`: `base64 -w0 tv-hero-release.jks`
   - `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
   - optional `RELEASES_TOKEN`: a fine-grained token with *Contents: Read and write* on `ajitpal/tv-hero-releases`. With it, each release is also mirrored there, so phones still on 2.7.0 see the update. Remove it once every phone runs 2.8.0 or later.
2. **Website:** Settings → Pages → Source: **GitHub Actions**.
3. **Private vulnerability reporting:** Settings → Code security → turn on *Private vulnerability reporting*.

Keep the keystore and its passwords out of the repo, and back them up: losing the key means every family must reinstall by hand.

## Earlier versions

**Actions → Publish earlier versions → Run workflow** (`backfill.yml`) builds each old tag (v2.0.0 … v2.7.0) as it was, signs it with the same key and publishes it as a release that is *not* marked latest, with that version's changelog section. Tags that already have a release are skipped, so it is safe to run again.

## Website only

Changes under `site/`, `brand/` or the screenshots redeploy the website on push to `main` (`site.yml`), keeping the current `update.json`.

## When something fails

| Problem | Fix |
|---|---|
| "versions don't match" | Run `tools/check_release.py vX.Y.Z` and fix the build files or changelog. |
| "signing certificate isn't tv-hero's" | The keystore secret is wrong. Re-encode the real `.jks`. |
| Pages deploy refused | Settings → Pages → Source must be **GitHub Actions**. |
| Mirror step 403 | Renew `RELEASES_TOKEN`, or delete it to stop mirroring. |
| `check_public.py` fails | A home or Tailscale address or other private detail reached the site or notes. Remove it. |
