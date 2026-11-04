# Reflecting SmartTube releases in SmartTube Mobile

## Difficulty and current gaps

Routine isolated API fixes are usually moderate work: find the upstream behavior,
check for an equivalent local fix, and port the missing parts. Changes to playback,
authentication, shared interfaces, or the media engine are harder because callers,
dependency versions, and phone lifecycle behavior must agree. A clean textual merge
does not establish correctness. Runtime confidence still requires device checks.

This fork already separates most phone UI into `app/src/stmobile/`, but also changes
shared presenters and vendors its dependencies as ordinary files. Cleanup commit
`3b27cdd` moved the old `SmarterTube/smarttubetv/` tree to `app/` and dependencies to
`third_party/`. Upstream paths therefore need mapping before comparison. There is
only an `origin` remote in the inspected checkout; no exact upstream source baseline
has been established. `32.68` in `app/build.gradle` is declared version metadata,
not proof that every upstream fix or dependency at that release is present.

The local pre-cleanup fork is recoverable through branch `cleanup-baseline-20261103`
at `7d8970b`. That is a fork checkpoint, not an original upstream commit ID.

## Inputs and persistent state

[upstream-state.json](upstream-state.json) records source repositories, directory
mappings, and independent component commit IDs. Unknown IDs remain `null`.

- `source_baseline_commit`: a proven original source snapshot, with evidence in the
  decision log. Do not fabricate it from the `+st` version suffix.
- `reviewed_through_commit`: the last upstream snapshot whose changes were fully
  accounted for in the log. This is a review cursor, not a claim of complete adoption.
- `declared_smarttube_version`: existing version metadata, advanced only when the
  corresponding integration scope is justified. Partial ports belong in the log.

Resolve a requested release channel explicitly: use stable when the user says
"latest" without specifying beta. With authorized network access, verify the latest
published release on that channel and pin its full commit ID before editing. Without
network authorization, use a supplied/local tag and say that its recency is unverified.
Do not substitute this fork's `v1.0.*` tags for SmartTube release tags.

For the first comparison, recover the original commits from trustworthy import
records or compare a candidate historical release against inherited source. Record
how the baseline was established. If provenance remains uncertain, use an explicit
candidate `--base` for investigation and label it provisional; leave the source
baseline null and do not describe the result as a complete release sync.

## Agent procedure

1. Inspect the working tree and read the local safeguards and existing decisions
   below. Preserve user changes. Identify the base and target commits in an existing
   upstream checkout. Read release notes when supplied or network access is authorized.
2. Compare **old upstream, new upstream, and this fork**. Use the helper below to
   enumerate changed files and map renamed directories. Inspect the relevant hunks
   and callers; do not copy whole directories or blindly cherry-pick TV commits.
   New/unmapped paths, deletions, file modes, and build changes need explicit decisions.
3. If a dependency pointer changes, inspect its old/new commit IDs in the parent's
   release tree. Compare that dependency separately using those exact snapshots.
   If it was embedded as source instead, account for changes under its mapped path.
   Do not independently update every component to its newest head. Interface changes
   must be ported with their callers; preserve local Gradle wiring and trimmed modules.
4. For every upstream commit or coherent fix, record one decision: `ported`,
   `adapted`, `already-equivalent`, `not-applicable`, or `deferred`. Group files only
   when they implement the same fix. Cite upstream hashes, local paths, why, and
   verification status. "Already fixed" requires checking the actual behavior and
   edge cases, not matching commit messages or spotting a similar method name.
5. Preserve a local fix until upstream covers its original failure mode and phone
   behavior. When upstream supersedes it, consolidate to one implementation and
   explain the replacement. Do not stack two retries, duplicate listeners, or apply
   both workarounds without understanding their interaction.
6. Inspect the resulting diff statically. Run only checks the user explicitly
   authorized. Report remaining uncertainty. Advance each review cursor only after
   all changed paths have decisions, including justified exclusions and deferrals.
   Keep deferred items visible and reconsider them on subsequent updates. A cursor
   can advance with deferrals, but the update must be reported as partial.

## Local comparison helper

`tools/compare-upstream.py` needs Python 3.9+ and Git. It reads local commits and
the fork's current working-tree files, prints Markdown to stdout, and performs no
fetch, merge, file write, install, build, or test. Running it is a static comparison.
The upstream checkout must already exist; obtaining one requires network authorization.

Example only; replace angle-bracket placeholders before running:

```powershell
python tools/compare-upstream.py --component smarttube --checkout C:\src\SmartTube --base <old-ref> --target <release-ref>
python tools/compare-upstream.py --component mediaservicecore --checkout C:\src\MediaServiceCore --base <old-pin> --target <new-pin>
```

