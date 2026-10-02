# Saved catalog activation after APK updates

Kairo #26, RGDS, October 2 2026.

The previous store loaded a saved checksum from an older APK marker but kept
the corresponding file inactive. An unchanged server revision then returned
false forever. The baseline device fixture reproduced this with the error
`APK update left unchanged downloaded catalog inactive`.

The store now treats a prior APK's file as a pending candidate, not an active
known revision. On the catalog worker it checks the saved bytes against their
checksum and validates them using the current host's schema. Only then does it
update the marker and activate the snapshot. Activation returns true so each
host reloads its catalog layers. Compatible saved data also activates offline.
No archive request is needed when server metadata is unchanged.

Corrupt bytes cannot inherit the marker checksum and are repaired from the
server. An incompatible candidate stays inactive and is not redundantly
downloaded when the server offers those same bytes. A newer compatible
revision can replace it. Failed schema validation leaves the prior file and
marker unchanged. User metadata overrides retain their existing precedence.

Run either host instrumentation with `-e snapshotActivation true`.
`SnapshotActivationFixture` passed in both apps on RGDS:

- simulate an APK timestamp change; reactivate unchanged compatible data with
  no additional archive request;
- repeated unchanged checks do not repeat full validation;
- compatible offline activation;
- failed changed-revision validation preserves active bytes and marker;
- valid changed revision activation;
- corruption repair on the same APK and after an APK change;
- incompatible schema rejection without re-fetching identical bytes, followed
  by successful activation of a compatible new revision;
- actual installed DOS host selects downloaded controller profiles, and PC98
  reports controller metadata sourced from Updated catalog.

Corruption/schema tests use isolated files and a fixture URL protocol. Both
real saved catalogs retained their original SHA-256 after actual APK updates:

- DOS catalog: `a96e570da72d0f4a597a3b47b9badb9271b3423d1b713e8e426d306b47bd1473`
- PC98 catalog: `866d0bb8f4edd0cbd8c9f6e98fcadacde83b31a577f08936a4990e609063900d`

## Validation cost

The initial 40-second DOS host wait expired while validation was still running,
not rejected. A 120-second bounded wait passed. Worker stack samples showed
`DosCatalogFields.validValue` / `invalidPath`, including JSON serialization.
PC98 samples showed repeated regex compilation in `GameCatalog.parseUpdate`.
This full revalidation is off the UI thread and occurs after an APK change;
the unchanged active-snapshot path still checks metadata only. These samples
are evidence for the separate startup-performance investigation (#15), not a
claim that validation performance is solved.

Installed/tested APK SHA-256:

- DOS: `d121e25e4377374645ee480c7cdbf9ba482ecef81faec9792b7aa27984507902`
- PC98: `b526d21dddaa814c75b37c2b596c3744be918bcf8635bba709c8738353b25782`
