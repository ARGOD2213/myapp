# Runs the automated tests against the dedicated connectone_test database (ConnectOne container, port 5434).
# Loads CONNECTONE_DB_PASSWORD from your user environment without printing it.
param([string]$Tests = "")
if (-not $env:CONNECTONE_DB_PASSWORD) { $env:CONNECTONE_DB_PASSWORD = [Environment]::GetEnvironmentVariable('CONNECTONE_DB_PASSWORD', 'User') }
if (-not $env:CONNECTONE_DB_PASSWORD) { throw "CONNECTONE_DB_PASSWORD is not set (see docs/LOCAL_SETUP.md)" }
Set-Location (Split-Path $PSScriptRoot -Parent)
if ($Tests) { .\mvnw.cmd -B test "-Dtest=$Tests" } else { .\mvnw.cmd -B test }
