# SmartTube Mobile — source build kit (PC build verified)

**Current status (2026-11-01):** The patched app compiled successfully on the user's Windows PC. All 75 targeted Android JVM tests and 16 kit tests passed. APK package identity, archive integrity and signatures were verified. No emulator was used; real-device behavior remains untested. See `../DELIVERY.md` for the current deliverables and `../reports/build-windows.log` for the successful build. The earlier milestone notes below describe the original kit.

The complete PC project includes `../Build-Android.ps1`, the patched `../SmarterTube` source and its submodules, plus the actual APKs in `../dist`. Run that PowerShell script to rebuild on this PC without launching an emulator. Test-runner dependency fixes are included in `scripts/apply-build-compatibility.py`; they do not add libraries to the app's runtime.

This development kit takes the real [SmarterTube](https://github.com/CodeSculptor/SmarterTube) Android phone/tablet fork of [SmartTube](https://github.com/yuliskov/SmartTube), pins it to a reviewed revision and applies targeted phone-only code changes. The mature SmartTube playback, content, accounts, subtitles, SponsorBlock, and other core implementations remain in the source checkout.

## Pinned base

- Source: `https://github.com/CodeSculptor/SmarterTube`
- Release: `v0.9.10-beta.1+st32.68` (October 26, 2026)
- Commit: `3b780e9a8ac62ba59c90d090fa46d87210c45525`
- Android flavor: `stmobile`
- Toolchain: JDK 17, Android SDK, NDK `21.0.6113669`, Python 3, Git and Gradle wrapper
- Expected modified package: `dev.smarttubemobile.unofficial`
- Display name: **SmartTube Mobile Dev** (independent, unofficial)

## Implemented source changes (not yet compiled)

**1. Phone navigation (`scripts/apply-bottom-navigation.py`):** adds a five-destination bottom bar to `MobileBrowseFragment`: Home, Shorts, Subscriptions, Library (History, Playlists, My videos menu) and Search. The original drawer remains. Browsing routes to existing `BrowsePresenter`/`SectionAdapter`, not a parallel client. Touch targets have accessible labels and selected styling.

**2. Identity and privacy isolation (`scripts/apply-development-identity.py`):** changes the mobile product flavor's application ID and version, launcher name/icon and About disclosure, while leaving upstream TV flavors and source unaffected. Disables inherited SmarterTube telemetry endpoint and automatic update prompts, and hides the update check that otherwise could offer an APK signed under a different package. Original SmartTube and SmarterTube links and attribution remain on the About screen.

**3. Android instrumentation test (`scripts/install-mobile-smoke-test.py`):** adds a mobile-flavor-specific Android test that inflates the actual browse XML, checks five visible destinations, clickable/focusable targets and accessibility descriptions. The CI workflow attempts two emulator smoke tests: Android 11/API 30 x86 (matching the existing x86 APK) and Android 16/API 36 x86_64 (architecture compatibility must be verified). Actual success requires a connected runner.

**4. Reproducible build checks:** dry-run patch assertions, preservation of unrelated source, patch idempotence, a complete `--binary` Git diff including newly generated files, Gradle JVM tests, APK ZIP/hash/package/signature checks and separate Android 11 (x86) and Android 16 (x86_64) emulator smoke tests. The latter may be blocked by upstream ABI packaging; do not call an ABI error a UI-test failure. The CI artifact records success/failure for each step.

No standalone mini player has been claimed or implemented. Existing PiP and background play are not substitutes for it.

## Build on GitHub Actions

1. Create your own GitHub repository and copy **all** files from this ZIP into its root, **including `.github/`**. Do not upload signing passwords.
2. Open **Actions → Build SmartTube Mobile modified debug APK → Run workflow**.
3. The workflow fetches the exact upstream commit/submodules, checks it, runs patch tests, adds source changes, builds `:smarttubetv:assembleStmobileDebug`, runs upstream JVM tests and attempts instrumented Android 11 x86 and Android 16 x86_64 testing.
4. When the build succeeds, retrieve the APK from the run's `smarttube-mobile-debug-source-patched` artifact; the build also produces `MOBILE_UI.patch`, reports, provenance, SHA-256 hashes and a test report.
5. Verify the APK's `package` is `dev.smarttubemobile.unofficial`, verify the debug signing certificate, and test installation and playback on a real phone before relying on it.

The GitHub workflow will not execute automatically until uploaded to an account and explicitly dispatched; merely downloading this ZIP does not compile anything.

### Local build

```bash
bash scripts/build-smartertube.sh
```

Optional options: `--audit-only`, `--skip-mobile-ui` (builds unmodified fork with its original ID), `--skip-tests`. Requires an internet-connected computer with Java 17 and Android SDK/NDK. Builds must start from a clean checkout. The modified build writes APKs to `smarttube-mobile-work/SmarterTube/smarttubetv/build/outputs/apk/stmobile/` and reports to `smarttube-mobile-work/reports/`.

## Offline verification

```bash
python3 -m unittest discover -s tests -v
bash -n scripts/build-smartertube.sh
python3 -m py_compile scripts/*.py
```

**Last measured:** 15/15 offline unit tests passed (2026-11-01); raw results are bundled in `reports/OFFLINE_TEST_RESULTS.log`. Android compilation, APK installation and runtime features **not tested**.

## Security, upstream and outstanding work

- Builds must use independent debug keys. Do not reuse upstream signing credentials or sideload arbitrary third-party APK repacks. The original project reported potentially compromised past build keys. Review its current advisory before installation or distribution.
- The downstream app ID and branding are experimental, not an endorsement or official release. Some engine internals may depend on the package identity; source compilation and behavioral testing must confirm compatibility.
- Features needing implementation or verification: persistent in-app mini player, Android 16 media lifecycle/background audio, all account functions, accessibility, gesture navigation and tablet layouts, and comprehensive feature parity testing.
- See `DEVELOPMENT_PROGRESS.md`, `docs/FEATURE_PARITY.md`, `docs/TESTING.md` and `docs/UPSTREAM.md`.

See `SOURCE_ATTRIBUTION.md` for upstream credit and license references.
