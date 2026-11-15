<#
.SYNOPSIS
Measures builds of an app's core against each other on a device (Kairo#192): frames a second flat
out, and whether every frame's picture and sound stayed the same.

.DESCRIPTION
Run from a Kairo app checkout as shared\tools\device-perf.ps1, in three steps:

  -Step Build   builds each -Variant ("name=CMake arguments", the first one the base the others are
                held to) as the app's library built as the shared boot test (KAIRO_DEVICE_TEST=ON,
                shared\cmake\KairoMachine.cmake), for arm64-v8a as the app's release build is (the
                NDK and CMake of shared\gradle\android-versions.properties, Release, PGO as the app
                ships it, c++_shared), prints each library's MD5, and pushes the builds, the table
                (core\test\perf-tests.json, or -Table) and the test resources it names to the device.
                It starts a new measurement: the results so far are cleared.
  -Step Round   runs the table's profile checks once with every variant, one after another, in an
                order that alternates from round to round, and adds the frames a second and each
                test's hash file's MD5 to .build\device-perf\results.json. One round per call.
  -Step Report  prints each test's frames a second per variant (mean, lowest, highest, and the
                difference from the base) and whether every variant's frames matched the base's in
                every round.

The test resources are the app's UI test resources, unpacked by shared\tools\test-core.ps1 into
.build\host-resources.

.EXAMPLE
shared\tools\device-perf.ps1 -Serial <serial> -Step Build -Variant 'base=-DKAIRO_HIDE_C_EXTERNS=OFF','hidden='
#>
param(
    [Parameter(Mandatory)][string]$Serial,
    [Parameter(Mandatory)][ValidateSet('Build', 'Round', 'Report')][string]$Step,
    # Build: each variant as "name=CMake arguments" (space-separated), the base first.
    [string[]]$Variant,
    # The speed table, relative to the app's root.
    [string]$Table = 'core\test\perf-tests.json',
    # Round: only these tests of the table.
    [string[]]$Test
)
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$module = Get-ChildItem $root -Directory | Where-Object {
    (Test-Path (Join-Path $_.FullName 'build.gradle.kts')) -and $_.Name -ne 'shared' -and
        (Select-String -Path (Join-Path $_.FullName 'build.gradle.kts') -Pattern 'com.android.application' -Quiet)
} | Select-Object -First 1 -ExpandProperty Name
if (-not $module) { throw "No application module in ${root}: run this from an app's repository" }
$state = Join-Path $root '.build\device-perf'
$variantsFile = Join-Path $state 'variants.json'
$resultsFile = Join-Path $state 'results.json'
$remote = "/data/local/tmp/kairo-perf/$module"
$adb = 'C:\bin\platform-tools\adb.exe'

function Invoke-Adb {
    $output = & $adb -s $Serial @args 2>&1
    if ($LASTEXITCODE -ne 0) { throw "adb $($args -join ' ') failed: $output" }
    $output
}

function Push-Path([string]$local, [string]$destination) {
    Invoke-Adb shell "mkdir -p '$(Split-Path -Parent $destination | ForEach-Object { $_.Replace('\', '/') })'" | Out-Null
    Invoke-Adb push $local $destination | Out-Null
}

