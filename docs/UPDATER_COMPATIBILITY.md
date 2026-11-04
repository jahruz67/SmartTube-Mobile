# SmartTube Mobile updater compatibility

## Current phone entry points

`MobileAboutActivity` hides the "Check for updates" button. `MobileBrowseActivity`
does not enqueue the inherited launch update notices. No automatic release source
is configured for this independent package through those phone entry points.

The inherited `MobileUpdateChecker` and `LaunchUpdateNotices` implementations remain
available, with tests. Their release URL resources still reference
`CodeSculptor/SmarterTube`; those releases must not be offered as upgrades for this
fork. Re-enabling these entry points requires configuring this project's releases
and reconciling tag formats, package identity, and signing compatibility first.

## Existing selection behavior

The implementation lives under
`app/src/stmobile/java/com/liskovsoft/smartyoutubetv2/mobile/update/`:

- `MobileUpdateChecker` accepts a GitHub releases-list API URL. It parses tags,
  filters by release channel, orders by product version, and reports
  `UP_TO_DATE`, `UPDATE_AVAILABLE`, `NO_COMPATIBLE_ASSET`, or `ERROR`.
- `SmarterTubeVersion` accepts the version-name format in
  [VERSIONING.md](VERSIONING.md), retains legacy mobile tags, and ignores
  upstream-only or unrecognized tags. The current workflow's plain `v1.0.*`
  release tags do not match its required `+st` format.
- `ApkAssetSelector` prefers an exact ABI match, then a universal APK, then no
  selection. ABI matching alone does not verify the package or signing identity.
- The checker returns URLs for the UI to open; it does not install an APK.

Shared TV update code under `common/` and
`third_party/SharedModules/appupdatechecker2/` is a separate path. Review it when
porting upstream changes; do not rely on historical TV/mobile versionCode gaps as
a permanent guarantee against offering incompatible packages.

Existing regression tests are under `app/src/testStmobile/.../mobile/update/`.
Their presence does not establish that network discovery, installation, or the
disabled phone entry points have been verified in this checkout.
