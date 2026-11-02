# SmartTube Mobile

A native YouTube client for Android phones and tablets, built on [SmarterTube](https://github.com/CodeSculptor/SmarterTube) and the [SmartTube](https://github.com/yuliskov/SmartTube) playback engine.

This is an independent, unofficial project. It is not affiliated with YouTube, Google, or the upstream projects.

## Features

- Touch-friendly browsing, search, channel pages, and bottom navigation.
- Portrait and landscape playback with multiple player styles.
- Video quality controls, subtitles, playback speed, and seek previews.
- Ad-free playback with SponsorBlock, Return YouTube Dislike, and DeArrow integration.
- Account sign-in, subscriptions, history, and playlists.
- Background audio, media notifications, and picture-in-picture.
- Optional Shorts playback and subscription upload notifications.

Feature availability can depend on upstream services and Android version.

## Requirements

- **Device:** Android 8 (API 26) or newer, with a touchscreen.
- **Development:** JDK 17 and an Android SDK installation containing:
  - Android SDK Platform 36
  - Android SDK Build Tools 35.0.0
  - Android NDK 28.2.13676358

The repository includes a Gradle wrapper; a separate Gradle installation is unnecessary. Building requires internet access to download dependencies.

## Installation

Download an APK from this repository's **Releases** page, when available, and open it on your Android device. Allow installation from the app used to open the APK if Android prompts you.

Choose the universal APK if you are unsure which architecture your device uses. Updates require a compatible version code and the same signing certificate as the installed app.

## Build from source

Clone this repository and open the `SmarterTube/` directory in Android Studio, or build from a terminal. The mobile product flavor is `stmobile`.

Configure your JDK 17 installation through `JAVA_HOME`. Set `ANDROID_HOME` to your Android SDK directory, or specify the SDK in `SmarterTube/local.properties`:

```properties
sdk.dir=C:/Users/YOUR_USERNAME/AppData/Local/Android/Sdk
```

Use your actual SDK path. Shared source dependencies are located in `SmarterTube/SharedModules/`, `SmarterTube/MediaServiceCore/`, and `SmarterTube/exoplayer-amzn-2.10.6/`; preserve these directories.

### Windows (PowerShell)

From the repository root:

```powershell
cd SmarterTube
.\gradlew.bat :smarttubetv:assembleStmobileDebug
```

### Linux / macOS

From the repository root:

```bash
cd SmarterTube
bash ./gradlew :smarttubetv:assembleStmobileDebug
```

Generated APKs are written to:

```text
SmarterTube/smarttubetv/build/outputs/apk/stmobile/debug/
```

To build and run the local JVM and instrumentation APK compilation checks on Windows, use the repository script from PowerShell:

```powershell
.\Build-Android.ps1
```

The script runs the build-kit unit tests, assembles the mobile debug APKs, runs the mobile and shared JVM tests, compiles the instrumentation test APK, and checks APK package identity and signatures. Instrumentation tests require a connected device or emulator to execute; this script only compiles them.

The application ID is `dev.smarttubemobile.unofficial`. Debug builds use Android's debug signing key unless a local `keystore.properties` configures another key.

## Project structure

```text
SmarterTube/                       Android application and shared dependencies
  smarttubetv/src/stmobile/        Mobile interface and resources
  smarttubetv/src/main/            Shared application implementation
  common/                         Shared models, presenters, and playback logic
SmartTube-Mobile-Build-Kit/        Source patching tools and build documentation
.github/workflows/                GitHub Actions workflows
reports/                          Historical build and verification reports
Build-Android.ps1                 Original pinned-source Windows build workflow
```

The repository contains the patched source tree and the scripts used to create the mobile changes from the pinned upstream checkout. Build the checked-out sources directly; do not reapply those overlay scripts here.

## Development

Keep mobile-specific changes in the `stmobile` source set where practical. Include reproduction steps, Android version, and relevant logs when reporting a bug. For pull requests, describe the behavior changed and any validation performed.

The release workflow in [`.github/workflows/release.yml`](.github/workflows/release.yml) builds signed release APKs and publishes a GitHub Release when manually dispatched or triggered by a matching version tag. Local development builds do not require running this workflow.

Historical verification results are documented in [DELIVERY.md](DELIVERY.md). Those results describe an earlier build and do not establish validation of subsequent changes.

## Credits and license

- [SmarterTube](https://github.com/CodeSculptor/SmarterTube) provides the Android phone and tablet interface.
- [SmartTube](https://github.com/yuliskov/SmartTube), by yuliskov and contributors, provides the underlying client and playback engine.

The upstream application is licensed under the [MIT License](SmarterTube/LICENSE). Third-party components retain their respective licenses. See [source attribution](SmartTube-Mobile-Build-Kit/SOURCE_ATTRIBUTION.md) for additional details.
