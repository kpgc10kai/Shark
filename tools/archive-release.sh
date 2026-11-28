#!/usr/bin/env bash
# Archive a superseded GitHub release:
#   - delete its downloadable assets
#   - mark it as a pre-release
#   - leave a notice pointing at the latest release
#
# Usage: tools/archive-release.sh v2.5.1-en.2
set -euo pipefail

repo="${REPO:-kpgc10kai/Shark-en}"
tag="${1:?usage: tools/archive-release.sh <tag>}"
tag="${tag#v}"

if ! gh release view "v$tag" --repo "$repo" >/dev/null 2>&1; then
    echo "release v$tag not found in $repo" >&2
    exit 1
fi

# 2.5.1-en.6 and later are retained as downloadable APKs.
if [[ "$tag" =~ ^2\.5\.1-en\.([0-9]+)$ ]] && (( BASH_REMATCH[1] >= 6 )); then
    echo "refusing to archive retained release v$tag" >&2
    exit 1
fi

assets_file=$(mktemp)
trap 'rm -f "$assets_file"' EXIT
gh release view "v$tag" --repo "$repo" --json assets --jq '.assets[].name' | tr -d '\r' > "$assets_file"
while IFS= read -r asset; do
    [ -n "$asset" ] || continue
    echo "deleting $asset"
    gh release delete-asset "v$tag" "$asset" --repo "$repo" --yes </dev/null
done < "$assets_file"

gh release edit "v$tag" \
    --repo "$repo" \
    --prerelease \
    --title "[ARCHIVED] Shark v$tag" \
    --notes "This build is archived and its APK has been withdrawn.

Download the latest release: https://github.com/$repo/releases/latest"

echo "archived v$tag"