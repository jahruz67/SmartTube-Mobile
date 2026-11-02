#!/usr/bin/env bash
# Reproducible developer build wrapper for the public SmarterTube mobile fork.
# Does not redistribute or modify upstream source and never imports signing keys.
set -Eeuo pipefail

REPO_URL='https://github.com/CodeSculptor/SmarterTube.git'
RELEASE_TAG='v0.9.10-beta.1+st32.68'
PINNED_COMMIT='3b780e9a8ac62ba59c90d090fa46d87210c45525'
WORK_ROOT="${WORK_ROOT:-$PWD/smarttube-mobile-work}"
SOURCE_DIR="${SOURCE_DIR:-$WORK_ROOT/SmarterTube}"
REPORT_DIR="${REPORT_DIR:-$WORK_ROOT/reports}"
AUDIT_ONLY=0
TESTS=1
MOBILE_UI=1
KIT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

usage() {
  cat <<USAGE
Usage: $0 [--audit-only] [--skip-tests] [--skip-mobile-ui]
Environment:
  WORK_ROOT    Working directory (default: ./smarttube-mobile-work)
  SOURCE_DIR   Use an existing checkout at this path (otherwise clone pinned release)
  REPORT_DIR   Store reproducibility manifests and test logs
Requirements:
  git, Bash, JDK 17, Android SDK (ANDROID_HOME or ANDROID_SDK_ROOT), Internet
USAGE
}

for arg in "$@"; do
  case "$arg" in
    --audit-only) AUDIT_ONLY=1 ;;
    --skip-tests) TESTS=0 ;;
    --skip-mobile-ui) MOBILE_UI=0 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $arg" >&2; usage >&2; exit 2 ;;
  esac
done

command -v git >/dev/null || { echo 'ERROR: git is required' >&2; exit 3; }
command -v sha256sum >/dev/null || { echo 'ERROR: sha256sum is required' >&2; exit 3; }
mkdir -p "$WORK_ROOT" "$REPORT_DIR"

if [[ ! -e "$SOURCE_DIR" ]]; then
  echo "Cloning pinned public source: $RELEASE_TAG"
  git clone --branch "$RELEASE_TAG" --depth 1 "$REPO_URL" "$SOURCE_DIR"
fi
[[ -d "$SOURCE_DIR/.git" ]] || { echo 'ERROR: SOURCE_DIR is not a Git repository' >&2; exit 4; }

git -C "$SOURCE_DIR" fetch --tags --force origin "$RELEASE_TAG"
ACTUAL_COMMIT="$(git -C "$SOURCE_DIR" rev-parse "${RELEASE_TAG}^{commit}")"
[[ "$ACTUAL_COMMIT" == "$PINNED_COMMIT" ]] || {
  echo "ERROR: Tag moved or wrong source revision: $ACTUAL_COMMIT" >&2
  exit 5
}
# Do not discard modifications in an existing checkout.
if [[ -n "$(git -C "$SOURCE_DIR" status --porcelain)" ]]; then
  echo 'ERROR: Source tree is dirty. Commit or stash your changes first.' >&2
  exit 6
fi
git -C "$SOURCE_DIR" checkout --detach "$PINNED_COMMIT"
git -C "$SOURCE_DIR" submodule sync --recursive
git -C "$SOURCE_DIR" submodule update --init --recursive

# The debug flavor inherits the release keystore if keystore.properties exists.
# Prevent accidental reuse of a private signing identity.
if [[ -e "$SOURCE_DIR/keystore.properties" ]]; then
  echo 'ERROR: Refusing to build debug APK using keystore.properties.' >&2
  exit 7
fi

{
  echo "source=$REPO_URL"
  echo "tag=$RELEASE_TAG"
  echo "commit=$(git -C "$SOURCE_DIR" rev-parse HEAD)"
  echo "timestamp_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "mobile_ui_patch=$MOBILE_UI"
  echo 'submodules:'
  git -C "$SOURCE_DIR" submodule status --recursive
  echo 'gradle_wrapper:'
  sha256sum "$SOURCE_DIR/gradle/wrapper/gradle-wrapper.jar" 2>/dev/null || echo 'wrapper jar missing'
} > "$REPORT_DIR/BUILD_PROVENANCE.txt"

if [[ "$AUDIT_ONLY" -eq 1 ]]; then
  echo 'Source and submodule provenance recorded. No build attempted.'
  echo "$REPORT_DIR/BUILD_PROVENANCE.txt"
  exit 0
fi

