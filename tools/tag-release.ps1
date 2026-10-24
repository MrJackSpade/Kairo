<#
.SYNOPSIS
Tags every Kairo app with the release version in shared/release-version and pushes the tags.

.DESCRIPTION
Every Kairo app releases the same version. This reads it from the release-version file beside this
script's shared checkout, and for every app in docs/templates/apps.json checked out beside this one
(in -Workspace, by default the folder holding this app's repository) it checks, before tagging any:
origin/main pins a shared commit whose release-version is the same, origin/main has
docs/releases/v<version>.md; an app that has the tag already is left as it is. Then it tags each app's origin/main
and pushes the tag, which starts that app's release build. The apps' pre-push hooks and release
workflows refuse any other version (tools/check-release-version.sh).

.EXAMPLE
shared\tools\tag-release.ps1
#>
param(
    # The folder holding the app repositories; the one around this app's repository otherwise.
    [string]$Workspace
)
$ErrorActionPreference = 'Stop'
$shared = Split-Path -Parent $PSScriptRoot
if (-not $Workspace) { $Workspace = Split-Path -Parent (Split-Path -Parent $shared) }
$version = (Get-Content (Join-Path $shared 'release-version') -Raw).Trim()
if (-not $version) { throw 'release-version is empty' }
$tag = "v$version"
$registry = Get-Content (Join-Path $shared 'docs\templates\apps.json') -Raw | ConvertFrom-Json
# Git for Windows' sh runs the shared shell checks; PowerShell has no sh of its own.
$sh = Join-Path (Split-Path (Split-Path (Get-Command git.exe).Source)) 'bin\sh.exe'
if (-not (Test-Path $sh)) { throw "Git's sh.exe was not found at $sh" }

function Invoke-Git([string]$repo) {
    $output = & git.exe -C $repo @args 2>&1
    if ($LASTEXITCODE -ne 0) { throw "git -C $repo $($args -join ' ') failed: $output" }
    return $output
}

$apps = @()
$problems = @()
foreach ($app in $registry.apps) {
    $repo = Join-Path $Workspace $app.repo
    if (-not (Test-Path $repo)) { $problems += "$($app.repo): not checked out in $Workspace"; continue }
    Invoke-Git $repo fetch -q origin main --tags | Out-Null
    $pin = (Invoke-Git $repo rev-parse 'origin/main:shared').Trim()
    Invoke-Git (Join-Path $repo 'shared') fetch -q origin | Out-Null
    $pinned = (& git -C (Join-Path $repo 'shared') show "${pin}:release-version" 2>$null)
    if ($LASTEXITCODE -ne 0) { $problems += "$($app.repo): its pinned shared $pin has no release-version" }
    elseif ($pinned.Trim() -ne $version) { $problems += "$($app.repo): its pinned shared $pin releases $($pinned.Trim()), not $version" }
    & git -C $repo cat-file -e "origin/main:docs/releases/$tag.md" 2>$null
    if ($LASTEXITCODE -ne 0) { $problems += "$($app.repo): origin/main has no docs/releases/$tag.md" }
    # The tag build refuses workflows that call shared at another commit than the pin.
    Push-Location $repo
    $pins = & $sh shared/tools/check-workflow-pins.sh origin/main 2>&1
    $pinsFailed = $LASTEXITCODE -ne 0
    Pop-Location
    if ($pinsFailed) { $problems += "$($app.repo): $($pins -join '; ')" }
    # The tag build also refuses a README whose shared blocks are stale (check-app.sh). This checks
    # the working tree, so it must match origin/main, as the pull before tagging leaves it.
    Push-Location $repo
    $docs = & python shared/tools/docs.py . --check 2>&1
    $docsFailed = $LASTEXITCODE -ne 0
    Pop-Location
    if ($docsFailed) { $problems += "$($app.repo): $($docs -join '; ')" }
    # An app tagged already (by an earlier run that stopped partway) keeps its tag.
    & git -C $repo rev-parse -q --verify "refs/tags/$tag" *> $null
    if ($LASTEXITCODE -eq 0) { Write-Output "$($app.repo): $tag already tagged"; continue }
    $apps += $repo
}
if ($problems) { throw "Not tagging $tag; fix these first:`n  $($problems -join "`n  ")" }

foreach ($repo in $apps) {
    $name = Split-Path -Leaf $repo
    Invoke-Git $repo tag -a $tag origin/main -m "$name $version" | Out-Null
    Invoke-Git $repo push -q origin $tag | Out-Null
    Write-Output "$name`: $tag on $((Invoke-Git $repo rev-parse --short origin/main).Trim())"
}
