#!/usr/bin/env bash
# publish-release.sh — automate OTA release publication for RideDecider
#
# Publishes a new signed patch release of the app to GitHub Releases so
# devices already on a previous v1.0.x receive it through the in-app OTA
# check (AppUpdateManager), without manual reinstall.
#
# Usage:
#   ./scripts/publish-release.sh <versionName> "nota 1" ["nota 2" ...]
#
# Example:
#   ./scripts/publish-release.sh 1.0.4 \
#     "Fix del bug X en el HUD" \
#     "Mejora Y en la pantalla de Ajustes"
#
# What it does (in order, all-or-nothing):
#   1. Validates preconditions:
#        - script is run from the repo root or from scripts/
#        - working tree is clean
#        - current branch is `main`
#        - remote `origin` is reachable and up to date
#        - gh CLI is authenticated
#        - keystore credentials are configured (RELEASE_STORE_FILE, ...)
#        - apksigner is available
#        - versionName has valid MAJOR.MINOR.PATCH format
#        - versionName is strictly greater than the current one
#   2. Bumps versionCode (auto-increment by 1) and versionName in
#      app/build.gradle.kts.
#   3. Runs `./gradlew clean assembleRelease` to produce a signed APK.
#   4. Verifies the APK certificate SHA-256 matches the expected fingerprint
#      of the RideDecider release keystore. Aborts if they differ (protects
#      the OTA channel from being broken).
#   5. Renames app-release.apk -> RideDecider-<versionName>.apk.
#   6. Computes SHA-256 and byte size.
#   7. Writes app/build/outputs/apk/release/latest.json with:
#        versionCode, versionName, apkUrl, apkSha256, apkSizeBytes,
#        releaseDate (UTC ISO 8601), and the release notes passed as args.
#   8. Publishes the GitHub Release v<versionName> with the APK and
#      latest.json as assets.
#   9. Verifies the OTA endpoint now serves the new version.
#  10. Commits the versionCode/versionName bump and pushes to origin/main.
#
# What it does NOT do:
#   - Never touches source code beyond the two lines in app/build.gradle.kts.
#   - Never reads or logs signing passwords: Gradle reads them directly
#     from local.properties (gitignored).
#   - Never force-pushes, never rewrites history.
#   - Aborts before any destructive action if any precondition fails.
#
# Requirements:
#   - Git Bash (Windows) or bash (Linux/macOS)
#   - Android SDK with build-tools >= 30 (for apksigner)
#   - GitHub CLI (gh) authenticated
#   - local.properties with RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD,
#     RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD

set -euo pipefail

# ------------------------------------------------------------------ config
readonly EXPECTED_CERT_SHA256="2e10214ef49e776501761e65f6abc43b9dd42a239e8256d28aad9a52794dfc62"
readonly GRADLE_FILE="app/build.gradle.kts"
readonly RELEASE_DIR="app/build/outputs/apk/release"
readonly APK_SRC="$RELEASE_DIR/app-release.apk"
readonly REPO_OWNER="WyrdicuS"
readonly REPO_NAME="RideDecider"
readonly OTA_MANIFEST_URL="https://github.com/${REPO_OWNER}/${REPO_NAME}/releases/latest/download/latest.json"

# ------------------------------------------------------------------ helpers
c_red()   { printf "\033[31m%s\033[0m\n" "$*" >&2; }
c_green() { printf "\033[32m%s\033[0m\n" "$*"; }
c_blue()  { printf "\033[34m%s\033[0m\n" "$*"; }
c_gray()  { printf "\033[90m%s\033[0m\n" "$*"; }
die()     { c_red "ERROR: $*"; exit 1; }
step()    { c_blue ""; c_blue "==> $*"; }

usage() {
  cat <<EOF
Usage:
  $0 <versionName> "nota 1" ["nota 2" ...]

Example:
  $0 1.0.4 "Fix bug X" "Mejora Y"
EOF
  exit 2
}

