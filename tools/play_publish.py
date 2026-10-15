#!/usr/bin/env python3
"""Publishes a signed Play bundle to a Google Play track through the Android Publisher API.

One edit: upload the bundle, set it as the track's completed release, commit. The access token
comes from GOOGLE_PLAY_ACCESS_TOKEN (the workflow's workload-identity sign-in).
"""
import argparse
import json
import os
import urllib.error
import urllib.request

API = 'https://androidpublisher.googleapis.com/androidpublisher/v3/applications'
UPLOAD = 'https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications'


def call(method, url, token, body=None, content_type='application/json'):
    if content_type == 'application/json' and body is not None:
        body = json.dumps(body).encode()
    request = urllib.request.Request(url, data=body, method=method, headers={
        'Authorization': f'Bearer {token}', 'Content-Type': content_type})
    try:
        with urllib.request.urlopen(request) as response:
            return json.loads(response.read() or b'{}')
    except urllib.error.HTTPError as error:
        raise SystemExit(f'{method} {url.split("?")[0]} failed ({error.code}): {error.read().decode()}')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('package')
    parser.add_argument('bundle')
    parser.add_argument('track')
    args = parser.parse_args()
    token = os.environ['GOOGLE_PLAY_ACCESS_TOKEN']
    app = f'{API}/{args.package}'

    edit = call('POST', f'{app}/edits', token, {})['id']
    with open(args.bundle, 'rb') as stream:
        uploaded = call('POST', f'{UPLOAD}/{args.package}/edits/{edit}/bundles?uploadType=media',
                        token, stream.read(), 'application/octet-stream')
    version = uploaded['versionCode']
    print(f'{args.package}: uploaded version code {version} (SHA-256 {uploaded["sha256"]})')
    call('PUT', f'{app}/edits/{edit}/tracks/{args.track}', token,
         {'track': args.track, 'releases': [{'versionCodes': [str(version)], 'status': 'completed'}]})
    call('POST', f'{app}/edits/{edit}:commit', token)
    print(f'{args.package}: version code {version} committed to the {args.track} track')


if __name__ == '__main__':
    main()
