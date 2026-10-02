#!/usr/bin/env python3
from audit_catalog_parts import *
if __name__ == '__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('product',choices=('dos','pc98'));parser.add_argument('artifacts',nargs='*',type=Path)
    args=parser.parse_args();audit(Path.cwd(),args.product,args.artifacts)
