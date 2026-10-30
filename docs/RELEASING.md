# Releasing

The code is private (`ajitpal/tv-hero`). Releases and the website are public in **`ajitpal/tv-hero-releases`**, because a free GitHub account can't serve Pages from a private repo, and apps can't download a private repo's releases without a token built into them.

## What happens on its own

| When | Workflow | Does |
|---|---|---|
| Every push to `main`, every pull request | `android.yml` | Version and changelog checks, a dry build of the public website (it fails if anything private would be published), the Python protocol self-test, then both apps built with unit tests and lint. |
| A tag `vX.Y.Z` is pushed | `release.yml` | Builds and tests with the **release key** (from secrets) and verifies the signing certificate. Publishes `tv-hero-tv-X.Y.Z.apk`, `tv-hero-phone-X.Y.Z.apk`, `update.json` and notes (from `CHANGELOG.md`) to the public repo, then publishes the website. |
| `site/`, `CHANGELOG.md` or the screenshots change on `main` | `site.yml` | Republishes the website between releases. |

The phone app's **Software Update** reads `https://github.com/ajitpal/tv-hero-releases/releases/latest/download/update.json`. It updates itself, and installs the TV app over the home Wi-Fi after checking each file's SHA-256, package, version and the pinned release certificate (`309705ca…9124`).

## One-time setup

1. **Create the public repo** `ajitpal/tv-hero-releases` on github.com: public, with a README.
2. **Turn on its website:** in that repo, Settings → Pages → Source: *Deploy from a branch* → `gh-pages` / root. The branch appears after the first release. The site will be at `https://ajitpal.github.io/tv-hero-releases/`.
3. **Make a token** for publishing:
   - github.com → Settings → Developer settings → Fine-grained tokens → Generate.
   - Repository access: *Only* `ajitpal/tv-hero-releases`.
   - Permissions: **Contents: Read and write**.
4. **Add five secrets** in `ajitpal/tv-hero` → Settings → Secrets and variables → Actions:
   - `RELEASES_TOKEN`: the token from step 3
   - `KEYSTORE_BASE64`: the release keystore as base64. In PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\Projects\app\TV-app\keys\tv-hero-release.jks")) | Set-Clipboard`
   - `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`: the values in `C:\Projects\app\TV-app\keys\keystore.properties`

The key never goes into the repo. CI writes it to the runner's temporary folder and deletes it at the end.

## Making a release

1. Set the same `versionName` (and a higher `versionCode`) in `tv-launcher/build.gradle` and `companion/build.gradle`.
2. Add a `## X.Y.Z` section at the top of `CHANGELOG.md`.
3. Push the commit, then tag it:

```
git tag v2.7.0
git push origin main --tags
```

Watch it under Actions. When it's green, the phone app finds the update within a day, or at once with **Check Now**.

## If something fails

| Message | Fix |
|---|---|
| "Signing secrets are missing" | Add the four keystore secrets (step 4). |
| "is not signed with the tv-hero release key" | The keystore secret is a different key. Updates would be refused by every phone, so nothing was published. |
| "RELEASES_TOKEN is missing" or 403 when publishing | Add or renew the token (step 3); it needs Contents: Read and write on `tv-hero-releases`. |
| "tag … doesn't match version" | The tag and both `versionName`s must be the same. |
| Check-public failed | The website or notes contained a network address or password-like text. Remove it from `site/` or `CHANGELOG.md`. |
