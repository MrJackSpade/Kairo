"""Mirror GitHub issues and their comments into a Discord forum channel.

Every Kairo app mirrors into the same two Kairo forums. An issue that only the repository owner
has opened and commented on goes to the owner's forum (DISCORD_OWNER_WEBHOOK); one that anyone
else has opened or commented on goes to the public forum (DISCORD_FORUM_WEBHOOK). Bots do not
count. When an issue's post is in the other forum, it is deleted and made again in the right
one, since Discord cannot move a post. In either forum each issue becomes a post named
"<repository> #<number> <title>"; each comment, close and reopen becomes a
message in that post, sent under the GitHub user's name and avatar. Images and
videos uploaded to GitHub are attached to the message instead of linked.

A run does not replay its event: it reconciles the whole issue with GitHub as it is now,
posting, editing and deleting whatever differs, so events that arrive together, out of order
or superseded still end in the right post. A closed issue's post is archived and the post is
renamed after the title, which a webhook cannot do, so the Kairo Discord bot does it
(DISCORD_BOT_TOKEN, with Manage Threads in both forums); it also deletes posts being moved.
The calling workflow runs one sync per issue at a time. The Discord IDs, with a digest of the GitHub text each message was made from
and the issue state last posted, are recorded in the calling repository, in the folder
DISCORD_SYNC_STATE names:

  <number>/thread.json           {"thread_id": ..., "message_id": ..., "digest": ..., "state": ...}
  <number>/comments/<id>.json    {"message_id": ..., "digest": ...}

A run only writes its own issue's folder, so runs for different issues always rebase cleanly.
"""

import hashlib
import json
import os
import re
import shutil
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

STATE = Path(os.environ["DISCORD_SYNC_STATE"]).resolve()
PUBLIC_WEBHOOK = os.environ["DISCORD_FORUM_WEBHOOK"]
if not PUBLIC_WEBHOOK:
    raise SystemExit("The Kairo Discord forum webhook secret is not set in this repository")
OWNER_WEBHOOK = os.environ["DISCORD_OWNER_WEBHOOK"]
if not OWNER_WEBHOOK:
    raise SystemExit("The Kairo Discord owner forum webhook secret is not set in this repository")
BOT_TOKEN = os.environ["DISCORD_BOT_TOKEN"]
if not BOT_TOKEN:
    raise SystemExit("The Kairo Discord bot token secret is not set in this repository")
REPO =os.environ["GITHUB_REPOSITORY"]
# The repository's own name heads its threads, since every app shares the forums.
OWNER, REPO_NAME = REPO.split("/")
NO_MENTIONS = {"parse": []}
MESSAGE_LIMIT = 2000
# Discord's per-message limits for a webhook in a server without boosts.
ATTACHMENT_LIMIT = 10 * 1024 * 1024
ATTACHMENT_COUNT = 10

# Files uploaded to GitHub, as the issue editor writes them: an <img> tag, a Markdown
# image, or a bare link on its own line (how videos are inserted).
ASSET = (r"https://(?:github\.com/user-attachments/assets/[\w-]+"
         r"|github\.com/[\w.-]+/[\w.-]+/assets/\d+/[\w-]+"
         r"|user-images\.githubusercontent\.com/[^\s)\"'<>]+)")
ASSET_MARKUP = re.compile(
    rf"<img\b[^>]*?\bsrc=\"(?P<img>{ASSET})\"[^>]*>"
    rf"|!\[[^\]]*\]\((?P<md>{ASSET})\)"
    rf"|^[ \t]*(?P<bare>{ASSET})[ \t]*$",
    re.IGNORECASE | re.MULTILINE)


def clip(value, limit):
    return value if len(value) <= limit else value[: limit - 1] + "…"


def request(method, url, label, headers, data=None):
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    while True:
        try:
            with urllib.request.urlopen(req) as response:
                body = response.read()
                return json.loads(body) if body else None
        except urllib.error.HTTPError as error:
            detail = error.read().decode()
            # Discord's rate limit says exactly how long to wait before resending.
            if error.code == 429 and label.startswith("Discord"):
                time.sleep(json.loads(detail)["retry_after"])
                continue
            raise RuntimeError(f"{method} {label} -> {error.code}: {detail}") from None


def discord(webhook, method, suffix, query, payload=None, files=()):
    """Send a webhook request. Files go as a multipart upload; a PATCH that lists the
    attachments replaces the message's old ones, so an edit without files clears them."""
    headers = {"User-Agent": f"{REPO} discord-sync"}
    data = None
    if payload is not None:
        if files or method == "PATCH":
            payload = {**payload, "attachments": [
                {"id": index, "filename": name} for index, (name, _) in enumerate(files)]}
        if files:
            boundary = uuid.uuid4().hex
            data = multipart(boundary, payload, files)
            headers["Content-Type"] = f"multipart/form-data; boundary={boundary}"
        else:
            data = json.dumps(payload).encode()
            headers["Content-Type"] = "application/json"
    # The label keeps the webhook token out of error messages.
    return request(method, webhook + suffix + query, f"Discord webhook{suffix}", headers, data)


