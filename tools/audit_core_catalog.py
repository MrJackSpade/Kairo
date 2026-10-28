#!/usr/bin/env python3
"""Audits the published catalog of the app whose root is the current directory (catalog_parts.APPS).

  audit_core_catalog.py [product] [artifacts ...]

Without a product, the app is the one whose module folder (kairo64/, kairo98/, ...) the root holds;
shared/tools/check-app.sh runs it that way for every app.
"""
from audit_catalog_parts import *


def own_product(root):
    found = [spec.product for app, spec in APPS.items() if (root / app).is_dir()]
    if len(found) != 1:
        raise SystemExit(f'{root} is not one Kairo app root: module folders for {found or "no app"}')
    return found[0]


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('product',nargs='?',choices=sorted(spec.product for spec in APPS.values()));parser.add_argument('artifacts',nargs='*',type=Path)
    args=parser.parse_args();audit(Path.cwd(),args.product or own_product(Path.cwd()),args.artifacts)
