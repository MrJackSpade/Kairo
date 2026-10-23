#!/bin/sh
# check-release-version.sh <tag> <version>: fails unless <tag> is v<version>.
#
# Every Kairo app releases the same version, the one in shared/release-version. The apps' pre-push
# hooks pass the version from the shared commit a tag's commit pins, and the release workflows
# the one in their checked-out shared/, so no app can tag or publish another version.
set -eu
tag=$1
version=$(printf '%s' "$2" | tr -d ' \r\n')
if [ -z "$version" ]; then
    echo "shared/release-version is empty" >&2
    exit 1
fi
if [ "$tag" != "v$version" ]; then
    echo "Tag $tag is not the Kairo release version v$version (shared/release-version)." >&2
    echo "Every Kairo app releases the same version: change shared/release-version, pin it in every app, then tag them all with shared/tools/tag-release.ps1." >&2
    exit 1
fi
