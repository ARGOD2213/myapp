# Starts the ConnectOne API against the dedicated PostgreSQL container (port 5434).
# Loads CONNECTONE_DB_PASSWORD from your Windows user environment WITHOUT printing it.
param([int]$Port = 18080, [string]$Profile = "default")

if (-not $env:CONNECTONE_DB_PASSWORD) {
    $env:CONNECTONE_DB_PASSWORD = [Environment]::GetEnvironmentVariable('CONNECTONE_DB_PASSWORD', 'User')
}
if (-not $env:CONNECTONE_DB_PASSWORD) { throw "CONNECTONE_DB_PASSWORD is not set (see docs/LOCAL_SETUP.md)" }
if (-not $env:CONNECTONE_JWT_SECRET) {
    $env:CONNECTONE_JWT_SECRET = [Environment]::GetEnvironmentVariable('CONNECTONE_JWT_SECRET', 'User')
}
if (-not $env:CONNECTONE_JWT_SECRET) {
    # Dev-only: random per-run secret; tokens become invalid after restart.
    $bytes = New-Object byte[] 48
    [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    $env:CONNECTONE_JWT_SECRET = [Convert]::ToBase64String($bytes)
}
$busy = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if ($busy) { throw "Port $Port is already in use; pass -Port <free port>" }
Set-Location (Split-Path $PSScriptRoot -Parent)
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=$Profile" "-Dspring-boot.run.arguments=--server.port=$Port"
