<#
.SYNOPSIS
Runs the UI tests of every Kairo app checked out beside this one, one app after another, and
reports each (docs/ui-tests.md).

.DESCRIPTION
The apps are those in docs/templates/apps.json whose repository sits in -Workspace (by default the
folder holding this app's repository). Each runs with its own shared\tools\uitest.ps1, so it is
tested with the shared commit it pins. A repository without UI tests (no src/androidTest) is listed
and skipped. Logs land in each repository's .build\uitest\<run id>\; the script exits with 0 only
when every app's tests passed.

.EXAMPLE
shared\tools\uitest-all.ps1 -Device 192.168.1.118:5555
Every test of every app.

.EXAMPLE
shared\tools\uitest-all.ps1 -Device 192.168.1.118:5555 -Filter 'MenuTests' -Apps Kairo88,KairoMSX
The menu crawls of two apps.
#>
param(
    [Parameter(Mandatory = $true)][string]$Device,
    # The folder holding the app repositories; the one around this app's repository otherwise.
    [string]$Workspace,
    # Only these apps (repository names); every app otherwise.
    [string[]]$Apps,
    # A regular expression over full test names, passed to each app's run.
    [string]$Filter,
    [int]$PatienceSeconds,
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
$shared = Split-Path -Parent $PSScriptRoot
if (-not $Workspace) { $Workspace = Split-Path -Parent (Split-Path -Parent $shared) }
$registry = Get-Content (Join-Path $shared 'docs\templates\apps.json') -Raw | ConvertFrom-Json
$names = $registry.apps | ForEach-Object { $_.repo }
if ($Apps) {
    $unknown = $Apps | Where-Object { $_ -notin $names }
    if ($unknown) { throw "Not Kairo apps: $($unknown -join ', ') (apps.json lists $($names -join ', '))" }
    $names = $names | Where-Object { $_ -in $Apps }
}

$results = [ordered]@{}
foreach ($name in $names) {
    $repo = Join-Path $Workspace $name
    if (-not (Test-Path $repo)) { $results[$name] = 'not checked out'; continue }
    if (-not (Get-ChildItem $repo -Directory | Where-Object { Test-Path (Join-Path $_.FullName 'src\androidTest') })) {
        $results[$name] = 'no UI tests'; continue
    }
    Write-Output "=== $name"
    $arguments = @{ Device = $Device }
    if ($Filter) { $arguments.Filter = $Filter }
    if ($PatienceSeconds) { $arguments.PatienceSeconds = $PatienceSeconds }
    if ($SkipBuild) { $arguments.SkipBuild = $true }
    # Each app's own runner, in its own process, so one app's failure cannot stop the others.
    & pwsh -NoProfile -File (Join-Path $repo 'shared\tools\uitest.ps1') @arguments
    $results[$name] = if ($LASTEXITCODE -eq 0) { 'passed' } else { 'FAILED' }
}

Write-Output ''
Write-Output 'UI tests:'
$results.GetEnumerator() | ForEach-Object { Write-Output ("  {0,-12} {1}" -f $_.Key, $_.Value) }
if ($results.Values -contains 'FAILED') { exit 1 }
exit 0
