[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repository = Split-Path $PSScriptRoot -Parent
$runtime = Join-Path $repository 'sky-server\target\rental-local'
$statePath = Join-Path $runtime 'state.json'
if (!(Test-Path -LiteralPath $statePath)) { Write-Output 'No managed rental instance is recorded.'; return }
$state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
if ([IO.Path]::GetFullPath($state.runtime) -ne [IO.Path]::GetFullPath($runtime)) { throw 'Runtime identity does not match this repository.' }
$nginx = Join-Path $state.nginxHome 'nginx.exe'
if (Test-Path -LiteralPath (Join-Path $runtime 'nginx.pid')) {
    $nginxPid = [int](Get-Content -LiteralPath (Join-Path $runtime 'nginx.pid') -Raw).Trim()
    $nginxProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$nginxPid"
    if ($nginxProcess) {
        if ($nginxProcess.Name -ne 'nginx.exe' -or !$nginxProcess.CommandLine.Replace('\','/').Contains($runtime.Replace('\','/'))) {
            throw 'Recorded Nginx PID belongs to another process; it was not stopped.'
        }
        & $nginx -p ($runtime.Replace('\','/') + '/') -c nginx.conf -s quit
        if ($LASTEXITCODE -ne 0) { throw 'Nginx shutdown failed; check its runtime log.' }
    }
}
if ($state.backendPid) {
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$($state.backendPid)"
    $expectedJar = Join-Path $runtime 'rental-local.jar'
    if ($process -and $process.CommandLine.Contains($expectedJar) -and $process.CommandLine.Contains("--server.port=$($state.backendPort)")) {
        Stop-Process -Id $state.backendPid
    } elseif ($process) { throw 'Recorded PID now belongs to another process; it was not stopped.' }
}
for ($attempt = 0; $attempt -lt 40; $attempt++) {
    $socket = New-Object Net.Sockets.TcpClient
    try { $webListening = $socket.ConnectAsync('127.0.0.1', [int]$state.webPort).Wait(200) -and $socket.Connected }
    catch { $webListening = $false }
    finally { $socket.Dispose() }
    $backendRunning = $state.backendPid -and (Get-Process -Id $state.backendPid -ErrorAction SilentlyContinue)
    if (!$webListening -and !$backendRunning) { break }
    Start-Sleep -Milliseconds 250
}
if ($webListening -or $backendRunning) { throw 'Managed service is still stopping; the state file was kept. Retry shortly.' }
Remove-Item -LiteralPath $statePath
Write-Output 'Managed rental web and backend stopped.'
