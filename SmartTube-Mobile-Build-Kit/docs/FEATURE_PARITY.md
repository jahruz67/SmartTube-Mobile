# Feature parity report

These are **upstream fork claims**, not assistant-verified runtime results. Source baseline: SmarterTube release v0.9.10-beta.1+st32.68. The fork's feature matrix was last explicitly reviewed for an earlier release, so current behavior needs a fresh pass.

| Feature | Reported in mobile fork | Verified in this environment | Action |
| --- | --- | --- | --- |
| Home feed, subscriptions, history, playlists | Works | No | Device smoke test |
| Search, suggestions, voice search | Works | No | Permission/keyboard testing |
| Channel pages and channel tabs | Works | No | Scroll/sort/filter testing |
| Portrait + fullscreen landscape player | Works, portrait partially working in prior matrix | No | Device retest |
| Quality, subtitles, playback speed | Works | No | Test multiple formats |
| SponsorBlock | Works | No | Test category skipping/configuration |
| Return YouTube Dislike and DeArrow | Works | No | Verify network integration |
| OAuth sign-in, account switcher, subscriptions | Works | No | Test with authorized test account |
| Shorts player | Works | No | Swipe/memory lifecycle tests |
| Background audio, media notifications | Works (media controls improved in latest release) | No | Android 13-16 testing |
| Picture-in-Picture | Works (lock/unlock fix in latest release) | No | Android lifecycle testing |
| Touch settings | Works | No | Check persistence and reachability |
| Tablet layout | Works | No | Rotate/foldable testing |
| Persistent in-app mini player | Not established from docs | No | Inspect source and implement if absent |
| Global mobile bottom navigation | Authored source changes for Home/Shorts/Subscriptions/Library/Search and test overlay | **Structural tests only** | Compile and device-verify all click routes, selected state and nonpresent sidebar sections |
| Bottom-bar accessibility | Five touch targets with labels and no duplicate child announcements; instrumented test authored | **Structural tests only** | Run x86 and Android 16 instrumented tests; verify TalkBack and system gesture insets |
| Independent developer app identity | Source patch for package, version `v0.1.0-alpha.3+st32.68` (compatible with fork version parser), icon, label and attribution | **Structural tests only** | Inspect merged manifest, signing cert, auth and installation side by side with original fork |
| Fork telemetry isolation | Original endpoint disabled in source resources, runtime source guarded by isConfigured | **Source logic inspected; not runtime-tested** | Check logs and packet captures for accidental telemetry requests |
| Incompatible auto-updater isolation | Inherited auto-update launch path disabled, About update control hidden | **Structural tests only** | Validate user can never install another app ID from the in-app updater |
| Android 11 x86 emulator smoke | Automated job implemented for the existing x86 APK split | **Not run** | Run GitHub Actions and inspect status artifact |
| Android 16 x86_64 emulator smoke | Separate compatibility probe; ABI split may prevent installation | **Not run** | Check APK ABI and emulator `ro.product.cpu.abilist` before interpreting failure |
| Posting comments | Not implemented | No | Depends on upstream auth/PoToken support |
| YouTube notification inbox | Not available | No | Existing upload notification substitute |
| Chromecast sender | Not supported by design | No | Out of existing fork scope |
| Lock-screen seeking | Conflicting documentation across releases | No | Retest latest release (media notification seek bar claim) |
| Modern target SDK compatibility | Mobile build currently targets API 33 | No | Audit Android 16 behavior before changing target |

Source references:
- https://github.com/CodeSculptor/SmarterTube/blob/master/docs/FEATURE_MATRIX.md
- https://github.com/CodeSculptor/SmarterTube/blob/master/docs/KNOWN_ISSUES.md
- https://github.com/CodeSculptor/SmarterTube/releases/latest
