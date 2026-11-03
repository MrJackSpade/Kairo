<#
.SYNOPSIS
Starts the Android emulator the Kairo test runs use (Kairo#114) and prints its adb serial.

.DESCRIPTION
Creates the AVD when it does not exist yet: the google_apis x86_64 image of the chosen Android API
(the apps' minSdk by default, so every run covers the oldest Android the apps support) with the
RP6's screen, 1920x1080 landscape at 400 dpi. It boots it headless with software GL (this
workstation's remote session has no host GL) on a fixed console port, so the serial is always
emulator-<Port>, and waits until Android has finished booting. Every boot starts from a clean disk
(-read-only), so several emulators on different ports can run at once. If the emulator exits before that,
the script fails with the end of its log.

Apps run on it as x86_64 builds (build.ps1 -Abi x86_64; uitest.ps1 -Emulator): the emulator images
for Android 10 and older cannot run arm64 code.

The SDK is ANDROID_HOME. The image is installed with sdkmanager when it is missing.

.EXAMPLE
shared\tools\emulator.ps1
The minSdk emulator; prints emulator-5580.

.EXAMPLE
shared\tools\emulator.ps1 -Api 34 -Port 5582
A second emulator on Android 14, for a test that needs a newer Android.

.EXAMPLE
shared\tools\emulator.ps1 -Stop
Shuts the emulator on the default port down.
#>
param(
    # The Android API of the image; the apps' minSdk otherwise.
    [int]$Api,
    # The emulator's console port (even, 5554-5682); its adb serial is emulator-<Port>.
    [ValidateRange(5554, 5682)][int]$Port = 5580,
    # Shuts the emulator on -Port down instead of starting one.
    [switch]$Stop,
    # Shows the emulator's window instead of running headless.
    [switch]$Window
)
$ErrorActionPreference = 'Stop'
if ($Port % 2 -ne 0) { throw "-Port must be even (the console port; adb uses the next one)" }
$serial = "emulator-$Port"

$sdkRoot = $env:ANDROID_HOME
if (-not $sdkRoot) { $sdkRoot = [Environment]::GetEnvironmentVariable('ANDROID_HOME', 'User') }
if (-not $sdkRoot -or -not (Test-Path $sdkRoot)) { throw "ANDROID_HOME is not set to the Android SDK" }
$adb = Join-Path $sdkRoot 'platform-tools\adb.exe'
$emulator = Join-Path $sdkRoot 'emulator\emulator.exe'

if ($Stop) {
    & $adb -s $serial emu kill
    if ($LASTEXITCODE -ne 0) { throw "No emulator answered on $serial" }
    # emu kill returns at once; a start right after must not find the old one still listed.
    while ((& $adb devices) -match "^$serial\s") { Start-Sleep -Milliseconds 500 }
    Write-Output "stopped $serial"
    exit 0
}

if (-not $Api) {
    $properties = Join-Path $PSScriptRoot '..\gradle\android-versions.properties'
    $Api = [int]((Get-Content $properties | Where-Object { $_ -match '^min_sdk=' }) -replace '^min_sdk=', '')
}
$image = "system-images;android-$Api;google_apis;x86_64"
$name = "kairo-test-api$Api"

$cmdline = Get-ChildItem (Join-Path $sdkRoot 'cmdline-tools\*\bin\sdkmanager.bat') | Select-Object -First 1
if (-not $cmdline) { throw "No cmdline-tools in $sdkRoot (sdkmanager and avdmanager are needed)" }
$bin = $cmdline.DirectoryName
$imageDir = Join-Path $sdkRoot "system-images\android-$Api\google_apis\x86_64"
if (-not (Test-Path (Join-Path $imageDir 'system.img'))) {
    Write-Output "install $image"
    # The licences were accepted for this SDK; sdkmanager asks again per package.
    'y', 'y', 'y' | & (Join-Path $bin 'sdkmanager.bat') "--sdk_root=$sdkRoot" $image
    if ($LASTEXITCODE -ne 0) { throw "sdkmanager could not install $image" }
}

$avdHome = if ($env:ANDROID_AVD_HOME) { $env:ANDROID_AVD_HOME } else { Join-Path $env:USERPROFILE '.android\avd' }
$config = Join-Path $avdHome "$name.avd\config.ini"
if (-not (Test-Path $config)) {
    Write-Output "create $name"
    'no' | & (Join-Path $bin 'avdmanager.bat') create avd --name $name --package $image --abi google_apis/x86_64 --force
    if ($LASTEXITCODE -ne 0) { throw "avdmanager could not create $name" }
    # The RP6's screen: 1920x1080 landscape. 4 GB, as the RP6's apps see far more than they use.
    $settings = [ordered]@{
        'hw.lcd.width' = '1920'; 'hw.lcd.height' = '1080'; 'hw.lcd.density' = '400'
        'hw.initialOrientation' = 'landscape'; 'hw.ramSize' = '4096'; 'vm.heapSize' = '512'
        'hw.keyboard' = 'yes'; 'hw.gpu.enabled' = 'yes'; 'hw.gpu.mode' = 'swiftshader_indirect'
        'disk.dataPartition.size' = '8G'
    }
    $lines = Get-Content $config | Where-Object { $key = ($_ -split '=', 2)[0].Trim(); -not $settings.Contains($key) }
    $lines += $settings.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }
    Set-Content -Path $config -Value $lines -Encoding ascii
}

$running = (& $adb devices) -match "^$serial\s+device"
if ($running) {
    $sdk = (& $adb -s $serial shell getprop ro.build.version.sdk).Trim()
    if ($sdk -ne "$Api") { throw "$serial is already running API $sdk, not $Api; stop it first (-Stop)" }
    Write-Output $serial
    exit 0
}

$log = Join-Path ([IO.Path]::GetTempPath()) "kairo-emulator-$Port.log"
# -read-only: every boot starts from the same clean disk, and several emulators (one per port) can
# run the same AVD at once, such as a launch check beside a running suite.
# GLESDynamicVersion: the emulator (37.2) leaves it off, which caps an Android 8 guest at OpenGL ES
# 2.0, and every core presents through an ES 3 context ("Could not create an OpenGL ES 3 context").
$arguments = @('-avd', $name, '-port', "$Port", '-read-only', '-no-snapshot', '-no-audio', '-no-boot-anim',
    '-gpu', 'swiftshader_indirect', '-feature', 'GLESDynamicVersion')
if (-not $Window) { $arguments += '-no-window' }
Write-Output "boot $name as $serial (log $log)"
$process = Start-Process -FilePath $emulator -ArgumentList $arguments -PassThru -WindowStyle Hidden `
    -RedirectStandardOutput $log -RedirectStandardError "$log.err"

# As long as booting takes: the emulator's own exit is the failure that ends the wait.
while ($true) {
    if ($process.HasExited) {
        $tail = (Get-Content $log -Tail 15 -ErrorAction SilentlyContinue) -join "`n"
        throw "The emulator exited (code $($process.ExitCode)) before Android booted:`n$tail"
    }
    $booted = & $adb -s $serial shell getprop sys.boot_completed 2>$null
    if ("$booted".Trim() -eq '1') { break }
    Start-Sleep -Seconds 2
}
# No animations to wait out in a test.
foreach ($setting in 'window_animation_scale', 'transition_animation_scale', 'animator_duration_scale') {
    & $adb -s $serial shell settings put global $setting 0 | Out-Null
}
Write-Output $serial
