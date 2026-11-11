# Runs backend tests. Core-service tests use the dedicated connectone_test database (ConnectOne container, port 5434);
# notification-service tests use connectone_notifications_test. Loads CONNECTONE_DB_PASSWORD from your user environment.
# Usage: .\scripts\test.ps1                      (all services)
#        .\scripts\test.ps1 -Service core-service [-Tests 'Postgres*']
param([string]$Service = "", [string]$Tests = "")
if (-not $env:CONNECTONE_DB_PASSWORD) { $env:CONNECTONE_DB_PASSWORD = [Environment]::GetEnvironmentVariable('CONNECTONE_DB_PASSWORD', 'User') }
if (-not $env:CONNECTONE_DB_PASSWORD) { throw "CONNECTONE_DB_PASSWORD is not set (see docs/LOCAL_SETUP.md)" }
Set-Location (Split-Path $PSScriptRoot -Parent)
$args = @('-B')
if ($Service) { $args += @('-pl', "services/$Service", '-am') }
$args += 'test'
if ($Tests) { $args += "-Dtest=$Tests"; $args += '-Dsurefire.failIfNoSpecifiedTests=false' }
.\mvnw.cmd @args
