<#
.SYNOPSIS
Builds an app's UI test build and its tests, installs them beside any other copy of the app, runs
them on a device under Android Test Orchestrator, and pulls their logs (docs/ui-tests.md).

.DESCRIPTION
The UI test build is its own package (<applicationId>.uitest), so it never replaces or reads the
data of an installed release. The orchestrator clears its data before every test. Each test's
log.jsonl, screenshots and game pictures land in .build\uitest\<run id>\ of the app's repository.

The test resources decrypt with the LOCAL_BUILD_TOKEN user environment variable.

.EXAMPLE
shared\tools\uitest.ps1 -Device 192.168.1.118:5555
shared\tools\uitest.ps1 -Device 192.168.1.118:5555 -Class com.mrjackspade.kairo88.uitest.Pc88Tests
shared\tools\uitest.ps1 -Device 192.168.1.118:5555 -PatienceSeconds 120
#>
param(
    [Parameter(Mandatory = $true)][string]$Device,
    # A test class, or class#method, to run alone; every test otherwise.
    [string]$Class,
    # How long any one wait may last before it fails its test; 30 seconds otherwise (uitest Patience).
    [int]$PatienceSeconds,
    [ValidateSet('withImages', 'withoutImages', '')][string]$Flavor = 'withoutImages'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$module = Get-ChildItem $root -Directory | Where-Object {
    (Test-Path (Join-Path $_.FullName 'build.gradle.kts')) -and $_.Name -ne 'shared' -and
        (Select-String -Path (Join-Path $_.FullName 'build.gradle.kts') -Pattern 'com.android.application' -Quiet)
} | Select-Object -First 1 -ExpandProperty Name
if (-not $module) { throw "No application module in $root" }

# The resources decrypt during the build; a tool shell may predate the variable.
if (-not $env:LOCAL_BUILD_TOKEN) { $env:LOCAL_BUILD_TOKEN = [Environment]::GetEnvironmentVariable('LOCAL_BUILD_TOKEN', 'User') }
if (-not $env:LOCAL_BUILD_TOKEN) { throw 'LOCAL_BUILD_TOKEN is not set: the UI test resources cannot be decrypted here' }

$flavored = Select-String -Path (Join-Path $root "$module\build.gradle.kts") -Pattern 'productFlavors|kairo-machine-app.gradle' -Quiet
$variant = if ($flavored) { $Flavor.Substring(0, 1).ToUpper() + $Flavor.Substring(1) + 'UiTest' } else { 'UiTest' }
Push-Location $root
try {
    & .\gradlew.bat ":${module}:assemble$variant" ":${module}:assemble${variant}AndroidTest" ":${module}:uiTestTools" --console=plain
    if ($LASTEXITCODE -ne 0) { throw "The UI test build failed" }
} finally { Pop-Location }

$outputs = Join-Path $root "$module\build\outputs\apk"
$appApk = Get-ChildItem -Recurse $outputs -Filter '*.apk' | Where-Object { $_.FullName -notmatch 'androidTest' -and $_.Name -match 'uiTest' } | Select-Object -First 1
$testApk = Get-ChildItem -Recurse (Join-Path $outputs 'androidTest') -Filter '*.apk' | Where-Object { $_.Name -match 'uiTest' } | Select-Object -First 1
if (-not $appApk -or -not $testApk) { throw "Missing UI test APKs under $outputs" }
$tools = Get-ChildItem (Join-Path $root "$module\build\uitest-tools") -Filter '*.apk'

function Adb { & adb -s $Device @args; if ($LASTEXITCODE -ne 0) { throw "adb $args failed" } }
foreach ($apk in @($tools) + $appApk + $testApk) {
    Write-Output "install $($apk.Name)"
    Adb install -r -t -g $apk.FullName | Out-Null
}

# The test APK's instrumentation, by its target: this module's UI test build.
$instrumentation = (Adb shell pm list instrumentation) | Where-Object { $_ -match "^instrumentation:([^/]+)/androidx\.test\.runner\.AndroidJUnitRunner \(target=[^)]*\.$module\.uitest\)" }
if (@($instrumentation).Count -ne 1) { throw "Expected one UI test instrumentation for $module, found: $instrumentation" }
$testPackage = [regex]::Match($instrumentation, '^instrumentation:([^/]+)/').Groups[1].Value

$runId = Get-Date -Format 'yyyyMMdd-HHmmss'
$arguments = @('-e', 'clearPackageData', 'true', '-e', 'useTestStorageService', 'true', '-e', 'runId', $runId,
    '-e', 'targetInstrumentation', "$testPackage/androidx.test.runner.AndroidJUnitRunner")
if ($Class) { $arguments += @('-e', 'class', $Class) }
if ($PatienceSeconds) { $arguments += @('-e', 'patienceSeconds', "$PatienceSeconds") }
Adb shell rm -rf "/sdcard/googletest/test_outputfiles/$runId" | Out-Null
Write-Output "run $runId on $Device"
$command = 'CLASSPATH=$(pm path androidx.test.services) app_process / androidx.test.services.shellexecutor.ShellMain ' +
    "am instrument -r -w $($arguments -join ' ') androidx.test.orchestrator/.AndroidTestOrchestrator"
& adb -s $Device shell $command
$status = $LASTEXITCODE

$local = Join-Path $root ".build\uitest\$runId"
New-Item -ItemType Directory -Force $local | Out-Null
Adb pull "/sdcard/googletest/test_outputfiles/$runId" $local | Out-Null
Write-Output "logs: $local"
exit $status
