#!/usr/bin/env bash
set -euo pipefail

shared_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ "$(git -C "$shared_root" rev-parse HEAD)" != "$1" ]]; then
  echo "Shared frontend checkout does not match the pinned commit" >&2
  exit 1
fi
if [[ -n "$(git -C "$shared_root" status --porcelain)" ]]; then
  echo "Shared frontend checkout is dirty" >&2
  exit 1
fi
