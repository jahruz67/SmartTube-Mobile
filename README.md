# SmartTube Mobile

An independent Android phone and tablet client built on [SmarterTube](https://github.com/CodeSculptor/SmarterTube) and [SmartTube](https://github.com/yuliskov/SmartTube). Features include touch navigation, search, channel browsing, accounts and playlists, video and background playback, subtitles, picture-in-picture, SponsorBlock, Return YouTube Dislike, and DeArrow. This project is not affiliated with YouTube, Google, or the upstream developers.

## Project structure

```text
app/              Android app: shared UI in src/main, phone UI in src/stmobile
common/           Playback, presenters, models, networking and persistence
third_party/      Required vendored media engine and Android library modules
gradle/           Wrapper and shared dependency/SDK versions
services/         Optional anonymous-statistics backend and its deployment instructions
tools/            APK verification and its Python unit tests
docs/             Version and updater compatibility contracts, inherited known issues
.github/          Release workflow
```

The source tree is self-contained. Vendored sources are ordinary tracked files; no Git submodule initialization or sibling checkout is required. Their original packages and licenses are retained. The modified Leanback widgets, media service, JavaScript engine, and ExoPlayer modules are required by the shared implementation even for the phone flavor. Upstream TV flavors remain available for compatibility; `stmobile` is the maintained application variant.

## Build prerequisites

- JDK **17**, selected through `JAVA_HOME`.
- Android SDK with **Platform 36**, **Build Tools 35.0.0**, and **NDK 28.2.13676358**.
- Internet access for the wrapper and declared Maven dependencies. Gradle **8.13**, Android Gradle Plugin **8.13.2**, and Kotlin **2.3.10** are pinned; use the included wrapper.
- Python 3 for `Build-Android.ps1` and APK inspection; a direct Gradle build does not require Python.

Set `ANDROID_HOME` to the SDK installation, or add `sdk.dir=<your SDK path>` to an untracked root `local.properties`. The build never selects source dependencies outside this checkout. Open the repository root in Android Studio.

### Windows / PowerShell

From the repository root:

```powershell
.\gradlew.bat --no-daemon --console=plain clean :app:assembleStmobileDebug
```

### Linux / macOS

```bash
bash ./gradlew --no-daemon --console=plain clean :app:assembleStmobileDebug
```

APKs are written to `app/build/outputs/apk/stmobile/debug/`:

```text
SmartTube-Mobile-v1.0.0-st32.68-universal.apk
SmartTube-Mobile-v1.0.0-st32.68-arm64-v8a.apk
SmartTube-Mobile-v1.0.0-st32.68-armeabi-v7a.apk
SmartTube-Mobile-v1.0.0-st32.68-x86.apk
```

The universal APK includes ARM64 and ARMv7; x86 is a separate APK. The application ID is `dev.smarttubemobile.unofficial`, version code **23744**, version name **v1.0.0+st32.68**, minimum Android **8 / API 26**, target/compile SDK **36**. `-PmobileVersionCode` and `-PmobileVersionName` retain the existing version overrides.

## Tests and APK verification

```powershell
.\gradlew.bat --no-daemon --console=plain :app:testStmobileDebugUnitTest :common:testStmobileDebugUnitTest :app:assembleStmobileDebugAndroidTest
python -m unittest discover -s tools/tests -v
python tools/inspect-apk.py app/build/outputs/apk/stmobile/debug/SmartTube-Mobile-v1.0.0-st32.68-universal.apk
```

For a clean build, JVM tests, test APK compilation, and signature/package checks together:

```powershell
.\Build-Android.ps1
```

The helper accepts `-JdkPath`, `-SdkPath`, and optional `-ReferenceApk`. Inspection with `--reference <baseline.apk>` requires the manifest, compiled resources, assets and native libraries to match the reference byte for byte. Keep `ANDROID_HOME` and `JAVA_HOME` set so inspection can find `aapt` and `apksigner`. Logs go to ignored `reports/`; generated files are not build inputs.

With a connected device or a booted compatible emulator, run the navigation resource/accessibility smoke test:

```powershell
.\gradlew.bat :app:connectedStmobileDebugAndroidTest
```

Use `bash ./gradlew` for the equivalent commands on Linux/macOS. Compilation and APK comparison cannot establish runtime parity: playback, authentication, service integrations, background audio and PiP also need device testing.

The shared utility suite and offline API retry regression tests can also run without account credentials:

```powershell
.\gradlew.bat :sharedutils:testDebugUnitTest :youtubeapi:testStstableDebugUnitTest --tests '*SearchCallRetryTest'
```

Other inherited API tests contact live services and require valid account fixtures; they are not part of the offline verification command.

## Signing and releases

Debug builds use the local Android debug certificate. Existing root `keystore.properties` support is retained, including its original override of debug signing. Release signing accepts either that private file (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) or the environment variables `MOBILE_KEYSTORE_PATH`, `MOBILE_KEYSTORE_PASSWORD`, `MOBILE_KEY_ALIAS`, and `MOBILE_KEY_PASSWORD`. Use the same private key as existing installations to preserve updates and data. Without private signing configuration, release APKs are unsigned.

```powershell
.\gradlew.bat :app:assembleStmobileRelease
```

Release output: `app/build/outputs/apk/stmobile/release/`. The release workflow requires GitHub secrets `MOBILE_KEYSTORE_BASE64` (base64 of the existing key), `MOBILE_KEYSTORE_PASSWORD`, `MOBILE_KEY_ALIAS`, and `MOBILE_KEY_PASSWORD`. Signing material is ignored and must be backed up privately. The previously tracked local key has been preserved, but old Git history still contains it; history and signing identity were not rewritten.

## Cleanup baseline

The original checkout is recoverable through Git branch `cleanup-baseline-20261103` at `7d8970b`. Its mobile debug APK built successfully. All 61 app JVM tests passed; one of 18 common tests (`unlockCallbackStillRunsAfterSuspend`) failed because its setup did not create the dim overlay needed to notify the registry. The cleaned test explicitly creates that overlay and executes the intended callback before checking its queued unlock. Production screensaver behavior is unchanged.

The cleanup moves the Android build to the repository root, groups dependencies under `third_party`, consolidates duplicate dependency sources and settings, and groups phone preference storage under `mobile.settings`. Inactive ExoPlayer extensions/demos, an unused Fragment fork, unapplied source overlays, duplicate standalone wrappers, historical reports and obsolete publishing assets were removed from the source tree. An abandoned sample test containing a hardcoded external proxy and an unbounded wait was removed; it tested its own proxy setup rather than application code. App resources, native libraries, component names, preference keys, versions, and supported ABIs are preserved.

Verification on November 3, 2026: a clean build from a tracked-source archive succeeded, with 61 app and 18 common JVM tests passing and the instrumentation APK compiled. Another 6 shared utility tests, 4 offline API retry tests, and 3 Python tooling tests passed. All four APK signatures and package IDs were verified. The universal APK matches all 1,452 baseline manifest/resource/asset/native entries byte for byte; all 26,395 defined classes are retained after accounting for preference package moves.

Remaining verification limits: Android Lint reports **20 existing errors and 535 warnings**, including back-navigation, TV manifest assumptions, custom views, opt-in annotations and tint usage. Every error location matches an unchanged baseline source line; these were not suppressed or repaired with behavioral changes. No device was connected. The available API 28 emulator requires unavailable hardware acceleration, and software mode never became usable, so instrumentation execution and runtime feature parity remain unverified.

## License and attribution

The application retains the upstream [MIT license](LICENSE). SharedModules and MediaServiceCore originate from yuliskov's projects; the vendored ExoPlayer fork derives from the Android Open Source Project/Amazon and retains its Apache license. ChatKit, modified Leanback, file picker, double-tap player, and sliding activity sources retain their respective license files under `third_party/`. Original source notices and test fixtures are retained.