Components: `smarttube`, `mediaservicecore`, `sharedmodules`, `exoplayer`. The ExoPlayer
repository is unrecorded: establish its source before using that comparison.
Omit `--base` on later runs to use the recorded review cursor, falling back to the
source baseline. Supply `--target` explicitly; the helper never guesses "latest".
Output may be saved in ignored `reports/` when that directory already exists.

| Helper assessment | Next action |
| --- | --- |
| TARGET MATCH | Content equals the target snapshot, including an already-absent deletion; inspect callers and record why no port is needed. |
| BASE MATCH | Content equals the old snapshot, including an absent newly added file; inspect and port the relevant change. |
| OVERLAP | Local content differs from both; reconcile behavior manually. A fix implemented elsewhere also needs semantic review. |
| DEPENDENCY | Compare the reported pinned commits in the corresponding source repository. |
| UNMAPPED / SPECIAL / DIRECTORY / FILE MODE | Inspect manually; update a mapping only when its ownership is established. |

All labels are review hints. Raw-byte comparison can overreport overlaps for line
endings or Git filters. Renames are intentionally exposed as deletion/addition pairs.
The report covers changed upstream files, not all existing local divergences, and
cannot detect semantically equivalent fixes in different files. An earlier deferred
fix will not reappear in a later diff merely because its review cursor advanced.

## Local safeguards to inspect when areas overlap

This is a starting inventory from static inspection, not an exhaustive patch list or
a claim of runtime verification. Preserve each behavior or document its replacement.

| Area / evidence | Behavior to preserve |
| --- | --- |
| `third_party/MediaServiceCore/youtubeapi/src/main/java/com/liskovsoft/youtubeapi/search/v2/SearchService2.kt`; `SearchCallRetryTest.java` in that module's tests | Anonymous fallback uses a fresh `wrapper.clone()` after the authenticated single-shot Call. Similar upstream retry fixes must not restore reuse or add a duplicate attempt. The existing test documents limited auth-path coverage. |
| `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/presenters/BrowsePresenter.java`; commits `6c0f299`, `c37f22d` | Phone notification hooks, single startup selection, and retained browsing sections. An upstream presenter replacement can break these without a merge conflict. |
| `app/src/stmobile/java/com/liskovsoft/smartyoutubetv2/mobile/ui/playback/`; commits `7b3b45f`, `c37f22d` | Surface handoff, mini-player state, and swipe-to-dismiss lifecycle. Shared player/controller changes require reviewing these phone callers. |
| `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/misc/ScreensaverManager.java`; `ScreensaverManagerTest.java` | Overlay, suspend and unlock callback ordering; preserve the intended callback behavior when changing shared playback lifecycle. |
| `settings.gradle`, `gradle/dependencies.gradle`, `app/build.gradle` | Self-contained vendored dependencies and `stmobile` flavor; keep phone Firebase isolation, local dependency versions/wiring, and source layout. |
| `app/build.gradle`, `.github/workflows/release.yml`, `app/src/stmobile/java/com/liskovsoft/smartyoutubetv2/mobile/update/` | Package `dev.smarttubemobile.unofficial`, signing identity, preference/data continuity, monotonic mobile versionCode, and this fork's updater endpoints/assets. Never offer a TV APK as a phone update. |

Current release contracts are recorded in [VERSIONING.md](VERSIONING.md) and
[UPDATER_COMPATIBILITY.md](UPDATER_COMPATIBILITY.md). The release workflow currently
hardcodes `+st32.68` as well as the app's default version. Updating one string alone
does not integrate an upstream release. Preserve the disabled phone update entry
points until this fork's release discovery and compatibility are configured.

## Decision log

No upstream release comparison has been completed by this workflow yet. No baseline,
review cursor, or upstream version was advanced when introducing these files.

Append rows for each update; retain unresolved deferrals. Add full source commit IDs
and a date/channel in the target cell. For fixes spanning components, name each pin.

| Target / component | Upstream fix / changed paths | Decision | Local evidence / reason | Verification / follow-up |
| --- | --- | --- | --- | --- |

Useful task prompt:

> Reflect the latest stable SmartTube release using docs/UPSTREAM_SYNC.md. Preserve
> our mobile behavior and existing fixes. Account for upstream changes as ported,
> adapted, already-equivalent, not-applicable, or deferred, with evidence. Report
> unresolved gaps and distinguish static review from runtime verification. Do not
> run tests or builds unless I explicitly request them.

If the task also authorizes network access, add: "You may read upstream release
metadata and fetch source refs for comparison." Otherwise supply the local checkout
and release ref. An exact first baseline still needs evidence, even with network access.
