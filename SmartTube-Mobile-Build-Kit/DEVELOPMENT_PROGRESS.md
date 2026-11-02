# SmartTube Mobile — development progress

## PC build completed — 2026-11-01

The pinned source and recursive submodules were cloned on the user's PC. The original ZIP and all 27 listed payload hashes matched. The navigation layout seam and Windows path test were repaired, and APK inspection now finds Windows Android SDK tools. The inherited Windows wrapper no longer deletes its local Gradle cache.

JDK 17, Gradle 7.5, Android platform 34 and build-tools 30.0.3 successfully built all four debug APK variants. The Windows rebuild script passed end to end. All 59 app JVM tests, 16 shared-code mobile JVM tests and 16 kit tests passed. Android device tests compile successfully. Test-only tooling was updated for JDK 17 and modern Android manifest requirements, with no new runtime dependencies.

APK integrity, application ID `dev.smarttubemobile.unofficial`, label `SmartTube Mobile Dev` and independent Android debug signatures were verified. No upstream signing key was imported. No emulator was used, and the kit workflow now compiles device tests without running an emulator. Real-device playback, authentication and Android 16 behavior remain unverified. The persistent in-app mini player remains unfinished.

Current evidence: `../reports/build-windows.log`, `../reports/kit-tests.log`, APK inspection reports and `../DELIVERY.md`. The earlier milestone report below is retained as history; its old PC/network/build blockers have been resolved.

## Historical milestone 3 report

Updated: **2026-11-01** · Milestone **3 — independent identity, accessible navigation and instrumented smoke-test implementation**.

## Completed (source and tooling)

- Selected SmarterTube rather than rewriting the existing SmartTube engine; inspected pinned real `stmobile` browse fragment, layout, `SectionAdapter`, `BrowsePresenter`, Gradle mobile product flavor, mobile manifest, updater and telemetry source via public GitHub.
- Retained milestone 2 bottom navigation: Home, Shorts, Subscriptions, Library popup and Search wired to existing presenter and mobile search entry point. Added parent accessibility nodes and suppressed duplicate child announcements.
- Added `scripts/apply-development-identity.py`: mobile-only independent application ID (`dev.smarttubemobile.unofficial`), version, launch name, About attribution and launcher icon; stops the SmarterTube update notifications and hides its incompatible update action; blanks the fork-specific opt-in telemetry endpoint.
- Added independent density-specific PNG launchers under `overlays/dev-icons/` and a deterministic generation script (Python + Pillow needed only to regenerate the PNG files, not to build the app).
- Added mobile-flavor-specific Android resource smoke test via `scripts/install-mobile-smoke-test.py` to validate five navigation tabs and accessibility targets without requiring a live YouTube API response.
- Extended GitHub Actions: independently branded build, explicit APK package verification, separate JVM tests, Android 11 API 30 x86 and Android 16 API 36 x86_64 emulator instrumented smoke tests, artifact logging and binary-inclusive patch export.
- Expanded offline tests from 7 to **15**. **15/15 passed locally** (raw log: `reports/OFFLINE_TEST_RESULTS.log`). Bash syntax and Python byte-compile succeeded. Workflow parsed successfully as YAML.

## Important testing distinction

| Check | Status |
| --- | --- |
| Public pinned source API/anchor inspection | Completed (web source) |
| Offline source patch and kit tests | **15/15 passed** |
| Launcher PNG files generated | Completed |
| Shell/Python/YAML static checks | Passed |
| Full Git checkout and submodules in container | **Blocked: DNS cannot resolve github.com** |
| JDK 17 / Android SDK | **Not available (local JDK 21 only)** |
| Android Gradle compilation | **Not run** |
| APK | **Not built** |
| Android 11 x86 / Android 16 x86_64 emulator smoke | **Written, not run** |
| Real device test | **Not run** |

## Known risks and limitations

1. The previous bottom navigation and this update have not been compiled against the full source checkout. Integration is protected by exact anchors and a fail-closed patch, not guaranteed build success.
2. GitHub Actions must be dispatched in a connected project. CI output is required before claiming build compatibility.
3. The mobile build still inherits SmartTube's application engine, including older TV-driven dependencies and compatibility behavior. Independent Android 16 lifecycle, media notification and security testing is essential.
4. Custom package identity may expose hardcoded authorities, links or application-ID assumptions in older shared components; inspect the merged manifest and test sign-in/playback.
5. The persistent **in-app mini player is not implemented**. In-app mini player must reuse the actual playback session; PiP/background playback alone do not satisfy the requirement.
6. Upstream fork's reported capabilities (home/search/player/SponsorBlock/sign-in/Shorts) are not separately verified by this development kit.
7. Android 16 emulator images are typically x86_64 while the current fork declares x86, ARMv7 and ARM64 APKs. Added an Android 11 x86 smoke test as a supported ABI baseline; Android 16 runs separately as a compatibility probe. Examine emulator ABI reports before attributing installation failure to the UI.
8. The workflow uses third-party GitHub Actions, which require version/SHA and supply-chain review before production use.

## Next work on a connected Android build runner

1. Trigger `.github/workflows/build-smarttube-mobile.yml`, capture Gradle compile errors or successful APK/artifact checks; correct failed anchors, resources or manifest merges.
2. Run the Android 16 emulator instrumentation smoke test and exercise all five bottom controls on-device. Verify the nav drawer, login, history, Shorts and keyboard.
3. Inspect merged manifest `applicationId`, signature, app label, app icon, providers and any app-ID-dependent intent authorities; check that inherited telemetry and update flows are dormant.
4. Inspect `MobilePlaybackActivity` + playback service and add a persistent mini-player host that reuses existing player/service state, then verify pause/resume, back, rotation, PiP and background transitions.
5. Test Android 13–16 notifications/media controls, PiP and audio focus; complete feature parity and performance profiling.
6. Document only measured results; package a verified debug APK and complete source/patch + build instructions.

## Reproducible commands

```bash
python3 -m unittest discover -s tests -v
bash scripts/build-smartertube.sh
# After build on a connected machine:
find smarttube-mobile-work/SmarterTube/smarttubetv/build/outputs/apk/stmobile -name '*.apk'
```

**Continuity:** Continue from the pinned checkout and existing files. Do not restart as a generic YouTube frontend.