command -v python3 >/dev/null || { echo 'ERROR: Python 3 is required for the mobile UI patch and APK verification' >&2; exit 8; }
python3 -m unittest discover -s "$KIT_DIR/tests" -v > "$REPORT_DIR/patch-tests.log" 2>&1 || {
  cat "$REPORT_DIR/patch-tests.log" >&2
  echo 'ERROR: Local patch tests failed' >&2
  exit 8
}
if [[ "$MOBILE_UI" -eq 1 ]]; then
  python3 "$KIT_DIR/scripts/apply-bottom-navigation.py" "$SOURCE_DIR" \
    2>&1 | tee "$REPORT_DIR/mobile-ui-patch.log"
  python3 "$KIT_DIR/scripts/apply-development-identity.py" "$SOURCE_DIR" \
    2>&1 | tee "$REPORT_DIR/development-identity.log"
  python3 "$KIT_DIR/scripts/install-mobile-smoke-test.py" "$SOURCE_DIR" \
    2>&1 | tee "$REPORT_DIR/instrumentation-install.log"
  python3 "$KIT_DIR/scripts/apply-build-compatibility.py" "$SOURCE_DIR" \
    2>&1 | tee "$REPORT_DIR/build-compatibility.log"
  # Include newly created files in the patch without staging their contents or committing.
  git -C "$SOURCE_DIR" add -N -- smarttubetv/src/stmobile/res/values/mobile_bottom_nav_strings.xml smarttubetv/src/androidTestStmobile/java/dev/smarttubemobile/tests/MobileBottomNavigationInstrumentationTest.java smarttubetv/src/stmobile/res/mipmap-mdpi/smarttube_mobile_dev_icon.png smarttubetv/src/stmobile/res/mipmap-hdpi/smarttube_mobile_dev_icon.png smarttubetv/src/stmobile/res/mipmap-xhdpi/smarttube_mobile_dev_icon.png smarttubetv/src/stmobile/res/mipmap-xxhdpi/smarttube_mobile_dev_icon.png smarttubetv/src/stmobile/res/mipmap-xxxhdpi/smarttube_mobile_dev_icon.png
  git -C "$SOURCE_DIR" diff --binary -- smarttubetv/src/stmobile/ smarttubetv/src/androidTestStmobile/ smarttubetv/build.gradle > "$REPORT_DIR/MOBILE_UI.patch"
  git -C "$SOURCE_DIR" status --short > "$REPORT_DIR/POST_PATCH_STATUS.txt"
fi
command -v java >/dev/null || { echo 'ERROR: JDK 17 required' >&2; exit 8; }
JAVA_VERSION="$(java -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p')"
[[ "$JAVA_VERSION" == 17 ]] || {
  echo "ERROR: JDK 17 required; detected Java ${JAVA_VERSION:-unknown}. Set JAVA_HOME." >&2
  exit 8
}
SDK_PATH="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[[ -n "$SDK_PATH" && -d "$SDK_PATH" ]] || {
  echo 'ERROR: Set ANDROID_HOME or ANDROID_SDK_ROOT to an installed Android SDK.' >&2
  exit 9
}
[[ -f "$SOURCE_DIR/gradlew" ]] || { echo 'ERROR: Gradle wrapper script not found' >&2; exit 10; }

# Gradle may use the network to fetch declared dependencies. Inspect first if needed.
(
  cd "$SOURCE_DIR"
  bash ./gradlew --no-daemon --stacktrace :smarttubetv:assembleStmobileDebug
) 2>&1 | tee "$REPORT_DIR/gradle-build.log"

if [[ "$TESTS" -eq 1 ]]; then
  if (
    cd "$SOURCE_DIR"
    bash ./gradlew --no-daemon --stacktrace :smarttubetv:testStmobileDebugUnitTest
  ) > "$REPORT_DIR/gradle-tests.log" 2>&1; then
    echo PASS > "$REPORT_DIR/UNIT_TEST_STATUS.txt"
  else
    echo FAIL > "$REPORT_DIR/UNIT_TEST_STATUS.txt"
    echo 'WARNING: Unit tests failed. See gradle-tests.log; build artifact is not feature-verified.' >&2
  fi
else
  echo NOT_RUN > "$REPORT_DIR/UNIT_TEST_STATUS.txt"
fi

APK_DIR="$SOURCE_DIR/smarttubetv/build/outputs/apk/stmobile"
if [[ ! -d "$APK_DIR" ]]; then
  echo "ERROR: No APK directory at $APK_DIR" >&2
  exit 11
fi
mapfile -d '' APK_FILES < <(find "$APK_DIR" -type f -name '*.apk' -print0)
[[ "${#APK_FILES[@]}" -gt 0 ]] || { echo 'ERROR: Gradle produced no APK.' >&2; exit 12; }
: > "$REPORT_DIR/APK_SHA256.txt"
for apk in "${APK_FILES[@]}"; do
  sha256sum "$apk" >> "$REPORT_DIR/APK_SHA256.txt"
  python3 "$KIT_DIR/scripts/inspect-apk.py" "$apk" \
    --package "$([[ "$MOBILE_UI" -eq 1 ]] && echo dev.smarttubemobile.unofficial || echo com.codesculptor.smartertube)" >> "$REPORT_DIR/APK_INSPECTION.txt"
  echo "APK: $apk"
done

echo 'Result: compilation completed; installation and runtime features still require device testing.'
