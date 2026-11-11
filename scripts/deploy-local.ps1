# Local deployment rehearsal against the docker-compose stack: build an immutable image tag, roll ONE service to it,
# gate on its health check + a smoke test through the gateway, and ROLL BACK automatically if the gate fails.
# This is the same sequence the CD workflow performs on AWS (new task-definition revision -> wait stable -> rollback on failure).
#
#   .\scripts\deploy-local.ps1 -Service core-service                  # tag = current git short SHA
#   .\scripts\deploy-local.ps1 -Service core-service -Tag v2          # explicit tag
#   .\scripts\deploy-local.ps1 -Service core-service -Rollback        # back to the previously deployed tag
#   .\scripts\deploy-local.ps1 -Service notification-service -SimulateBadRelease   # watch the automatic rollback
# State (which tag is live, which was live before) is kept in .deploy\<service>.json (gitignored).
param(
    [Parameter(Mandatory)][ValidateSet('core-service', 'notification-service', 'api-gateway', 'frontend')][string]$Service,
    [string]$Tag,
    [switch]$Rollback,
    [switch]$SimulateBadRelease,   # deploys with a broken datasource URL so the health gate fails and rollback is demonstrated
    [string]$GatewayUrl = 'http://localhost:18090'
)
$ErrorActionPreference = 'Continue'   # docker writes progress to stderr; real failures are checked explicitly below
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
if (-not $env:CONNECTONE_DB_PASSWORD) { $env:CONNECTONE_DB_PASSWORD = [Environment]::GetEnvironmentVariable('CONNECTONE_DB_PASSWORD', 'User') }
if (-not $env:CONNECTONE_DB_PASSWORD) { throw "CONNECTONE_DB_PASSWORD is not set" }
New-Item -ItemType Directory -Force .deploy | Out-Null
$stateFile = ".deploy\$Service.json"
$state = if (Test-Path $stateFile) { Get-Content $stateFile | ConvertFrom-Json } else { [pscustomobject]@{ current = 'local'; previous = $null } }

function Wait-Healthy($svc, $seconds) {
    $cid = docker compose ps -q $svc
    $started = Get-Date
    for ($i = 0; $i -lt ($seconds / 3); $i++) {
        $h = docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $cid 2>$null
        if ($h -eq 'healthy' -or ($h -eq 'running' -and $svc -eq 'frontend')) { Write-Host ("   {0} {1} after {2:n0}s" -f $svc, $h, ((Get-Date) - $started).TotalSeconds); return $true }
        $restarts = [int](docker inspect --format '{{.RestartCount}}' $cid 2>$null)
        if ($restarts -ge 3) { Write-Host "   $svc is crash-looping ($restarts restarts): failing the gate early" -ForegroundColor Yellow; return $false }
        Start-Sleep 3
    }
    return $false
}
function Smoke-Test {
    try {
        $plans = Invoke-RestMethod "$GatewayUrl/api/v1/recharge-plans" -TimeoutSec 10
        $status = Invoke-RestMethod "$GatewayUrl/api/v1/system/status" -TimeoutSec 10
        return (@($plans).Count -gt 0 -and $status.status -eq 'UP')
    } catch { Write-Host "   smoke test error: $($_.Exception.Message)" -ForegroundColor Yellow; return $false }
}
function Roll-To($tag, [switch]$bad) {
    $env:TAG = $tag
    $broken = 'jdbc:postgresql://postgres:5432/this_database_does_not_exist'
    if ($bad -and $Service -eq 'core-service') { $env:CORE_DB_URL = $broken }
    if ($bad -and $Service -eq 'notification-service') { $env:NOTIFICATION_DB_URL = $broken }
    docker compose up -d --no-deps $Service 2>&1 | Out-Null
    Remove-Item Env:CORE_DB_URL, Env:NOTIFICATION_DB_URL -ErrorAction SilentlyContinue
}

if ($Rollback) {
    if (-not $state.previous) { throw "No previous release recorded for $Service" }
    $target = $state.previous
    Write-Host "Rolling ${Service} back: $($state.current) -> $target"
    Roll-To $target
    if (-not (Wait-Healthy $Service 420)) { throw "rollback target $target is not healthy" }
    $state = [pscustomobject]@{ current = $target; previous = $state.current }
    $state | ConvertTo-Json | Set-Content $stateFile
    Write-Host "Rollback complete. ${Service} is on $target"; return
}

if (-not $Tag) { $Tag = (git rev-parse --short HEAD).Trim() }
Write-Host "1/4 Building connectone/${Service}:$Tag (immutable tag)"
$env:TAG = $Tag
docker compose build $Service 2>&1 | Select-Object -Last 1
Write-Host "2/4 Rolling ${Service}: $($state.current) -> $Tag"
Roll-To $Tag -bad:$SimulateBadRelease
Write-Host "3/4 Health gate (container readiness, up to 7 min on a small VM)"
$healthy = Wait-Healthy $Service 420
$smoke = $healthy -and (Smoke-Test)
if ($healthy -and $smoke) {
    $state = [pscustomobject]@{ current = $Tag; previous = $state.current }
    $state | ConvertTo-Json | Set-Content $stateFile
    Write-Host "4/4 Smoke test via gateway passed. DEPLOYED: ${Service} is on $Tag (previous: $($state.previous))" -ForegroundColor Green
} else {
    Write-Host "4/4 GATE FAILED (healthy=$healthy smoke=$smoke): rolling back to $($state.current)" -ForegroundColor Red
    Roll-To $state.current
    if (-not (Wait-Healthy $Service 420)) { throw "ROLLBACK ALSO UNHEALTHY: investigate with docker compose logs $Service" }
    Write-Host "Rolled back. ${Service} is on $($state.current). Inspect the failure: docker compose logs $Service" -ForegroundColor Yellow
    exit 1
}
