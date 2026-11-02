# SmartTube Mobile — Windows build delivery

Built on November 1, 2026 from SmarterTube commit `3b780e9a8ac62ba59c90d090fa46d87210c45525` with the recovered v3 development kit. This is one project and one app; the debug APK is the requested development build of the actual patched source.

## Installable files

- `dist/SmartTubeMobile-debug-arm64-v8a.apk`: 64-bit ARM phones.
- `dist/SmartTubeMobile-debug-armeabi-v7a.apk`: 32-bit ARM devices.
- `dist/SmartTubeMobile-debug-universal.apk`: combined architecture package.
- `dist/SmartTubeMobile-debug-x86.apk`: x86 devices.

Application ID: `dev.smarttubemobile.unofficial`. Launcher label: `SmartTube Mobile Dev`. Version: `v0.1.0-alpha.3+st32.68`. Minimum SDK: 17; target SDK: 33; compile SDK: 34. The requested debug build uses the PC's Android debug certificate, independently of upstream release signing. It is not a release-signed production distribution.

## Verification

- All 59 app JVM tests passed, with no failures or skips.
- All 16 shared-code mobile JVM tests passed, with no failures or skips.
- All 16 kit tests passed.
- The Android device-test APK compiled; it was not executed.
- All four app APKs passed archive-integrity, package-ID and Android signature checks (v1 and v2 signatures).
- The Windows rebuild script completed successfully. The supplementary compatibility patch also passed a clean pinned-source application and idempotence check.

No emulator was used. No physical Android device was connected, so installation, playback, sign-in, SponsorBlock, PiP, background media behavior and Android 16 compatibility have not been verified on a device. The persistent in-app mini player remains unfinished. Existing runtime implementations are retained; this delivery does not certify full feature parity or a comprehensive security audit.

## Source and rebuilding

`SmarterTube/` contains the actual modified app, shared engine and checked-out submodule sources. `SmartTube-Mobile-Project.zip` is a full source snapshot with licenses, patches, build kit, rebuild script and verification reports. It omits Git history, generated build directories, caches, SDK files, signing keys and APKs; APKs are delivered separately in `dist/`.

Run `./Build-Android.ps1` in PowerShell from the extracted project. It uses the existing JDK 17 and Android SDK; optional `-JdkPath` and `-SdkPath` parameters support other installation locations. It builds the app, runs the targeted local JVM tests, compiles device tests and verifies APK identities and signatures. It never launches an emulator or installs the app. No account credentials or upstream signing keys are needed to build.

The app's code and resources include the five-tab navigation, independent branding, disabled inherited update prompts and telemetry endpoint from the earlier kit. The PC fixes repair the layout patch anchor, Windows tool discovery and path handling, remove automatic Gradle cache deletion, and update libraries used to run mobile tests. Robolectric and AndroidX Test are testing tools and are not shipped as app runtime libraries.

## Evidence and remaining scope

`reports/BUILD_PROVENANCE.txt` records the source, submodules, toolchain and original kit hash. `reports/build-windows.log` is the successful final app/JVM/device-test compilation log. `reports/kit-tests.log`, copied JUnit XML files and individual APK inspection reports contain the results. `reports/TRACKED_CHANGES.patch` covers tracked-file edits; the complete source snapshot also includes the new resources and test files. `dist/SHA256SUMS.txt` records the delivered APK hashes.

Earlier failed SDK installation and exploratory checks are preserved in separate logs. An initial attempt used the upstream TV shared-test variant and exposed legacy Robolectric/JDK incompatibility and a mixed-flavor Google Services configuration issue; the final checks correctly use the mobile shared-code variant. The TV build configuration was not modernized. An incomplete SDK installation was repaired and preserved locally under `reports/incomplete-android-34`; that backup is excluded from the source ZIP.

The upstream development-environment compromise notice was reviewed at https://smarttubeapp.github.io/. Source/submodule pins and build configuration were inspected, and APK native-library inventories are saved in `reports/APK_CONTENTS.json`. This is a limited supply-chain review, not a malware-free guarantee. Existing vendored native libraries remain part of the inherited source. No upstream prebuilt APK or release key was reused.