def bot(method, thread_id, fields=None):
    """Read, change or delete a forum post through the bot; webhooks cannot change threads."""
    headers = {"Authorization": f"Bot {BOT_TOKEN}", "User-Agent": f"{REPO} discord-sync"}
    data = None
    if fields is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(fields).encode()
    return request(method, f"https://discord.com/api/v10/channels/{thread_id}", f"Discord thread {thread_id}",
                   headers, data)


def change_thread(thread_id, **fields):
    bot("PATCH", thread_id, fields)


FORUMS = {}


def forum(webhook):
    """The forum channel a webhook posts into."""
    if webhook not in FORUMS:
        FORUMS[webhook] = discord(webhook, "GET", "", "")["channel_id"]
    return FORUMS[webhook]


def webhook_for(issue, comments):
    """The owner's forum when the owner alone opened and commented on the issue, else the public one."""
    people = {item["user"]["login"].lower() for item in [issue, *comments] if item["user"]["type"] != "Bot"}
    return OWNER_WEBHOOK if people <= {OWNER.lower()} else PUBLIC_WEBHOOK


def multipart(boundary, payload, files):
    parts = [(b'Content-Disposition: form-data; name="payload_json"\r\n'
              b"Content-Type: application/json", json.dumps(payload).encode())]
    for index, (name, data) in enumerate(files):
        quoted = name.replace('"', "")
        parts.append((f'Content-Disposition: form-data; name="files[{index}]"; filename="{quoted}"\r\n'
                      "Content-Type: application/octet-stream".encode(), data))
    body = b"".join(b"--" + boundary.encode() + b"\r\n" + head + b"\r\n\r\n" + data + b"\r\n"
                    for head, data in parts)
    return body + b"--" + boundary.encode() + b"--\r\n"


def github(path):
    headers = {
        "Accept": "application/vnd.github+json",
        "Authorization": f"Bearer {os.environ['GITHUB_TOKEN']}",
        "User-Agent": f"{REPO} discord-sync",
    }
    return request("GET", f"https://api.github.com/repos/{REPO}{path}", f"GitHub {path}", headers)


def download(url):
    """A GitHub upload as (filename, bytes), or None when it is over Discord's size limit.
    The asset link redirects to a short-lived signed download."""
    req = urllib.request.Request(url, headers={"User-Agent": f"{REPO} discord-sync"})
    try:
        with urllib.request.urlopen(req) as response:
            data = response.read(ATTACHMENT_LIMIT + 1)
            name = Path(urllib.parse.urlparse(response.geturl()).path).name
    except urllib.error.HTTPError as error:
        raise RuntimeError(f"GET {url} -> {error.code}") from None
    return None if len(data) > ATTACHMENT_LIMIT else (name, data)


def with_footer(text, url):
    """Every message ends with a small link to its issue or comment; the angle brackets
    keep Discord from adding a preview card. Text too long for a message is cut to fit."""
    footer = f"-# [View on GitHub](<{url}>)"
    room = MESSAGE_LIMIT - len(footer) - 2
    if len(text) > room:
        text = text[: room - 1] + "…"
    return f"{text}\n\n{footer}" if text else footer


def render(body, url):
    """The message text and attachments for an issue or comment body. GitHub uploads become
    attachments; one over Discord's limits stays as a link."""
    files, attached = [], {}

    def attach(match):
        asset = match.group("img") or match.group("md") or match.group("bare")
        if asset not in attached:
            file = download(asset) if len(files) < ATTACHMENT_COUNT else None
            attached[asset] = file is not None
            if file:
                files.append(file)
        return "" if attached[asset] else f"<{asset}>"

    body = re.sub(r"[ \t]+\n", "\n", ASSET_MARKUP.sub(attach, body or ""))
    body = re.sub(r"\n{3,}", "\n\n", body).strip()
    return with_footer(body, url), files


def as_user(user):
    """Post under the GitHub user's own name and avatar instead of the webhook's."""
    return {"username": user["login"], "avatar_url": user["avatar_url"]} if user else {}


def new_message(user, content):
    return {"content": content, **as_user(user), "allowed_mentions": NO_MENTIONS}


def changed_message(content):
    # Clearing embeds also converts messages posted before the sync used plain text.
    return {"content": content, "embeds": [], "allowed_mentions": NO_MENTIONS}


def status(issue):
    if issue["state"] == "open":
        return "Reopened"
    reason = {"completed": "completed", "not_planned": "not planned",
              "duplicate": "a duplicate"}.get(issue.get("state_reason"))
    return f"Closed as {reason}" if reason else "Closed"




def thread_path(issue):
    return STATE / str(issue["number"]) / "thread.json"


def comment_path(issue, comment_id):
    return STATE / str(issue["number"]) / "comments" / f"{comment_id}.json"


def digest(text):
    return hashlib.sha256((text or "").encode()).hexdigest()


def read(path):
    return json.loads(path.read_text())