if ($Step -eq 'Build') {
    if (-not $Variant -or $Variant.Count -lt 2) { throw 'Build needs two -Variant values or more: the base first' }
    $versions = @{}
    Get-Content (Join-Path $root 'shared\gradle\android-versions.properties') | Where-Object { $_ -match '^\s*([^#=]+)=(.*)$' } |
        ForEach-Object { $versions[$Matches[1].Trim()] = $Matches[2].Trim() }
    $sdk = [Environment]::GetEnvironmentVariable('ANDROID_HOME', 'User')
    if (-not $sdk) { throw 'ANDROID_HOME is not set: it names the Android SDK' }
    $ndk = Join-Path $sdk "ndk\$($versions.ndk)"
    if (-not (Test-Path $ndk)) { throw "The NDK $($versions.ndk) is not installed at $ndk" }
    $cmake = Join-Path $sdk "cmake\$($versions.cmake)\bin\cmake.exe"
    $ninja = (Join-Path $sdk "cmake\$($versions.cmake)\bin\ninja.exe").Replace('\', '/')
    $stl = Join-Path $ndk 'toolchains\llvm\prebuilt\windows-x86_64\sysroot\usr\lib\aarch64-linux-android\libc++_shared.so'
    $tablePath = Join-Path $root $Table
    if (-not (Test-Path $tablePath)) { throw "No speed table at $tablePath" }
    $resources = Join-Path $root '.build\host-resources'
    if (-not (Test-Path $resources)) { throw "No test resources at ${resources}: run shared\tools\test-core.ps1 once" }

    New-Item -ItemType Directory -Force $state | Out-Null
    $names = @()
    Invoke-Adb shell "rm -rf '$remote/variants'" | Out-Null
    foreach ($entry in $Variant) {
        $name, $arguments = $entry -split '=', 2
        if ($name -notmatch '^[A-Za-z0-9_-]+$') { throw "Variant name '$name' must be letters, digits, - or _" }
        $names += $name
        $build = Join-Path $state $name
        $extra = @($arguments -split ' ' | Where-Object { $_ })
        # A changed variant configures from nothing: an argument once given stays in CMake's cache.
        $stamp = Join-Path $build 'variant-arguments.txt'
        if ((Test-Path $build) -and (-not (Test-Path $stamp) -or [IO.File]::ReadAllText($stamp) -ne ($extra -join ' '))) {
            Remove-Item -Recurse -Force $build
        }
        if (-not (Test-Path (Join-Path $build 'build.ninja'))) {
            & $cmake -S (Join-Path $root "$module\src\main\cpp") -B $build -G Ninja "-DCMAKE_MAKE_PROGRAM=$ninja" `
                "-DCMAKE_TOOLCHAIN_FILE=$($ndk.Replace('\', '/'))/build/cmake/android.toolchain.cmake" `
                -DANDROID_ABI=arm64-v8a "-DANDROID_PLATFORM=android-$($versions.min_sdk)" -DANDROID_STL=c++_shared `
                -DCMAKE_BUILD_TYPE=Release -DKAIRO_PGO=ON -DKAIRO_DEVICE_TEST=ON @extra
            if ($LASTEXITCODE -ne 0) { throw "CMake configure failed for $name" }
            [IO.File]::WriteAllText($stamp, ($extra -join ' '))
        }
        & $cmake --build $build --target "${module}_test" -j 16
        if ($LASTEXITCODE -ne 0) { throw "Build failed for $name" }
        $libraries = @(Get-ChildItem $build -Recurse -Filter '*.so' | Where-Object { $_.FullName -notmatch '\\CMakeFiles\\' })
        # The code's own MD5 (.text), which two variants share when the change does not touch it: the
        # file's also differs by the build folder named in its debug information.
        $objcopy = Join-Path $ndk 'toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-objcopy.exe'
        foreach ($library in $libraries) {
            $text = Join-Path $build "$($library.BaseName).text"
            & $objcopy "--dump-section=.text=$text" $library.FullName (Join-Path $build "$($library.BaseName).objcopy-out")
            if ($LASTEXITCODE -ne 0) { throw "Could not read the code of $($library.FullName)" }
            "{0,-12} code {1} {2}" -f $name, (Get-FileHash $text -Algorithm MD5).Hash, $library.Name
        }
        $folder = "$remote/variants/$name"
        foreach ($file in @($libraries.FullName) + @((Join-Path $build "${module}_test"), $stl)) { Push-Path $file "$folder/$(Split-Path -Leaf $file)" }
        Invoke-Adb shell "chmod 755 '$folder/${module}_test'" | Out-Null
    }

    # The table with the app's own files it names, and the test resources it names, as the test
    # finds them: <app>/core/test/<table> beside <app>/shared, and <resources>/<name>.
    Invoke-Adb shell "rm -rf '$remote/app' '$remote/resources' '$remote/work'; mkdir -p '$remote/app/shared'" | Out-Null
    $tableRemote = "$remote/app/$($Table.Replace('\', '/'))"
    Push-Path $tablePath $tableRemote
    $wanted = [System.Collections.Generic.HashSet[string]]::new()
    $tests = (Get-Content $tablePath -Raw | ConvertFrom-Json).tests
    foreach ($entry in $tests) {
        $named = @()
        if ($entry.firmware -is [string]) { $named += $entry.firmware } else { $named += $entry.firmware.PSObject.Properties.Value }
        if ($entry.home) { $named += $entry.home.PSObject.Properties.Value | Where-Object { $_ -ne 'folder:' } }
        $named += $entry.media | Where-Object { $_ }
        foreach ($name in $named) { [void]$wanted.Add($name) }
    }
    foreach ($name in $wanted) {
        if ($name.StartsWith('app:')) {
            $local = Join-Path $root $name.Substring(4)
            if (-not (Test-Path -LiteralPath $local)) { throw "The table names $name, which is not in the app" }
            Push-Path $local "$remote/app/$($name.Substring(4))"
            continue
        }
        $local = Join-Path $resources $name
        if (-not (Test-Path -LiteralPath $local)) { throw "The table names $name, which is not in the test resources" }
        if (Test-Path -LiteralPath $local -PathType Container) {
            Push-Path $local "$remote/resources/$name"
            continue
        }
        # With the files the test copies beside it: those of the same name (a CUE's track data).
        $item = Get-Item -LiteralPath $local
        foreach ($sibling in Get-ChildItem -LiteralPath $item.DirectoryName -File | Where-Object { $_.BaseName -eq $item.BaseName }) {
            Push-Path $sibling.FullName "$remote/resources/$(Split-Path -Parent $name)/$($sibling.Name)".Replace('\', '/')
        }
    }
    ConvertTo-Json @{ table = $Table; variants = $names } | Set-Content $variantsFile
    '[]' | Set-Content $resultsFile
    "Built and pushed: $($names -join ', ')"
    return
}

if (-not (Test-Path $variantsFile)) { throw 'No build to measure: run -Step Build first' }
$setup = Get-Content $variantsFile -Raw | ConvertFrom-Json
$results = @(Get-Content $resultsFile -Raw | ConvertFrom-Json)

if ($Step -eq 'Round') {
    $round = 1 + (@($results | ForEach-Object { $_.round }) | Measure-Object -Maximum).Maximum
    $order = @($setup.variants)
    if ($round % 2 -eq 0) { [array]::Reverse($order) }
    $tableRemote = "$remote/app/$($setup.table.Replace('\', '/'))"
    $quoted = if ($Test) { ($Test | ForEach-Object { "'$_'" }) -join ' ' } else { '' }
    foreach ($name in $order) {
        $folder = "$remote/variants/$name"
        $work = "$remote/work/$name"
        $output = & $adb -s $Serial shell "cd '$remote' && LD_LIBRARY_PATH='$folder' '$folder/${module}_test' '$tableRemote' '$remote/resources' '$work' $quoted 2>&1" 2>&1
        $exit = $LASTEXITCODE
        $profiles = @($output | Where-Object { $_ -match '^PROFILE ' })
        if ($exit -ne 0 -or -not $profiles) { throw "Round $round, ${name}: the test failed (exit $exit):`n$($output -join "`n")" }
        foreach ($line in $profiles) {
            if ($line -notmatch '^PROFILE (.+) (\d+) frames ([\d.]+) s ([\d.]+) frames/s') { throw "Unreadable line: $line" }
            $testName = $Matches[1]
            $rate = [double]$Matches[4]
            $md5 = (Invoke-Adb shell "md5sum '$work/$testName/profile-hashes.txt'") -split ' ' | Select-Object -First 1
            # Every round's frames kept, so the report can say where two runs part.
            $kept = Join-Path $state "hashes\$round-$name-$testName.txt"
            New-Item -ItemType Directory -Force (Split-Path -Parent $kept) | Out-Null
            Invoke-Adb pull "$work/$testName/profile-hashes.txt" $kept | Out-Null
            $results += [pscustomobject]@{ round = $round; variant = $name; test = $testName; rate = $rate; hashes = $md5 }
            "round {0} {1,-12} {2,-28} {3,8:F2} frames/s  {4}" -f $round, $name, $testName, $rate, $md5
        }
    }
    ConvertTo-Json -InputObject $results -Depth 4 | Set-Content $resultsFile
    return
}

# Report
if (-not $results) { throw 'No rounds measured yet: run -Step Round' }
$base = $setup.variants[0]
$rounds = ($results | ForEach-Object { $_.round } | Sort-Object -Unique).Count
"$rounds rounds; base $base"
foreach ($group in $results | Group-Object test) {
    $baseRuns = @($group.Group | Where-Object { $_.variant -eq $base })
    $baseMean = ($baseRuns.rate | Measure-Object -Average).Average
    $reference = @($baseRuns.hashes | Sort-Object -Unique)
    $group.Name
    foreach ($name in $setup.variants) {
        $runs = @($group.Group | Where-Object { $_.variant -eq $name })
        $stats = $runs.rate | Measure-Object -Average -Minimum -Maximum
        $same = @($runs.hashes | Sort-Object -Unique)
        $exact = if ($reference.Count -ne 1) { 'base frames differ between its own runs' }
                 elseif ($same.Count -eq 1 -and $same[0] -eq $reference[0]) { 'every frame the same as the base' }
                 else { 'FRAMES DIFFER from the base' }
        "  {0,-12} {1,8:F2} frames/s ({2:F2}-{3:F2}) {4,7:+0.00;-0.00}%  {5}" -f $name, $stats.Average, $stats.Minimum,
            $stats.Maximum, (100 * ($stats.Average / $baseMean - 1)), $exact
    }
    # Where each run that differs from the base's first run parts from it: the first frame, and
    # whether its picture, its sound or both differ.
    $first = $baseRuns | Sort-Object round | Select-Object -First 1
    $firstFile = Join-Path $state "hashes\$($first.round)-$base-$($group.Name).txt"
    foreach ($run in $group.Group | Where-Object { $_.hashes -ne $first.hashes }) {
        $file = Join-Path $state "hashes\$($run.round)-$($run.variant)-$($group.Name).txt"
        if (-not (Test-Path -LiteralPath $file) -or -not (Test-Path -LiteralPath $firstFile)) { continue }
        $a = [IO.File]::ReadAllLines($firstFile); $b = [IO.File]::ReadAllLines($file)
        for ($i = 0; $i -lt [Math]::Min($a.Count, $b.Count); $i++) {
            if ($a[$i] -ne $b[$i]) {
                $x = $a[$i] -split ' '; $y = $b[$i] -split ' '
                $kind = @(if ($x[1] -ne $y[1]) { 'picture' }) + @(if ($x[2] -ne $y[2]) { 'sound' })
                $later = 0; for ($j = $i; $j -lt [Math]::Min($a.Count, $b.Count); $j++) { if ($a[$j] -ne $b[$j]) { $later++ } }
                "    round $($run.round) $($run.variant): parts from round $($first.round) $base at timed frame $i ($($kind -join ' and ')); $later frames differ"
                break
            }
        }
    }
}
