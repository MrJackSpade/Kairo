<#
.SYNOPSIS
Builds an app's UI test build and its tests, installs them beside any other copy of the app, runs
them on a device under Android Test Orchestrator, and pulls their logs (docs/ui-tests.md).

.DESCRIPTION
Run it from the app's repository (it tests the app whose repository holds this shared checkout):

    shared\tools\uitest.ps1 -Device <serial>

The UI test build is its own package (<applicationId>.uitest), so it never replaces or reads the
data of an installed release. The orchestrator clears its data before every test. Each test's
log.jsonl, screenshots and game pictures land in .build\uitest\<run id>\ of the app's repository,
with the runner's raw output (instrument.txt) and a summary (summary.txt). The script exits with 0
only when every selected test passed.

The test resources decrypt with the LOCAL_BUILD_TOKEN user environment variable.

.EXAMPLE
shared\tools\uitest.ps1 -Device 192.168.1.118:5555
Every test of this app.

.EXAMPLE
shared\tools\uitest.ps1 -Device 192.168.1.118:5555 -Class com.mrjackspade.kairo88.uitest.Pc88Tests
One test class; -Class takes several, comma-separated, and Class#method for one test.

.EXAMPLE
shared\tools\uitest.ps1 -Device 192.168.1.118:5555 -Filter 'MenuTests|ControllerTests'
Every test whose full name (package.Class#method) matches the regex.

.EXAMPLE
shared\tools\uitest.ps1 -Device 192.168.1.118:5555 -SkipBuild -PatienceSeconds 10
Runs the APKs already built, with a shorter bound on every wait.
#>
param(
    # The device's adb serial, such as 192.168.1.118:5555.
    [Parameter(Mandatory = $true)][string]$Device,
    # Test classes (comma-separated), or Class#method for one test; every test otherwise.
    [string]$Class,
    # A regular expression over full test names (package.Class#method); every test otherwise.
    [string]$Filter,
    # How long any one wait may last before it fails its test; 30 seconds otherwise (uitest Patience).
    [int]$PatienceSeconds,
    # Installs and runs the APKs from the last build instead of building first.
    [switch]$SkipBuild,
    # Removes the UI test build and its test APK from the device afterwards.
    [switch]$Uninstall,
    [ValidateSet('withImages', 'withoutImages')][string]$Flavor = 'withoutImages'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$module = Get-ChildItem $root -Directory | Where-Object {
    (Test-Path (Join-Path $_.FullName 'build.gradle.kts')) -and $_.Name -ne 'shared' -and
        (Select-String -Path (Join-Path $_.FullName 'build.gradle.kts') -Pattern 'com.android.application' -Quiet)
} | Select-Object -First 1 -ExpandProperty Name
if (-not $module) { throw "No application module in ${root}: run this from an app's repository" }
if ($Class -and $Filter) { throw '-Class and -Filter cannot be combined' }

function Adb { $out = & adb -s $Device @args; if ($LASTEXITCODE -ne 0) { throw "adb $args failed: $out" }; $out }

# The device must be there, and new enough for the framework's window inspection.
$sdk = [int]((Adb shell getprop ro.build.version.sdk) | Select-Object -First 1)
if ($sdk -lt 29) { throw "$Device runs Android API $sdk; the UI tests need Android 10 (API 29) or later" }

$flavored = Select-String -Path (Join-Path $root "$module\build.gradle.kts") -Pattern 'productFlavors|kairo-machine-app.gradle' -Quiet
$variant = if ($flavored) { $Flavor.Substring(0, 1).ToUpper() + $Flavor.Substring(1) + 'UiTest' } else { 'UiTest' }
if (-not $SkipBuild) {
    # The resources decrypt during the build; a shell may predate the variable.
    if (-not $env:LOCAL_BUILD_TOKEN) { $env:LOCAL_BUILD_TOKEN = [Environment]::GetEnvironmentVariable('LOCAL_BUILD_TOKEN', 'User') }
    if (-not $env:LOCAL_BUILD_TOKEN) { throw 'LOCAL_BUILD_TOKEN is not set: the UI test resources cannot be decrypted here' }
    Push-Location $root
    try {
        & .\gradlew.bat ":${module}:assemble$variant" ":${module}:assemble${variant}AndroidTest" ":${module}:uiTestTools" --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'The UI test build failed' }
    } finally { Pop-Location }
}

$outputs = Join-Path $root "$module\build\outputs\apk"
$appApk = Get-ChildItem -Recurse $outputs -Filter '*.apk' | Where-Object { $_.FullName -notmatch 'androidTest' -and $_.Name -match 'uiTest' } | Select-Object -First 1
$testOutputs = Join-Path $outputs 'androidTest'
$testApk = if (Test-Path $testOutputs) { Get-ChildItem -Recurse $testOutputs -Filter '*.apk' | Where-Object { $_.Name -match 'uiTest' } | Select-Object -First 1 }
if (-not $appApk -or -not $testApk) { throw "Missing UI test APKs under $outputs; build them first (run without -SkipBuild)" }
$tools = Get-ChildItem (Join-Path $root "$module\build\uitest-tools") -Filter '*.apk'
if (@($tools).Count -ne 2) { throw "Missing the orchestrator and test services APKs; run without -SkipBuild" }

foreach ($apk in @($tools) + $appApk + $testApk) {
    Write-Output "install $($apk.Name)"
    Adb install -r -t -g $apk.FullName | Out-Null
}

# The test APK's instrumentation, by its target: this module's UI test build.
$instrumentation = (Adb shell pm list instrumentation) | Where-Object { $_ -match "^instrumentation:([^/]+)/androidx\.test\.runner\.AndroidJUnitRunner \(target=([^)]*\.$module\.uitest)\)" }
if (@($instrumentation).Count -ne 1) { throw "Expected one UI test instrumentation for $module, found: $instrumentation" }
$testPackage = [regex]::Match($instrumentation, '^instrumentation:([^/]+)/').Groups[1].Value
$appPackage = [regex]::Match($instrumentation, '\(target=([^)]+)\)').Groups[1].Value

$runId = Get-Date -Format 'yyyyMMdd-HHmmss'
$quote = { param($value) "'" + ($value -replace "'", "'\''") + "'" }
$arguments = @('-e', 'clearPackageData', 'true', '-e', 'useTestStorageService', 'true', '-e', 'runId', $runId,
    '-e', 'targetInstrumentation', "$testPackage/androidx.test.runner.AndroidJUnitRunner")
if ($Class) { $arguments += @('-e', 'class', (& $quote $Class)) }
if ($Filter) { $arguments += @('-e', 'tests_regex', (& $quote $Filter)) }
if ($PatienceSeconds) { $arguments += @('-e', 'patienceSeconds', "$PatienceSeconds") }
Adb shell rm -rf "/sdcard/googletest/test_outputfiles/$runId" | Out-Null
Write-Output "run $runId of $appPackage on $Device"
$command = 'CLASSPATH=$(pm path androidx.test.services) app_process / androidx.test.services.shellexecutor.ShellMain ' +
    "am instrument -r -w $($arguments -join ' ') androidx.test.orchestrator/.AndroidTestOrchestrator"
$output = & adb -s $Device shell $command 2>&1
$output | Write-Output

$local = Join-Path $root ".build\uitest\$runId"
New-Item -ItemType Directory -Force $local | Out-Null
$output | Set-Content -Encoding utf8 (Join-Path $local 'instrument.txt')
Adb pull "/sdcard/googletest/test_outputfiles/$runId" $local | Out-Null

# am instrument exits 0 whatever the tests did; the result is in its status codes.
$codes = $output | Select-String '^INSTRUMENTATION_STATUS_CODE: (-?\d+)' | ForEach-Object { [int]$_.Matches[0].Groups[1].Value }
$passed = @($codes | Where-Object { $_ -eq 0 }).Count
$failed = @($codes | Where-Object { $_ -eq -2 -or $_ -eq -1 }).Count
$skipped = @($codes | Where-Object { $_ -eq -3 -or $_ -eq -4 }).Count
$crashed = [bool]($output | Select-String 'INSTRUMENTATION_FAILED|Process crashed|INSTRUMENTATION_RESULT: shortMsg')
$summary = "$appPackage on $Device, run $runId`npassed $passed, failed $failed, skipped $skipped" +
    $(if ($crashed) { "`nthe run did not finish: see instrument.txt" } else { '' })
$summary | Set-Content -Encoding utf8 (Join-Path $local 'summary.txt')
Write-Output $summary
Write-Output "logs: $local"

if ($Uninstall) {
    Adb uninstall $testPackage | Out-Null
    Adb uninstall $appPackage | Out-Null
    Write-Output "uninstalled $appPackage and $testPackage"
}
if ($crashed -or $failed -gt 0 -or $passed -eq 0) { exit 1 }
exit 0
