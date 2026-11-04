# SmartTube Mobile versioning

The maintained flavor is `stmobile`. Its versions are independent of SmartTube's TV
flavors. Defaults in `app/build.gradle` are `v1.0.0+st32.68` and versionCode `23744`;
`mobileVersionName` and `mobileVersionCode` override them.

## Installed version and APK names

The version-name parser accepts:

```text
v<major>.<minor>.<patch>[-<alpha|beta|rc>.<number>]+st<upstream-base>
```

The product version and channel determine ordering. `+st32.68` is upstream metadata;
it does not make one product version newer than another. Legacy
`<upstream>-mobile-<suffix>` identifiers remain supported by `SmarterTubeVersion`
and sort before the current format. Preserve that compatibility when editing it.

APK names replace `+` with `-`, for example:
`SmartTube-Mobile-v1.0.0-st32.68-universal.apk`. Build outputs include ARM64, ARMv7,
x86, and a universal APK containing ARM64 and ARMv7.

## Release workflow

`.github/workflows/release.yml` assigns versionCode `100000 + GITHUB_RUN_NUMBER`
and versionName `v1.0.<run-number>+st32.68`. Its default release tag is
`v1.0.<run-number>`; tag-triggered runs use the supplied tag. Those tags omit the
`+st` suffix required by the inherited updater parser. See
[updater compatibility](UPDATER_COMPATIBILITY.md) before enabling update discovery.

Preserve package `dev.smarttubemobile.unofficial`, the existing signing identity,
and a versionCode greater than the last shipped version for in-place upgrades.
An upstream integration must account for the base string in both the app and CI;
follow [UPSTREAM_SYNC.md](UPSTREAM_SYNC.md) before advancing that metadata.
