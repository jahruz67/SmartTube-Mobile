# Repository instructions

Keep changes focused and preserve the maintained `stmobile` flavor. Do not run tests,
builds, linters, installs, or apps without an explicit request. Static inspection is
allowed; never describe unrun checks as passing.

## Reflecting an upstream release

Read [docs/UPSTREAM_SYNC.md](docs/UPSTREAM_SYNC.md) before porting SmartTube,
SmarterTube, or vendored dependency changes. It defines the comparison procedure,
local behavior to preserve, and the decision log. Start from
[docs/upstream-state.json](docs/upstream-state.json); missing commit IDs mean unknown,
not permission to infer a baseline from a version string.

Use local Git data unless web/network access was explicitly authorized. Do not fetch,
merge, reset, replace a vendored directory, or advance upstream metadata simply to
make this fork resemble upstream. Port the relevant behavior and account for every
upstream change. Preserve existing user edits.

The current layout is defined by `settings.gradle` and `README.md`. Dependencies under
`third_party/` are tracked vendored sources. Older inherited documents may still call
them submodules or describe CodeSculptor's release identity; those descriptions are
historical. Preserve this fork's package, signing, updater, and version contracts.
