<#
.SYNOPSIS
Builds a Kairo app from its checkout, signed for the devices, and optionally installs it.

.DESCRIPTION
Run from any Kairo app checkout as shared\tools\build.ps1. Builds from the checkout's real path;
no drive mapping is needed. Loads the local signing key from Kairo98\.downloads\ci-signing when
present, without printing it. Writes the full Gradle log to .build\<module>-<type>.log and prints
only the result, errors, and the APK path.

.EXAMPLE
shared\tools\build.ps1 -Install 192.168.1.118:5555
shared\tools\build.ps1 -Type release -VersionCode 1000003 -VersionName 1.0.3-dev
#>
param(
    # The app module; the checkout's only application module when omitted.
    [string]$Module,
    [ValidateSet('debug', 'release')][string]$Type = 'debug',
    [ValidateSet('withImages', 'withoutImages')][string]$Flavor = 'withImages',
    # Gradle properties <module>VersionCode and <module>VersionName, to update an installed build.
    [string]$VersionCode,
    [string]$VersionName,
    # Deletes the build's intermediate state first (build, .cxx and the shared frontend's build).
    [switch]$Clean,
    # adb serial to install the APK on with adb install -r.
    [string]$Install
)
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $Module) {
    $modules = Get-ChildItem $root -Directory | Where-Object {
        (Test-Path (Join-Path $_.FullName 'build.gradle.kts')) -and $_.Name -ne 'shared' -and
            (Select-String -Path (Join-Path $_.FullName 'build.gradle.kts') -Pattern 'com.android.application' -Quiet)
    }
    if ($modules.Count -ne 1) { throw "Name the module with -Module: found $($modules.Name -join ', ')" }
    $Module = $modules[0].Name
}

# Signing: Kairo98's debug builds and every release build use the persistent Kairo key, read from
# the local ignored files. Kairo98 reads it as KAIRO98_BETA_*, the others as KAIRO_RELEASE_*.
$signing = Join-Path (Split-Path -Parent $root) 'Kairo98\.downloads\ci-signing'
if (Test-Path (Join-Path $signing 'password.txt')) {
    $keystore = Join-Path $signing 'kairo98-beta.p12'
    $password = (Get-Content -Raw (Join-Path $signing 'password.txt')).Trim()
    $env:KAIRO98_BETA_KEYSTORE = $keystore
    $env:KAIRO98_BETA_PASSWORD = $password
    $env:KAIRO_RELEASE_KEYSTORE = $keystore
    $env:KAIRO_RELEASE_PASSWORD = $password
}

if ($Clean) {
    foreach ($dir in @("$Module\build", "$Module\.cxx", 'shared\frontend\build', 'build')) {
        $path = Join-Path $root $dir
        if (Test-Path $path) { Remove-Item -Recurse -Force $path }
    }
}

# The shared app module (gradle/kairo-machine-app.gradle) defines the artwork flavors; an app with
# its own module, as KairoDos, has build types only.
$flavored = Select-String -Path (Join-Path $root "$Module\build.gradle.kts") -Pattern 'kairo-machine-app.gradle' -SimpleMatch -Quiet
$variant = if ($flavored) { $Flavor + (Get-Culture).TextInfo.ToTitleCase($Type) } else { $Type }
$task = ":${Module}:assemble$($variant.Substring(0,1).ToUpper())$($variant.Substring(1))"
$arguments = @($task, '--console=plain')
if ($VersionCode) { $arguments += "-P${Module}VersionCode=$VersionCode" }
if ($VersionName) { $arguments += "-P${Module}VersionName=$VersionName" }

$logDir = Join-Path $root '.build'
New-Item -ItemType Directory -Force $logDir | Out-Null
$log = Join-Path $logDir "$Module-$Type.log"
Push-Location $root
try {
    & .\gradlew.bat @arguments *> $log
    $exit = $LASTEXITCODE
} finally {
    Pop-Location
}
Select-String -Path $log -CaseSensitive -Pattern '^e: |error:|What went wrong|BUILD ' -Context 0, 3 |
    Select-Object -First 20 | ForEach-Object { $_.Line; $_.Context.PostContext }
if ($exit -ne 0) { throw "Build failed (exit $exit); full log: $log" }

$apk = Get-ChildItem (Join-Path $root "$Module\build\outputs\apk") -Recurse -Filter *.apk |
    Where-Object { (-not $flavored -or $_.FullName -match [regex]::Escape($Flavor)) -and $_.FullName -match $Type } |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $apk) { throw "No $variant APK under $Module\build\outputs\apk" }
"APK: $($apk.FullName)"

if ($Install) {
    $adb = Get-ChildItem 'F:\android-sdk\platform-tools\adb.exe', 'D:\android-sdk\platform-tools\adb.exe' -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if (-not $adb) { throw 'adb not found in the Android SDK' }
    & $adb.FullName -s $Install install -r $apk.FullName
    if ($LASTEXITCODE -ne 0) { throw "Install on $Install failed" }
}
