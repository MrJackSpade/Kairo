# {{repo}} Privacy Policy

Last updated: October 22, 2026

{{repo}} is an Android {{system}} emulator published by {{publisher}}. This policy covers {{repo}}{{#googlePlay}}, including the paid Google Play edition and the free GitHub edition{{/googlePlay}}. For privacy questions or requests, email {{contact}}.

## Data on your device

{{repo}} reads the game files and folders{{#firmware}} and any firmware files{{/firmware}} that you choose through Android's file picker, and games that another app asks it to open. It uses file contents to identify supported games. Game files, file names, content hashes, {{#firmware}}imported firmware, {{/firmware}}{{#saveStates}}save states, {{/saveStates}}settings, and downloaded images stay on your device in the selected folder or the app's private storage. {{publisherShort}} does not receive these files, names, hashes, or settings. {{repo}} has no account, advertising, analytics, or crash reporting service.

## Optional artwork downloads

The standard edition does not bundle artwork. If you choose "Download missing images" or tap an image to request a larger version, {{repo}} makes an HTTPS request directly to {{artworkHostList}}. The requested image URL can reveal which game's artwork was requested. The image service also receives your network IP address and ordinary request information needed to deliver the image. {{repo}} does not send your game files, file names, {{#firmware}}firmware, {{/firmware}}or content hashes to these services, and {{publisherShort}} does not receive their request logs. The services control their own logging and retention. Downloaded images are stored on your device.

## Catalog updates

When {{repo}} checks for catalog updates, it requests the latest catalog over HTTPS from `raw.githubusercontent.com`, which hosts the Kairo projects' catalogs. The request sends ordinary network information, including your IP address, to that service. It does not send your game files, file names, or content hashes. The hosting service controls its own logs and retention.

## Update checks

When {{#googlePlay}}the GitHub edition of {{/googlePlay}}{{repo}} starts, it requests the list of {{repo}}'s releases over HTTPS from `api.github.com` to see whether a newer version is available{{#googlePlay}}; the Google Play edition does not, since Google Play updates it{{/googlePlay}}. The request sends ordinary network information, including your IP address, to GitHub. It does not send your game files, file names, or content hashes. GitHub controls its own logs and retention.

## Imported catalog files

When you import a catalog file, a copy of its metadata, artwork and update address is stored privately on your device. Checking installed catalogs contacts only the update addresses supplied by those installed files over HTTPS. Those servers receive your IP address and ordinary request information, but not your game files, library names or content hashes. The servers control their own logs and retention. Removing a catalog deletes its imported metadata and artwork, while keeping your games and personal overrides.

## Storage, security, and deletion

{{repo}} accesses files only after you select them with Android's file picker or another app asks it to open them. It stores app data in private app storage, does not enable Android app backup, and uses HTTPS for every network request. Local app data remains until you clear {{repo}}'s storage or uninstall it; that does not remove original game files in your chosen folder. If you use a game's Delete action and confirm, {{repo}} deletes that game's file from your chosen folder. {{publisherShort}} has no server copy of app data to delete. To ask about data sent to an image or hosting service, contact that service.

## Support correspondence

If you email {{publisherShort}} for support, we receive your email address and the information you choose to send and use it to respond. Email {{contact}} to request deletion of that correspondence.

## Changes

We may update this policy when {{repo}}'s behavior changes. The current version will be available in the app{{#googlePlay}} and through the Google Play listing{{/googlePlay}}.