def write(path, data):
    path.write_text(json.dumps(data, indent=2) + "\n")


def write_new(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("x") as file:
        json.dump(data, file, indent=2)
        file.write("\n")


def thread_name(issue):
    return clip(f"{REPO_NAME} #{issue['number']} {issue['title']}", 100)


def create_thread(webhook, issue):
    """A new post for the issue's body. Its recorded state starts open, so a closed issue
    gets its closing message after its comments."""
    content, files = render(issue["body"], issue["html_url"])
    payload = {"thread_name": thread_name(issue), **new_message(issue["user"], content)}
    message = discord(webhook, "POST", "", "?wait=true", payload, files)
    thread = {"thread_id": message["channel_id"], "message_id": message["id"],
              "digest": digest(issue["body"]), "state": "open"}
    write_new(thread_path(issue), thread)
    print(f"Issue #{issue['number']} -> thread {message['channel_id']}")
    return thread


def state_actor(issue):
    """Who last closed or reopened the issue."""
    if issue["state"] == "closed":
        return issue.get("closed_by")
    reopens = [event for event in paged(f"/issues/{issue['number']}/events") if event["event"] == "reopened"]
    return reopens[-1]["actor"] if reopens else None


def reconcile(number):
    """Bring the issue's post in line with GitHub: the body, every comment, the state message,
    the archive and the name. Each Discord change is recorded as soon as it is made, so IDs of
    messages already posted survive a later failure."""
    issue = github(f"/issues/{number}")
    comments = list(paged(f"/issues/{number}/comments?sort=created"))
    path = thread_path(issue)
    webhook = webhook_for(issue, comments)
    if path.exists():
        thread = read(path)
        if bot("GET", thread["thread_id"])["parent_id"] != forum(webhook):
            # A post cannot change forums: delete it and its records, and post the issue again.
            bot("DELETE", thread["thread_id"])
            shutil.rmtree(path.parent)
            print(f"Issue #{number} thread {thread['thread_id']} deleted to move forums")
    thread = read(path) if path.exists() else create_thread(webhook, issue)
    in_thread = f"?thread_id={thread['thread_id']}"
    # An archived post refuses edits, and the final change below must unarchive in the same request
    # it renames in, so the post stays open while the run works on it.
    change_thread(thread["thread_id"], archived=False)

    if thread["digest"] != digest(issue["body"]):
        content, files = render(issue["body"], issue["html_url"])
        discord(webhook, "PATCH", f"/messages/{thread['message_id']}", in_thread, changed_message(content), files)
        thread["digest"] = digest(issue["body"])
        write(path, thread)
        print(f"Issue #{number} edited in thread {thread['thread_id']}")

    for comment in comments:
        cpath = comment_path(issue, comment["id"])
        if not cpath.exists():
            content, files = render(comment["body"], comment["html_url"])
            message = discord(webhook, "POST", "", f"?wait=true&thread_id={thread['thread_id']}",
                              new_message(comment["user"], content), files)
            write_new(cpath, {"message_id": message["id"], "digest": digest(comment["body"])})
            print(f"Comment {comment['id']} -> message {message['id']}")
            continue
        record = read(cpath)
        if record["digest"] != digest(comment["body"]):
            content, files = render(comment["body"], comment["html_url"])
            discord(webhook, "PATCH", f"/messages/{record['message_id']}", in_thread, changed_message(content), files)
            record["digest"] = digest(comment["body"])
            write(cpath, record)
            print(f"Comment {comment['id']} edited in message {record['message_id']}")

    current = {str(comment["id"]) for comment in comments}
    for cpath in sorted((path.parent / "comments").glob("*.json")):
        if cpath.stem not in current:
            message_id = read(cpath)["message_id"]
            discord(webhook, "DELETE", f"/messages/{message_id}", in_thread)
            cpath.unlink()
            print(f"Comment {cpath.stem} deleted message {message_id}")

    if thread["state"] != issue["state"]:
        payload = new_message(state_actor(issue), with_footer(status(issue), issue["html_url"]))
        discord(webhook, "POST", "", f"?wait=true&thread_id={thread['thread_id']}", payload)
        thread["state"] = issue["state"]
        write(path, thread)
        print(f"Issue #{number} {status(issue)} in thread {thread['thread_id']}")

    change_thread(thread["thread_id"], archived=issue["state"] == "closed", name=thread_name(issue))


def paged(path):
    separator = "&" if "?" in path else "?"
    page = 1
    while True:
        items = github(f"{path}{separator}per_page=100&page={page}")
        if not items:
            return
        yield from items
        page += 1


def main():
    if os.environ["GITHUB_EVENT_NAME"] == "workflow_dispatch":
        number = os.environ["SYNC_ISSUE"]
        if number:
            reconcile(int(number))
            return
        # Every issue, oldest first; one without a post gets one.
        for issue in paged("/issues?state=all&sort=created&direction=asc"):
            if "pull_request" not in issue:
                reconcile(issue["number"])
        return
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text())
    reconcile(event["issue"]["number"])


if __name__ == "__main__":
    main()
