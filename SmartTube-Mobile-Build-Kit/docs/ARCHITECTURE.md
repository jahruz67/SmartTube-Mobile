# Architecture and implementation boundary

## Foundation

Chosen foundation: CodeSculptor/SmarterTube, itself a fork of yuliskov/SmartTube. Public repository docs state that the shared TV source is upstream-synchronizable and that mobile-only components live under `smarttubetv/src/stmobile/`. The mobile build flavor is `stmobile`, with application ID `com.codesculptor.smartertube`, separate from upstream's TV builds. The mobile fork depends on SharedModules and a mobile-specific CodeSculptor/MediaServiceCore submodule.

## Observed modules and design

- `smarttubetv/src/main/`: SmartTube's established application/UI and shared integration (per fork policy).
- `smarttubetv/src/stmobile/`: smartphone-specific source and resources (per fork policy).
- `SharedModules`: pinned upstream shared support code.
- `MediaServiceCore`: pinned CodeSculptor fork of the media/service engine.
- `common`, `chatkit`, and other root modules: retained supporting modules.
- Mobile UI currently uses AppCompat, Material TabLayout and ViewPager2, Chrome Custom Tabs for device-code login, and WorkManager for subscription-upload notifications. It does not need a ground-up Compose rewrite.

## Next engineering work

1. Validate a clean stmobile debug build from the pinned release and sign only with a local debug key.
2. Inventory mobile screens and inspect view/presenter contracts before patching.
3. Evaluate a proper app-wide bottom navigation vs the current drawer. Keep account, notifications, and settings accessible.
4. Investigate whether an **in-app** mini player exists; do not conflate Android PiP with a persistent mini player inside the application's navigation layout.
5. Verify background media session actions, Android 16 system insets, notification permissions, and audio focus.
6. Consider a distinct application ID and product identity for an independent downstream fork, with thorough audit of intent authorities and login integration before renaming.
7. Continue upstream sync using the existing mobile source-set boundary.

Public reference: https://github.com/CodeSculptor/SmarterTube/blob/master/smarttubetv/build.gradle

## Milestone 2: mobile bottom-navigation source extension

The guarded `scripts/apply-bottom-navigation.py` transformation changes only `smarttubetv/src/stmobile/` in the **actual fetched** SmarterTube source tree during the build. It adds five touch destinations to `mobile_browse_fragment.xml`, listeners and state synchronization in `MobileBrowseFragment.java`, and text resources in a new mobile-only values file. All content remains loaded via the original `BrowsePresenter`; the phone's drawer and all TV modules remain unmodified. An unsupported source revision fails safely instead of guessing at integration. This patch is authored, not Gradle- or device-verified.
