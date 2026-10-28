#!/bin/sh
# Verifies a Kairo app's generated files and prepares its build, run from the app's root by the
# shared build and release workflows (app-build.yml, app-release.yml): the shared .gitattributes,
# the README's shared blocks, the catalog and artwork of an app on the shared catalog
# (tools/catalog_config.py), the published catalog's audit (adult artwork and descriptions kept out
# of the main catalog), and the app's own checks and build preparation, tools/checks.sh.
set -e
if ! cmp -s shared/app/gitattributes .gitattributes; then
    echo '.gitattributes differs from shared/app/gitattributes; copy it' >&2
    exit 1
fi
python3 shared/tools/docs.py . --check
if [ -f tools/catalog_config.py ]; then
    python3 shared/tools/app_catalog.py . --check
    python3 shared/tools/fetch_artwork.py . --check
fi
python3 shared/tools/audit_core_catalog.py
if [ -f tools/checks.sh ]; then
    sh tools/checks.sh
fi
