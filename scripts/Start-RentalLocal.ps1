[CmdletBinding()]
param(
    [int]$BackendPort = 8081,
    [int]$WebPort = 8082,
    [string]$NginxHome = 'C:\AAAAA\One_Study\frontend\sky-takeout\nginx-1.20.2',
    [switch]$Build,
    [switch]$UseIdeaRedisConfiguration
)
$ErrorActionPreference = 'Stop'
if ($BackendPort -lt 1024 -or $BackendPort -gt 65535 -or $WebPort -lt 1024 -or $WebPort -gt 65535 -or $BackendPort -eq $WebPort) {
    throw 'Choose different backend/web ports in the range 1024-65535.'
}
$repository = Split-Path $PSScriptRoot -Parent
$runtime = Join-Path $repository 'sky-server\target\rental-local'
$statePath = Join-Path $runtime 'state.json'
$jar = Join-Path $repository 'sky-server\target\sky-server-1.0-SNAPSHOT.jar'
$dist = Join-Path $repository 'frontend\rental-web\dist'
$nginx = Join-Path $NginxHome 'nginx.exe'
$mimeTypes = Join-Path $NginxHome 'conf\mime.types'
if (!(Test-Path -LiteralPath $nginx) -or !(Test-Path -LiteralPath $mimeTypes)) { throw 'Nginx executable or mime.types is missing; set -NginxHome.' }

function Test-LocalPort([int]$Port) {
    $socket = New-Object Net.Sockets.TcpClient
    try { return $socket.ConnectAsync('127.0.0.1', $Port).Wait(300) -and $socket.Connected }
    catch { return $false }
    finally { $socket.Dispose() }
}

