# External frontend setup for {{repo}}

[← {{repo}}](../README.md) · [All documentation]({{frontends.docsIndex}})

{{frontends.intro}} Choosing **Library** from a frontend-launched game closes {{repo}} and returns to the frontend.

## Setup guides

- [LaunchBox for Android](#launchbox-for-android)
- [ES-DE](#es-de)
- [iiSU](#iisu)
- [Beacon](#beacon)

## LaunchBox for Android

{{frontends.launchboxImport}} Open that platform, tap the **top-right three-dot menu → Emulator Settings**, and choose **Custom Emulator** as the default emulator (without “With Code”). Enter:

| Setting | Value |
| --- | --- |
| Custom Emulator Package Name | `{{package}}` |
| Custom Emulator Activity Name | `{{package}}.Launch` |
| Custom Emulator ROM Path Key | `ROM` |

Turn **Extract ROM Archives** off. No separate launch command is needed.

## ES-DE

Add this Android package rule to ES-DE's custom `es_find_rules.xml`:

```xml
<emulator name="{{emulatorId}}">
  <rule type="androidpackage">
    <entry>{{package}}/{{package}}.Launch</entry>
  </rule>
</emulator>
```

In the {{frontends.esdeSystem}} system's custom `es_systems.xml` configuration, add this command and select it as the emulator:

```xml
<command label="{{repo}}">%EMULATOR_{{emulatorId}}% %ACTION%=android.intent.action.VIEW %DATA%=%ROMPROVIDER%</command>
```

See the [ES-DE Android configuration guide](https://gitlab.com/es-de/emulationstation-de/-/blob/master/INSTALL.md) for custom file locations and system override syntax. Pass the original {{frontends.gameFile}} or ZIP to {{repo}}.

## iiSU

iiSU reads its emulator list from two files in `Android/media/com.iisulauncher/iiSULauncher/Emuladores/` on shared storage. If your file manager cannot open `Android/media`, use Android's built-in Files app. Back up both files before editing them.

In `emuladores.json`, find the console whose `shortName` is `{{frontends.iisuShortName}}` and add this object to its `emulators` array:

```json
{
  "id": "{{emulatorId}}",
  "name": "{{repo}} (Standalone)",
  "routeType": "uri",
  "commands": [
    {
      "description": "{{repo}}",
      "command": "{{package}}/{{package}}.Launch -a android.intent.action.VIEW -d %ROM_URI%"
    }
  ],
  "packages": [
    "{{package}}"
  ]
}
```

Add this object to the array in `supported_emulators.json`:

```json
{
  "name": "{{emulatorId}}",
  "packages": [
    "{{package}}"
  ]
}
```

Put your games in the `{{frontends.iisuShortName}}` folder of your iiSU ROM folder. Select the {{frontends.iisuConsole}} console, open **Quick Settings → Console Settings → Edit Console Settings**, and choose **{{repo}} (Standalone)** as the emulator. An iiSU update can replace these files; add the entries again if {{repo}} disappears from the list.

## Beacon

Open **Settings** and add a platform:

| Setting | Value |
| --- | --- |
| Platform type | **Custom** |
| Name / Short name | Any, for example `{{frontends.beaconName}}` / `{{frontends.beaconShortName}}` |
| Player app | **{{repo}}** |
| ROMs folder | Your {{system}} game folder |

Scroll down, open **Advanced**, turn on **Use custom launch**, accept the warning, and enter this **am start command**:

```text
am start -n {{package}}/{{package}}.Launch -a android.intent.action.VIEW -d {file_uri}
```

Leave **File handling** at **Default** and save. Without the custom launch command, Beacon opens {{repo}}'s library instead of the game.
{{#frontends.extra}}

{{frontends.extra}}
{{/frontends.extra}}

The app does not register a generic Android “Open with” file handler. Select games inside the app or use the explicit component configured above; APKs and unrelated documents should not offer the emulator as a handler.
