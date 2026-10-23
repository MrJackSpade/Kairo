<#
.SYNOPSIS
Runs a native host test inside a Windows job object with a hard memory limit.

.DESCRIPTION
A test that exceeds the limit is killed by Windows instead of exhausting the workstation's
memory. These emulators should need tens of megabytes; the limit only stops a runaway. The limit
is 10 GB per process.

The script declares no parameters: every word after the program goes to it unchanged, so a test's
own options (such as --memory) never bind to the script, and each reaches the test as one
argument, quotes included.

.EXAMPLE
shared\tools\run-limited.ps1 .build\host\boot_test.exe bios .build\run "roms\game.cue" 20
#>
$ErrorActionPreference = 'Stop'
if ($args.Count -lt 1) { throw 'usage: run-limited.ps1 <program> [argument...]' }
$Program = [string]$args[0]
$Arguments = @($args | Select-Object -Skip 1 | ForEach-Object { [string]$_ })
$LimitBytes = 10GB

if (-not ('KairoJob' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class KairoJob {
    [StructLayout(LayoutKind.Sequential)] struct Basic {
        public long PerProcessUserTimeLimit, PerJobUserTimeLimit; public uint LimitFlags;
        public UIntPtr MinimumWorkingSetSize, MaximumWorkingSetSize; public uint ActiveProcessLimit;
        public UIntPtr Affinity; public uint PriorityClass, SchedulingClass; }
    [StructLayout(LayoutKind.Sequential)] struct Io {
        public ulong a, b, c, d, e, f; }
    [StructLayout(LayoutKind.Sequential)] struct Extended {
        public Basic BasicLimitInformation; public Io IoInfo;
        public UIntPtr ProcessMemoryLimit, JobMemoryLimit, PeakProcessMemoryUsed, PeakJobMemoryUsed; }
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode)] static extern IntPtr CreateJobObject(IntPtr a, string name);
    [DllImport("kernel32.dll")] static extern bool SetInformationJobObject(IntPtr job, int cls, ref Extended info, uint size);
    [DllImport("kernel32.dll")] static extern bool QueryInformationJobObject(IntPtr job, int cls, ref Extended info, uint size, IntPtr ret);
    [DllImport("kernel32.dll")] static extern bool AssignProcessToJobObject(IntPtr job, IntPtr process);
    const uint ProcessMemory = 0x100, KillOnClose = 0x2000;
    public static IntPtr Create(ulong bytes) {
        IntPtr job = CreateJobObject(IntPtr.Zero, null);
        var info = new Extended();
        info.BasicLimitInformation.LimitFlags = ProcessMemory | KillOnClose;
        info.ProcessMemoryLimit = new UIntPtr(bytes);
        if (!SetInformationJobObject(job, 9, ref info, (uint) Marshal.SizeOf(info)))
            throw new System.ComponentModel.Win32Exception();
        return job;
    }
    public static void Assign(IntPtr job, IntPtr process) {
        if (!AssignProcessToJobObject(job, process)) throw new System.ComponentModel.Win32Exception();
    }
    public static ulong Peak(IntPtr job) {
        var info = new Extended();
        QueryInformationJobObject(job, 9, ref info, (uint) Marshal.SizeOf(info), IntPtr.Zero);
        return info.PeakProcessMemoryUsed.ToUInt64();
    }
}
'@
}

$job = [KairoJob]::Create([uint64]$LimitBytes)
$start = New-Object System.Diagnostics.ProcessStartInfo
$start.FileName = (Resolve-Path $Program).Path
foreach ($argument in $Arguments) { $start.ArgumentList.Add($argument) }
$start.UseShellExecute = $false
$process = [System.Diagnostics.Process]::Start($start)
[KairoJob]::Assign($job, $process.Handle)
$process.WaitForExit()
"exit $($process.ExitCode), peak memory $([math]::Round([KairoJob]::Peak($job) / 1MB)) MB"
exit $process.ExitCode