if (Test-LocalPort $WebPort) { throw "Web port $WebPort is occupied. Use Stop-RentalLocal.ps1 for this task, or select another port." }
if ($Build) {
    $maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    $mavenPath = if ($maven) { $maven.Source } else {
        (Get-ChildItem "$env:ProgramFiles\JetBrains\IntelliJ IDEA *\plugins\maven\lib\maven3\bin\mvn.cmd" -ErrorAction SilentlyContinue | Sort-Object FullName -Descending | Select-Object -First 1).FullName
    }
    if (!$mavenPath) { throw 'Maven was not found. Build the backend in IDEA first.' }
    Push-Location $repository
    try { & $mavenPath -q -pl sky-server -am -DskipTests package; if ($LASTEXITCODE -ne 0) { throw 'Backend build failed.' } }
    finally { Pop-Location }
    Push-Location (Join-Path $repository 'frontend\rental-web')
    try {
        if (!(Test-Path -LiteralPath node_modules)) { & npm.cmd ci; if ($LASTEXITCODE -ne 0) { throw 'npm ci failed.' } }
        & npm.cmd run build
        if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
    } finally { Pop-Location }
}
if (!(Test-Path -LiteralPath $jar) -or !(Test-Path -LiteralPath (Join-Path $dist 'index.html'))) { throw 'Built files are missing. Run this script with -Build or build both projects first.' }
[IO.Directory]::CreateDirectory($runtime) | Out-Null
[IO.Directory]::CreateDirectory((Join-Path $runtime 'logs')) | Out-Null
foreach ($temporaryDirectory in @('client_body_temp','proxy_temp','fastcgi_temp','scgi_temp','uwsgi_temp')) {
    [IO.Directory]::CreateDirectory((Join-Path $runtime ('temp\' + $temporaryDirectory))) | Out-Null
}
$configPath = Join-Path $runtime 'nginx.conf'
$backendPid = $null
$nginxStarted = $false
$savedRedis = $env:SKY_REDIS_PASSWORD
$savedSecret = $env:RENTAL_JWT_SECRET
try {
    if ($UseIdeaRedisConfiguration -and !$env:SKY_REDIS_PASSWORD) {
        $ideaPath = Join-Path $repository '.idea\workspace.xml'
        if (Test-Path -LiteralPath $ideaPath) {
            [xml]$workspace = Get-Content -LiteralPath $ideaPath -Raw
            $redisNode = $workspace.SelectSingleNode('//configuration[@name="SkyApplication"]//env[@name="SKY_REDIS_PASSWORD"]')
            if ($redisNode) { $env:SKY_REDIS_PASSWORD = $redisNode.GetAttribute('value') }
        }
    }
    if (!(Test-LocalPort $BackendPort)) {
        # A new ephemeral key avoids using the published development fallback for this instance.
        if (!$env:RENTAL_JWT_SECRET) {
            $bytes = New-Object byte[] 32
            $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
            try { $generator.GetBytes($bytes); $env:RENTAL_JWT_SECRET = [Convert]::ToBase64String($bytes) }
            finally { $generator.Dispose() }
        }
        $runJar = Join-Path $runtime 'rental-local.jar'
        Copy-Item -LiteralPath $jar -Destination $runJar
        $javaArguments = @('-Dfile.encoding=UTF-8', '-Duser.timezone=Asia/Shanghai', '-jar', ('"' + $runJar + '"'), "--server.port=$BackendPort", '--server.address=127.0.0.1', '--logging.level.com.sky.mapper=INFO', '--logging.level.com.sky.interceptor=ERROR', '--logging.level.com.sky.controller.admin.EmployeeController=ERROR')
        $backend = Start-Process -FilePath java -ArgumentList $javaArguments -WorkingDirectory $repository -WindowStyle Hidden -RedirectStandardOutput (Join-Path $runtime 'backend.log') -RedirectStandardError (Join-Path $runtime 'backend-error.log') -PassThru
        $backendPid = $backend.Id
    }
    $ready = $false
    for ($attempt = 0; $attempt -lt 45; $attempt++) {
        try {
            $response = Invoke-RestMethod "http://127.0.0.1:$BackendPort/rental/categories" -TimeoutSec 2
            if ($response.code -eq 1) { $ready = $true; break }
        } catch { }
        if ($backendPid -and !(Get-Process -Id $backendPid -ErrorAction SilentlyContinue)) { break }
        Start-Sleep -Milliseconds 500
    }
    if (!$ready) { throw 'Rental backend is unavailable. Check database configuration and runtime backend logs.' }
    $config = @'
pid nginx.pid;
error_log error.log warn;
events { worker_connections 256; }
http {
    include "__MIME__";
    default_type application/octet-stream;
    access_log access.log;
    sendfile on;
    server {
        listen 127.0.0.1:__WEB_PORT__;
        server_name localhost;
        root "__DIST__";
        index index.html;
        client_max_body_size 3m;
        location /rental/ {
            proxy_pass http://127.0.0.1:__BACKEND_PORT__;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
        }
        location /images/ {
            proxy_pass http://127.0.0.1:__BACKEND_PORT__;
            proxy_set_header Host $host;
        }
        location ~ (^|/)\. { deny all; }
        location / { try_files $uri $uri/ /index.html; }
    }
}
'@
    $config = $config.Replace('__MIME__', $mimeTypes.Replace('\', '/')).Replace('__DIST__', $dist.Replace('\', '/')).Replace('__WEB_PORT__', [string]$WebPort).Replace('__BACKEND_PORT__', [string]$BackendPort)
    [IO.File]::WriteAllText($configPath, $config, [Text.UTF8Encoding]::new($false))
    $prefix = $runtime.Replace('\', '/') + '/'
    & $nginx -t -p $prefix -c nginx.conf
    if ($LASTEXITCODE -ne 0) { throw 'Nginx configuration validation failed.' }
    Start-Process -FilePath $nginx -ArgumentList @('-p', ('"' + $prefix + '"'), '-c', 'nginx.conf') -WorkingDirectory $NginxHome -WindowStyle Hidden | Out-Null
    $nginxStarted = $true
    $webReady = $false
    for ($attempt = 0; $attempt -lt 20; $attempt++) {
        try { if ((Invoke-WebRequest "http://127.0.0.1:$WebPort/" -TimeoutSec 2).StatusCode -eq 200) { $webReady = $true; break } } catch { }
        Start-Sleep -Milliseconds 250
    }
    if (!$webReady) { throw 'Nginx did not become ready.' }
    @{ backendPid=$backendPid; backendPort=$BackendPort; webPort=$WebPort; nginxHome=$NginxHome; runtime=$runtime } | ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding UTF8
    Write-Output "User web:  http://localhost:$WebPort/"
    Write-Output "Admin web: http://localhost:$WebPort/admin/login"
    Write-Output "Runtime logs: $runtime"
} catch {
    if ($nginxStarted) { & $nginx -p ($runtime.Replace('\','/') + '/') -c nginx.conf -s quit }
    if ($backendPid) { Stop-Process -Id $backendPid -ErrorAction SilentlyContinue }
    throw
} finally {
    $env:SKY_REDIS_PASSWORD = $savedRedis
    $env:RENTAL_JWT_SECRET = $savedSecret
}
