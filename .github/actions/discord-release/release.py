"""Announce a GitHub release in the Kairo Discord server's releases channel, which every Kairo app shares.

The announcement is titled after the release, headed by the repository's name when the release
name does not already start with it. The channel is a text channel, so it is a message, not a post.
"""

import json
import os
import subprocess
import sys
import urllib.error
import urllib.request

tag = sys.argv[1]
repo = os.environ["GITHUB_REPOSITORY"]
webhook = os.environ["DISCORD_RELEASE_WEBHOOK"]
if not webhook:
    raise SystemExit("The Kairo Discord releases webhook secret is not set in this repository")
repo_name = repo.split("/")[1]
fields = "name,tagName,url,body,isPrerelease,publishedAt,author,assets"
release = json.loads(subprocess.check_output(["gh", "release", "view", tag, "-R", repo, "--json", fields]))


def clip(text, limit):
    return text if len(text) <= limit else text[: limit - 1] + "…"


title = release["name"] or release["tagName"]
if not title.startswith(repo_name):
    title = f"{repo_name} {title}"
title += " (pre-release)" if release["isPrerelease"] else ""
downloads = "\n".join(f"[{asset['name']}]({asset['url']})" for asset in release["assets"])
embed = {
    "title": clip(title, 256),
    "url": release["url"],
    "color": 0x5865F2,
    "timestamp": release["publishedAt"],
    "author": {"name": release["author"]["login"], "url": f"https://github.com/{release['author']['login']}"},
}
if release["body"]:
    embed["description"] = clip(release["body"], 4096)
if downloads:
    embed["fields"] = [{"name": "Downloads", "value": clip(downloads, 1024)}]

payload = {"embeds": [embed], "allowed_mentions": {"parse": []}}
request = urllib.request.Request(
    webhook + "?wait=true",
    data=json.dumps(payload).encode(),
    headers={"Content-Type": "application/json", "User-Agent": f"{repo} discord-release"},
)
try:
    with urllib.request.urlopen(request) as response:
        message = json.load(response)
except urllib.error.HTTPError as error:
    raise RuntimeError(f"Discord webhook -> {error.code}: {error.read().decode()}") from None
print(f"Announced {release['tagName']} as Discord message {message['id']} in channel {message['channel_id']}")
