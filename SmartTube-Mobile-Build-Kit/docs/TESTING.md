# Testing and security report

## Tests executed while creating this kit

- Shell script Bash parsing: checked (pass/fail recorded by creator).
- Workflow YAML parsing: checked where PyYAML is available (pass/fail recorded by creator).
- Source clone and submodule verification: attempted in the creator environment but GitHub DNS is unavailable.
- Gradle build: NOT RUN. Creator environment lacks Android SDK and JDK 17.
- Local source-patch tests: 15/15 PASSED on 2026-11-01. Upstream Gradle/JVM unit tests: NOT RUN.
- Emulator/device install: NOT RUN.
- Playback and user sign-in: NOT RUN.
- Dependency scan and APK scan: NOT RUN.

Do not interpret a future successful compilation as proof of feature parity.

## Build proof for your environment

Run the supplied script and retain `BUILD_PROVENANCE.txt`, `gradle-build.log`, `gradle-tests.log`, `UNIT_TEST_STATUS.txt`, and `APK_SHA256.txt`. Confirm the resulting APK is signed by a locally controlled debug key. Inspect dependency trees and source for malicious or unexpected build steps first.

## Minimum device verification

- Install debug APK with `adb install -r <path-to-apk>` (use a new package ID if you need a separate install alongside official SmarterTube).
- Verify launch, home, search, one VOD playback and seek, quality change, captions, playback speed, and rotation.
- Verify SponsorBlock automatic/manual skip on a known segment and confirm the settings change takes effect.
- Verify Shorts, swipe-to-next, rapid switching, and exit/return without leaking audio.
- Verify subscription and account switching with an authorized test account (do not log secrets).
- Verify background playback, media notification, lock screen controls, PiP entry/exit, and Bluetooth headset pause.
- Verify Android back gestures, soft keyboard, dark/light themes, narrow/wide screens, Android 16 edge-to-edge, and tablet landscape.
- Test offline/slow network, low memory, rotation, media interruption, screen lock, and repeated activity recreation.
- Check logcat for fatal exceptions and use memory profiler to confirm players are released.
- Mark every feature Verified only when independently exercised on a device.

## Security concerns

Upstream SmartTube published a developer-environment compromise advisory in December 2025. Review the currently trusted source revisions; never reuse historic upstream APKs or compromised signing keys. This kit pins the published SmarterTube release source and records submodule revisions but does **not** constitute a security audit. The public SmarterTube build.gradle can re-use a custom signing config for debug if `keystore.properties` is present: this kit forbids that file in its build flow. Do not grant GitHub Actions access to private signing keys.

Advisory: https://www.patreon.com/smarttube/posts/important-about-144979415

## Milestone 3 automated checks

The GitHub Actions build attempts two independent emulator tests: Android 11 (API 30, x86) for the fork's existing x86 ABI and Android 16 (API 36, x86_64) for modern device compatibility. The latter may be blocked by the upstream APK ABI splits: it is a separate compatibility probe, not proof of a UI defect. It installs the mobile-flavor instrumented resource test from `src/androidTestStmobile` and verifies the modified `mobile_browse_fragment` has all five clickable, focusable and accessible tabs. This is **not** a network/playback/sign-in test.

Both `emulator-x86-status.txt` and `emulator-test-status.txt` (plus device test XML when available) must be checked. They are distinct tests; neither replaces full device functional testing. The emulator step is `continue-on-error` so a failed emulator does not hide the compiled APK; any failure must still be investigated and must **not** be called passing.

The app package for patched builds must be `dev.smarttubemobile.unofficial`, version `v0.1.0-alpha.3+st32.68`, and the signing certificate must be independently generated (never inherited from SmarterTube release). The SmarterTube telemetry endpoint must be empty, and update checks must not offer another package/signature as an in-place upgrade.
