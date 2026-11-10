param([ValidateSet('capture','finish')] [string]$Action, [int]$Port, [int]$ServerPid, [string]$Created)
$ErrorActionPreference = 'Stop'
if ($Action -eq 'capture') {
    $owners = @(Get-NetTCPConnection -LocalPort $Port -State Listen | Select-Object -ExpandProperty OwningProcess -Unique)
    if ($owners.Count -ne 1) { throw 'Expected one local server process' }
    $serverProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$($owners[0])"
    if ($serverProcess.CommandLine -notmatch 'KnotServer') { throw 'The listening process is not a Fabric test server' }
    @{pid=$serverProcess.ProcessId; created=$serverProcess.CreationDate.ToUniversalTime().ToString('o')} | ConvertTo-Json -Compress
} else {
    $serverProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$ServerPid"
    if ($null -eq $serverProcess) { exit 0 }
    if ($serverProcess.CreationDate.ToUniversalTime().ToString('o') -ne $Created -or $serverProcess.CommandLine -notmatch 'KnotServer') { throw 'Server process identity changed' }
    if (Get-NetTCPConnection -OwningProcess $ServerPid -State Listen -ErrorAction SilentlyContinue) { throw 'Server still listens; refusing to terminate it' }
    Stop-Process -Id $ServerPid -Force
    Wait-Process -Id $ServerPid -Timeout 15 -ErrorAction SilentlyContinue
}
