#!/usr/bin/env bash
# Publish the latest commit on the current branch: push the branch, then push a tag.
# The tag (v*) is what triggers the GitHub Actions release build.
#
# The tag is ALWAYS auto-incremented. An optional label is appended when given.
# Usage:
#   ./tools/publish-to-github.sh             # -> viktor.<next>
#   ./tools/publish-to-github.sh ai-editor   # -> viktor.<next>-ai-editor
set -euo pipefail
cd /home/viktor/Anki-Android

BASE="viktor"   # tag prefix; the auto-incremented number is appended as ".N"
BRANCH=$(git rev-parse --abbrev-ref HEAD)

git fetch --tags --quiet origin || true

# Next number = highest existing ".N" (ignoring any "-label" suffix) + 1
last=0
for t in $(git tag -l "${BASE}.*"); do
  n="${t#"${BASE}".}"   # "1" or "1-ai-editor"
  n="${n%%-*}"          # strip "-label" -> "1"
  [[ "$n" =~ ^[0-9]+$ ]] && (( n > last )) && last=$n
done

TAG="${BASE}.$(( last + 1 ))"
[ -n "${1:-}" ] && TAG="${TAG}-$1"   # append optional label

echo "Branch: $BRANCH"
echo "Tag:    $TAG"

git push origin "$BRANCH"
git tag "$TAG"
git push origin "$TAG"

echo "Published $TAG -> release build triggered. Watch it with: gh run watch"
