# Starts the whole platform locally as three JVMs (+ shared infra containers):
#   api-gateway :18080  ->  core-service :18081  and  notification-service :18082
# Logs go to logs\<service>.log (gitignored). Stop with .\scripts\stop-all.ps1.
# Secrets come from your Windows user environment (CONNECTONE_DB_PASSWORD, optional CONNECTONE_JWT_SECRET); never printed.
param([switch]$Build, [string]$CacheType = "none")
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root

foreach ($v in 'CONNECTONE_DB_PASSWORD', 'CONNECTONE_JWT_SECRET') {
    if (-not [Environment]::GetEnvironmentVariable($v, 'Process')) {
        $val = [Environment]::GetEnvironmentVariable($v, 'User')
        if ($val) { [Environment]::SetEnvironmentVariable($v, $val, 'Process') }
    }
}
if (-not $env:CONNECTONE_DB_PASSWORD) { throw "CONNECTONE_DB_PASSWORD is not set (see docs/LOCAL_SETUP.md)" }
if (-not $env:CONNECTONE_JWT_SECRET) {
    # all three services must share one secret; generate one for this run (tokens die when you stop)
    $bytes = New-Object byte[] 48; (New-Object Security.Cryptography.RNGCryptoServiceProvider).GetBytes($bytes)
    $env:CONNECTONE_JWT_SECRET = [Convert]::ToBase64String($bytes)
}
$env:CONNECTONE_CACHE_TYPE = $CacheType

& "$PSScriptRoot\dev-infra.ps1"

$services = @(
    @{ name = 'core-service';         port = 18081; heap = '-Xmx384m' },
    @{ name = 'notification-service'; port = 18082; heap = '-Xmx192m' },
    @{ name = 'api-gateway';          port = 18080; mgmt = 18089; heap = '-Xmx160m' }
)
foreach ($s in $services) {
    if (Get-NetTCPConnection -LocalPort $s.port -State Listen -ErrorAction SilentlyContinue) { throw "Port $($s.port) is already in use (is the platform already running? .\scripts\stop-all.ps1)" }
}
if ($Build -or -not (Test-Path "services\core-service\target\core-service-0.1.0-SNAPSHOT.jar")) {
    & .\mvnw.cmd -B -q -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw "build failed" }
}
New-Item -ItemType Directory -Force logs | Out-Null
foreach ($s in $services) {
    $jar = "services\$($s.name)\target\$($s.name)-0.1.0-SNAPSHOT.jar"
    Start-Process java -ArgumentList @($s.heap, '-Duser.timezone=UTC', '-jar', $jar) -WorkingDirectory $root `
        -RedirectStandardOutput "logs\$($s.name).log" -RedirectStandardError "logs\$($s.name).err.log" -WindowStyle Hidden
}
foreach ($s in $services) {
    $up = $false
    for ($i = 0; $i -lt 60 -and -not $up; $i++) {
        Start-Sleep 3
        $hp = if ($s.mgmt) { $s.mgmt } else { $s.port }
        try { $up = (Invoke-RestMethod "http://localhost:$hp/actuator/health" -TimeoutSec 3).status -eq 'UP' } catch { }
    }
    if (-not $up) { throw "$($s.name) did not become healthy; see logs\$($s.name).log" }
    Write-Host ("{0,-22} UP  http://localhost:{1}" -f $s.name, $s.port)
}
Write-Host "Platform ready: use the gateway http://localhost:18080 (frontend: cd frontend; npm run dev -> http://localhost:5173)"
