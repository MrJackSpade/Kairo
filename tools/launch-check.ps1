<#
.SYNOPSIS
Checks that an app opens: installs it with no data, launches it, and waits for its first screen
(Kairo#114, after Kairo#144 closed every app at launch on Android 12 and older).

.DESCRIPTION
The app reaches its first screen when its window shows text (first run's setup or the library).
The check fails as soon as the app's process dies or the crash log holds an entry for it, with that
entry. It runs on any adb device; release checks run it on the API 26 emulator (tools/emulator.ps1
locally, app-release.yml in CI) with an x86_64 build.

.EXAMPLE
shared\tools\launch-check.ps1 -Device emulator-5580 -Apk kairo98\build\outputs\apk\withoutImages\release\kairo98-withoutImages-release.apk -Package com.loxifi.kairo98 -Activity com.mrjackspade.kairo98.MainActivity
#>
param(
    [Parameter(Mandatory = $true)][string]$Device,
    [Parameter(Mandatory = $true)][string]$Apk,
    # The application ID, such as com.loxifi.kairo98.
    [Parameter(Mandatory = $true)][string]$Package,
    # The launcher activity's class, such as com.mrjackspade.kairo98.MainActivity.
    [Parameter(Mandatory = $true)][string]$Activity
)
$ErrorActionPreference = 'Stop'
$adb = (Get-Command adb -CommandType Application | Select-Object -First 1).Source
function Adb { $out = & $adb -s $Device @args 2>&1; if ($LASTEXITCODE -ne 0) { throw "adb $args failed: $out" }; $out }

$sdk = (Adb shell getprop ro.build.version.sdk | Select-Object -First 1).Trim()
Write-Output "launch check of $Package on $Device (Android API $sdk)"
$installed = (Adb shell pm list packages) -replace '^package:', ''
if ($installed -contains $Package) { Adb uninstall $Package | Out-Null }
Adb install -t $Apk | Out-Null
# Only the crash buffer: older Android cannot clear the main log from the shell.
Adb logcat -b crash -c | Out-Null

# Without -W: on Android 8, am start -W never returns when the app dies before its first frame.
Adb shell am start -n "$Package/$Activity" | Write-Output

function Crashes { (Adb logcat -b crash -d) | Where-Object { $_ -match [regex]::Escape($Package) -or $_ -match 'AndroidRuntime|DEBUG' } }
function Alive { [bool](Adb shell ps -A | Where-Object { $_ -match "\s$([regex]::Escape($Package))$" }) }

$dump = '/data/local/tmp/kairo-launch-check.xml'
# The process may not be up yet right after am start; once it has been, its end is a failure.
$seen = $false
while ($true) {
    $crash = Crashes
    if ($crash) { throw "$Package crashed at launch:`n$($crash -join "`n")" }
    $alive = Alive
    if ($alive) { $seen = $true }
    elseif ($seen) { throw "$Package stopped running after its launch (no crash logged)" }
    else { Start-Sleep -Seconds 1; continue }
    # The app's window, as the accessibility layer sees it: any text of the app's own means a page is up.
    & $adb -s $Device shell uiautomator dump $dump 2>&1 | Out-Null
    $xml = (& $adb -s $Device shell cat $dump 2>$null) -join ''
    $texts = [regex]::Matches($xml, "text=`"([^`"]+)`"[^>]*package=`"$([regex]::Escape($Package))`"") | ForEach-Object { $_.Groups[1].Value }
    if ($texts) {
        # Once more after the screen is up: a crash in what startup posts after its first frame.
        $crash = Crashes
        if ($crash) { throw "$Package crashed at launch:`n$($crash -join "`n")" }
        if (-not (Alive)) { throw "$Package died after showing its first screen" }
        Write-Output "first screen: $(($texts | Select-Object -First 3) -join ' | ')"
        break
    }
    Start-Sleep -Seconds 1
}
Adb uninstall $Package | Out-Null
Write-Output "launch check passed"
