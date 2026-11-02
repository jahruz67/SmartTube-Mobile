# Upstream update guide

1. Review upstream SmartTube's current security status and fixes: https://github.com/yuliskov/SmartTube.
2. Read SmarterTube's own versioning, release and upstream merge guides before selecting a new baseline.
3. Open a maintenance branch from the pinned, known-good source. Record its full commit and submodule SHAs.
4. Compare mobile-specific `smarttubetv/src/stmobile/`, product flavor settings, and the CodeSculptor/MediaServiceCore pin against the incoming version.
5. Merge only after reviewing changed Gradle scripts, submodule URLs, dependencies and Android manifest component exports.
6. Build and test the debug APK, then use a real device and the feature parity checklist. Do not claim inherited features work merely because they compile.
7. Update the pinned SHA in both build script and GitHub Actions workflow, and update DEVELOPMENT_PROGRESS.md and FEATURE_PARITY.md.
8. Preserve upstream license notices and distinguish your fork's application identity from both SmartTube and SmarterTube when distributing an independent release.

**Do not automatically track master for installation builds.** The pinned Git revision makes it possible to reproduce and audit what was built.

Relevant upstream guide: https://github.com/CodeSculptor/SmarterTube/blob/master/docs/upstream-merge.md

## Milestone 3 downstream separation

The development variant patch edits only `smarttubetv/build.gradle` product flavor `stmobile` and files inside `smarttubetv/src/stmobile/` (plus a variant-specific instrumentation test under `src/androidTestStmobile`). It leaves upstream TV flavor and engine source intact. Run patch scripts on a clean pinned checkout; each validates the source anchors before writing. Newly added PNG icons and resource XML are captured in `MOBILE_UI.patch` using `git add -N` + `git diff --binary`.

Do not inherit SmarterTube release update checks or opt-in statistics for a separately identified development package. If the downstream project later publishes its own trusted updates, implement a correctly signed and package-matching updater instead of re-enabling the SmarterTube updater.