# ------------------------------------------------------------------ args
[ $# -ge 2 ] || usage
readonly NEW_VERSION_NAME="$1"
shift
readonly NOTES=("$@")

# versionName must be MAJOR.MINOR.PATCH with numeric parts
if ! echo "$NEW_VERSION_NAME" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$'; then
  die "versionName must be MAJOR.MINOR.PATCH (got: '$NEW_VERSION_NAME')"
fi

# ------------------------------------------------------------------ locate repo root
# Allow running from scripts/ or from repo root
if [ -f "$GRADLE_FILE" ]; then
  REPO_ROOT="$(pwd)"
elif [ -f "../$GRADLE_FILE" ]; then
  REPO_ROOT="$(cd .. && pwd)"
else
  die "Cannot find $GRADLE_FILE. Run this script from the repo root or scripts/."
fi
cd "$REPO_ROOT"
c_gray "Repo root: $REPO_ROOT"

# ------------------------------------------------------------------ precondition checks
step "Checking preconditions"

# git installed and inside a git repo
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || die "Not inside a git repository."

# branch must be main
CURRENT_BRANCH="$(git branch --show-current)"
[ "$CURRENT_BRANCH" = "main" ] || die "Current branch is '$CURRENT_BRANCH', expected 'main'."
c_gray "Branch: main"

# working tree must be clean
if [ -n "$(git status --porcelain)" ]; then
  git status --short >&2
  die "Working tree is not clean. Commit or stash your changes first."
fi
c_gray "Working tree: clean"

# origin/main must exist and be up to date
git fetch origin main --quiet
LOCAL_HEAD="$(git rev-parse HEAD)"
REMOTE_HEAD="$(git rev-parse origin/main)"
[ "$LOCAL_HEAD" = "$REMOTE_HEAD" ] || die "Local main is not in sync with origin/main. Pull or push first."
c_gray "In sync with origin/main"

# gh CLI authenticated
command -v gh >/dev/null 2>&1 || die "GitHub CLI (gh) not found. Install it."
gh auth status >/dev/null 2>&1 || die "GitHub CLI not authenticated. Run: gh auth login"
c_gray "gh CLI: authenticated"

# release credentials must be present (Gradle will actually read them)
for key in RELEASE_STORE_FILE RELEASE_STORE_PASSWORD RELEASE_KEY_ALIAS RELEASE_KEY_PASSWORD; do
  if [ -f local.properties ] && grep -q "^${key}=" local.properties; then
    continue
  fi
  if [ -n "${!key:-}" ]; then
    continue
  fi
  die "Missing signing property '$key' in local.properties and environment."
done
c_gray "Signing credentials: found"

# apksigner
SDK_DIR_RAW="$(grep '^sdk.dir=' local.properties 2>/dev/null | sed 's/^sdk.dir=//' | tr -d '\r')"
if [ -n "$SDK_DIR_RAW" ]; then
  # Java properties escape ":" as "\:" and "\" as "\\". Use Python for a
  # reliable unescape (bash parameter expansion breaks on the \\ pattern
  # in some Git Bash versions), then convert to POSIX with cygpath.
  if command -v python >/dev/null 2>&1; then
    SDK_DIR="$(python -c "import sys; s=sys.argv[1]; print(s.replace(chr(92)+':',':').replace(chr(92)*2, chr(92)))" "$SDK_DIR_RAW")"
  elif command -v python3 >/dev/null 2>&1; then
    SDK_DIR="$(python3 -c "import sys; s=sys.argv[1]; print(s.replace(chr(92)+':',':').replace(chr(92)*2, chr(92)))" "$SDK_DIR_RAW")"
  else
    SDK_DIR="$SDK_DIR_RAW"
  fi
  if command -v cygpath >/dev/null 2>&1; then
    SDK_DIR="$(cygpath -u "$SDK_DIR" 2>/dev/null || echo "$SDK_DIR")"
  fi
else
  SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
fi
[ -n "$SDK_DIR" ] || die "Could not locate Android SDK (sdk.dir in local.properties or ANDROID_HOME)."

# On Windows use apksigner.bat, elsewhere apksigner
if [ -f "$SDK_DIR/build-tools" ] || [ -d "$SDK_DIR/build-tools" ]; then
  APKSIGNER="$(find "$SDK_DIR/build-tools" -maxdepth 2 -name 'apksigner.bat' 2>/dev/null | sort -V | tail -1)"
  [ -n "$APKSIGNER" ] || APKSIGNER="$(find "$SDK_DIR/build-tools" -maxdepth 2 -name 'apksigner' 2>/dev/null | sort -V | tail -1)"
fi
[ -n "${APKSIGNER:-}" ] && [ -e "$APKSIGNER" ] || die "apksigner not found under $SDK_DIR/build-tools"
c_gray "apksigner: $APKSIGNER"

# Windows-style path required by apksigner.bat
apksigner_win_path() {
  local p="$1"
  if [ "${APKSIGNER: -4}" = ".bat" ]; then
    # convert /c/... -> C:\...
    echo "$p" | sed 's|^/\([a-zA-Z]\)/|\1:\\|' | sed 's|/|\\|g'
  else
    echo "$p"
  fi
}

# gradle wrapper
if [ -f "gradlew.bat" ]; then
  GRADLE_CMD="./gradlew.bat"
elif [ -f "gradlew" ]; then
  GRADLE_CMD="./gradlew"
else
  die "No gradlew found in repo root."
fi
c_gray "Gradle: $GRADLE_CMD"

# ------------------------------------------------------------------ version detection
step "Reading current version"
CURRENT_VERSION_CODE="$(grep -E '^\s*versionCode\s*=\s*[0-9]+' "$GRADLE_FILE" | head -1 | sed 's/.*=\s*//' | tr -d ' \r')"
CURRENT_VERSION_NAME="$(grep -E '^\s*versionName\s*=\s*"' "$GRADLE_FILE" | head -1 | sed 's/.*=\s*"\(.*\)"/\1/' | tr -d '\r')"
[ -n "$CURRENT_VERSION_CODE" ] || die "Could not parse current versionCode from $GRADLE_FILE"
[ -n "$CURRENT_VERSION_NAME" ] || die "Could not parse current versionName from $GRADLE_FILE"
c_gray "Current: versionCode=$CURRENT_VERSION_CODE versionName=$CURRENT_VERSION_NAME"
c_gray "New:     versionCode=$((CURRENT_VERSION_CODE + 1)) versionName=$NEW_VERSION_NAME"

# ensure versionName is strictly greater
version_lte() {
  # returns 0 iff $1 <= $2 (semver compare on MAJOR.MINOR.PATCH)
  local a b
  a="$1"; b="$2"
  [ "$(printf '%s\n%s\n' "$a" "$b" | sort -V | head -1)" = "$a" ]
}
if version_lte "$NEW_VERSION_NAME" "$CURRENT_VERSION_NAME"; then
  die "New versionName '$NEW_VERSION_NAME' is not greater than current '$CURRENT_VERSION_NAME'."
fi

NEW_VERSION_CODE=$((CURRENT_VERSION_CODE + 1))

# ------------------------------------------------------------------ apply bump
step "Bumping version in $GRADLE_FILE"
# Portable sed in-place: create a backup then remove it
sed -i.bak \
  -e "s/^\(\s*versionCode\s*=\s*\)[0-9]\+/\1${NEW_VERSION_CODE}/" \
  -e "s/^\(\s*versionName\s*=\s*\"\)[^\"]*\(\"\)/\1${NEW_VERSION_NAME}\2/" \
  "$GRADLE_FILE"
rm -f "${GRADLE_FILE}.bak"

# verify the edit
grep -E "versionCode\s*=\s*${NEW_VERSION_CODE}" "$GRADLE_FILE" >/dev/null || die "versionCode bump failed."
grep -E "versionName\s*=\s*\"${NEW_VERSION_NAME}\"" "$GRADLE_FILE" >/dev/null || die "versionName bump failed."
c_green "Bumped."

# ------------------------------------------------------------------ build
step "Building signed release APK (this can take a couple of minutes)"
"$GRADLE_CMD" clean assembleRelease >/dev/null
[ -f "$APK_SRC" ] || die "Expected APK not found at $APK_SRC"
c_green "APK built."

# ------------------------------------------------------------------ signature verification
step "Verifying APK signature matches the RideDecider release keystore"
APK_WIN="$(apksigner_win_path "$REPO_ROOT/$APK_SRC")"
CERT_INFO="$("$APKSIGNER" verify --print-certs "$APK_WIN" 2>&1 || true)"
ACTUAL_CERT_SHA="$(echo "$CERT_INFO" | grep -i "SHA-256 digest:" | head -1 | awk '{print $NF}')"
if [ -z "$ACTUAL_CERT_SHA" ]; then
  echo "$CERT_INFO" >&2
  die "Could not extract certificate SHA-256 from apksigner output."
fi
c_gray "Expected cert SHA-256: $EXPECTED_CERT_SHA256"
c_gray "Actual   cert SHA-256: $ACTUAL_CERT_SHA"
if [ "$ACTUAL_CERT_SHA" != "$EXPECTED_CERT_SHA256" ]; then
  c_red "Signature mismatch. Publishing this APK would break the OTA channel"
  c_red "(devices with previous versions could not install this update)."
  c_red "Reverting the version bump."
  git checkout -- "$GRADLE_FILE"
  die "Aborted."
fi
c_green "Signature OK — compatible with existing installations."

# ------------------------------------------------------------------ rename + hash
step "Preparing release assets"
APK_DST="$RELEASE_DIR/RideDecider-${NEW_VERSION_NAME}.apk"
cp "$APK_SRC" "$APK_DST"
APK_SIZE="$(stat -c%s "$APK_DST" 2>/dev/null || stat -f%z "$APK_DST")"
APK_SHA256="$(sha256sum "$APK_DST" | awk '{print $1}')"
TODAY_ISO="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
c_gray "APK:    $APK_DST"
c_gray "Size:   $APK_SIZE bytes"
c_gray "SHA256: $APK_SHA256"
c_gray "Date:   $TODAY_ISO"

# ------------------------------------------------------------------ latest.json
step "Writing latest.json"
LATEST_JSON="$RELEASE_DIR/latest.json"
{
  echo "{"
  echo "  \"versionCode\": ${NEW_VERSION_CODE},"
  echo "  \"versionName\": \"${NEW_VERSION_NAME}\","
  echo "  \"minSupportedVersionCode\": 1,"
  echo "  \"mandatory\": false,"
  echo "  \"apkUrl\": \"https://github.com/${REPO_OWNER}/${REPO_NAME}/releases/download/v${NEW_VERSION_NAME}/RideDecider-${NEW_VERSION_NAME}.apk\","
  echo "  \"apkSha256\": \"${APK_SHA256}\","
  echo "  \"apkSizeBytes\": ${APK_SIZE},"
  echo "  \"releaseDate\": \"${TODAY_ISO}\","
  echo "  \"releaseNotes\": ["
  local_count=${#NOTES[@]}
  i=0
  for note in "${NOTES[@]}"; do
    i=$((i + 1))
    # escape " and \ inside the note
    escaped="$(printf '%s' "$note" | sed 's/\\/\\\\/g; s/"/\\"/g')"
    if [ "$i" -lt "$local_count" ]; then
      echo "    \"${escaped}\","
    else
      echo "    \"${escaped}\""
    fi
  done
  echo "  ]"
  echo "}"
} > "$LATEST_JSON"
c_green "Manifest written: $LATEST_JSON"

# ------------------------------------------------------------------ github release
step "Publishing GitHub Release v${NEW_VERSION_NAME}"
NOTES_MD="Patch release automatically published by scripts/publish-release.sh.

### Notas

$(for n in "${NOTES[@]}"; do echo "- $n"; done)

### Compatibilidad OTA

Firmado con el mismo keystore que las versiones anteriores. Certificado SHA-256: \`${EXPECTED_CERT_SHA256}\`. Los dispositivos ya instalados pueden actualizar directamente sin desinstalar."

gh release create "v${NEW_VERSION_NAME}" \
  --title "RideDecider ${NEW_VERSION_NAME}" \
  --notes "$NOTES_MD" \
  "$APK_DST" \
  "$LATEST_JSON" >/dev/null
c_green "GitHub Release published."

# ------------------------------------------------------------------ verify endpoint
step "Verifying OTA endpoint serves the new version"
sleep 2
SERVED_JSON="$(curl -sSL "$OTA_MANIFEST_URL")"
SERVED_CODE="$(echo "$SERVED_JSON" | grep '"versionCode"' | head -1 | grep -oE '[0-9]+' | head -1)"
if [ "$SERVED_CODE" = "$NEW_VERSION_CODE" ]; then
  c_green "OTA endpoint now serves versionCode=${NEW_VERSION_CODE}."
else
  c_red "OTA endpoint returned versionCode=$SERVED_CODE (expected $NEW_VERSION_CODE)."
  c_red "The GitHub release was created but the endpoint hasn't propagated yet."
  c_red "This is usually a delay of a few seconds; check manually in a minute."
fi

# ------------------------------------------------------------------ commit + push
step "Committing version bump and pushing to origin/main"
git add "$GRADLE_FILE"
git commit -m "chore(release): bump to v${NEW_VERSION_NAME} (versionCode ${NEW_VERSION_CODE})

Automatically committed by scripts/publish-release.sh after publishing
the GitHub release v${NEW_VERSION_NAME} signed with the standard
RideDecider release keystore (cert SHA-256 ${EXPECTED_CERT_SHA256:0:12}...).

Release URL: https://github.com/${REPO_OWNER}/${REPO_NAME}/releases/tag/v${NEW_VERSION_NAME}"
git push origin main >/dev/null
c_green "Pushed."

# ------------------------------------------------------------------ summary
c_blue ""
c_green "============================================================"
c_green "  Release v${NEW_VERSION_NAME} published successfully."
c_green "============================================================"
c_gray "  versionCode:  ${NEW_VERSION_CODE}"
c_gray "  versionName:  ${NEW_VERSION_NAME}"
c_gray "  APK size:     ${APK_SIZE} bytes"
c_gray "  APK SHA-256:  ${APK_SHA256}"
c_gray "  Release URL:  https://github.com/${REPO_OWNER}/${REPO_NAME}/releases/tag/v${NEW_VERSION_NAME}"
c_gray "  OTA manifest: ${OTA_MANIFEST_URL}"
c_blue ""
c_gray "  Devices on v${CURRENT_VERSION_NAME} will see the update dialog on next app launch."
