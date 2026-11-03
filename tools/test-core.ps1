<#
.SYNOPSIS
Builds an app's core into the shared PC boot test and runs it on the app's table (Kairo#115).

.DESCRIPTION
Run from a Kairo app checkout as shared\tools\test-core.ps1. It builds the app's own native
sources (<module>\src\main\cpp, whose kairo_machine_library() is the shared boot test off Android,
shared\cmake\KairoMachine.cmake) for the PC with llvm-mingw into .build\host-test, unpacks the
app's UI test resources (its games and firmware, decrypted with LOCAL_BUILD_TOKEN) into
.build\host-resources once, and runs the test on core\test\boot-tests.json through
shared\tools\run-limited.ps1. Each test writes under .build\boot\<test>. The script exits with the
test's status: 0 only when every check passed.

.EXAMPLE
shared\tools\test-core.ps1
Every test in the app's table.

.EXAMPLE
shared\tools\test-core.ps1 -Test 'Acrojet'
One test by its name.
#>
param(
    # Tests by name; every test in the table otherwise.
    [string[]]$Test,
    # Configures the build again from nothing.
    [switch]$Clean
)
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$module = Get-ChildItem $root -Directory | Where-Object {
    (Test-Path (Join-Path $_.FullName 'build.gradle.kts')) -and $_.Name -ne 'shared' -and
        (Select-String -Path (Join-Path $_.FullName 'build.gradle.kts') -Pattern 'com.android.application' -Quiet)
} | Select-Object -First 1 -ExpandProperty Name
if (-not $module) { throw "No application module in ${root}: run this from an app's repository" }
$table = Join-Path $root 'core\test\boot-tests.json'
if (-not (Test-Path $table)) { throw "No boot test table at $table" }

$toolchain = 'F:/tools/llvm-mingw-20261006-ucrt-x86_64/bin'
$cmake = 'F:\android-sdk\cmake\3.22.1\bin\cmake.exe'
$ninja = 'F:/android-sdk/cmake/3.22.1/bin/ninja.exe'
$build = Join-Path $root '.build\host-test'
$env:PATH = "$($toolchain.Replace('/', '\'));$env:PATH"

if ($Clean -and (Test-Path $build)) { Remove-Item -Recurse -Force $build }
if (-not (Test-Path (Join-Path $build 'build.ninja'))) {
    & $cmake -S (Join-Path $root "$module\src\main\cpp") -B $build -G Ninja -DCMAKE_BUILD_TYPE=Release `
        "-DCMAKE_MAKE_PROGRAM=$ninja" "-DCMAKE_C_COMPILER=$toolchain/clang.exe" "-DCMAKE_CXX_COMPILER=$toolchain/clang++.exe" `
        -DKAIRO_PGO=OFF -DKAIRO_SHADERS=OFF
    if ($LASTEXITCODE -ne 0) { throw 'CMake configure failed' }
}
& $cmake --build $build --target $module -j 16
if ($LASTEXITCODE -ne 0) { throw 'Host build failed' }

# The app's UI test resources: the same games and firmware its device tests use.
$resources = Join-Path $root '.build\host-resources'
$bundle = Join-Path $root 'uitest\TestResources.part1.dat'
# Unpacked again only when the bundle changed; archives are extracted beside themselves.
if (-not $env:LOCAL_BUILD_TOKEN) { $env:LOCAL_BUILD_TOKEN = [Environment]::GetEnvironmentVariable('LOCAL_BUILD_TOKEN', 'User') }
if (-not $env:LOCAL_BUILD_TOKEN) { throw 'LOCAL_BUILD_TOKEN is not set: the test resources cannot be decrypted here' }
& python (Join-Path $PSScriptRoot 'host_resources.py') $bundle $resources
if ($LASTEXITCODE -ne 0) { throw "Could not unpack $bundle" }

# What the app's build makes that its core reads (KairoMSX's openMSX data), which an app with
# tools/prepare_host_test.py makes for the test, under .build\host-data, for its table's "home".
$prepare = Join-Path $root 'tools\prepare_host_test.py'
if (Test-Path $prepare) {
    & python -I $prepare (Join-Path $root '.build\host-data')
    if ($LASTEXITCODE -ne 0) { throw "$prepare failed" }
}

$work = Join-Path $root '.build\boot'
New-Item -ItemType Directory -Force $work | Out-Null
& (Join-Path $PSScriptRoot 'run-limited.ps1') (Join-Path $build "$module.exe") $table $resources $work @Test
exit $LASTEXITCODE
