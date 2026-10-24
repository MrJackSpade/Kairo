#!/bin/sh
# check-workflow-pins.sh [<commit>]: fails unless, in an app's <commit> (HEAD by default), every call
# its build and release workflows make to the shared workflows is at the shared commit it pins.
#
# The tag build refuses a mismatch only after the tag is pushed (app-release.yml), so the pre-push
# hook and tag-release.ps1 run this first: a shared pin update must move these calls with it.
set -eu
commit=${1:-HEAD}
pinned=$(git rev-parse "$commit^{commit}:shared")
status=0
for file in .github/workflows/tag-builds.yml .github/workflows/play-release.yml .github/workflows/android.yml; do
    calls=$(git show "$commit:$file" 2>/dev/null | grep 'MrJackSpade/Kairo/.github/workflows/' || true)
    [ -n "$calls" ] || continue
    if printf '%s\n' "$calls" | grep -vq "@$pinned\$"; then
        echo "$file must call the shared workflows at the submodule's commit $pinned" >&2
        status=1
    fi
done
exit $status
