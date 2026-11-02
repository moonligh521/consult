# Local Windows check. Production gate is capacity-check.sh on the Linux host.
$needMemGb = 10
$needDiskGb = 300
$needCpu = 4
$os = Get-CimInstance Win32_OperatingSystem
$memGb = [math]::Round($os.FreePhysicalMemory / 1MB, 1)
$disk = Get-PSDrive -Name (Get-Location).Drive.Name
$diskGb = [math]::Round($disk.Free / 1GB, 1)
$cpu = (Get-CimInstance Win32_Processor | Measure-Object -Property NumberOfLogicalProcessors -Sum).Sum
Write-Output "available_memory_gb=$memGb"
Write-Output "available_disk_gb=$diskGb"
Write-Output "cpu_cores=$cpu"
if ($memGb -lt $needMemGb -or $diskGb -lt $needDiskGb -or $cpu -lt $needCpu) {
    Write-Output "capacity_gate=fail"
    exit 1
}
Write-Output "capacity_gate=pass"
