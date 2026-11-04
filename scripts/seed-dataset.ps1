# Generates the large synthetic ConnectOne dataset in the dedicated ConnectOne PostgreSQL container ONLY.
# Safety: targets container "connectone-postgres" (port 5434, database connectone_dev). It never touches ai-lab-postgres.
# Usage:   .\scripts\seed-dataset.ps1 -Reset                   (10,000 customers)
#          .\scripts\seed-dataset.ps1 -Reset -Customers 500    (quick, small)
# Login password of every synthetic account = CONNECTONE_SEED_PASSWORD (>= 12 chars; user env var or current session).
param([switch]$Reset, [int]$Customers = 10000, [string]$Base = (Get-Date -Format 'yyyy-MM-dd'))
$ErrorActionPreference = 'Continue'   # psql NOTICEs arrive on stderr; failures are detected via $LASTEXITCODE
$container = 'connectone-postgres'
$db = (docker exec $container sh -c 'echo $POSTGRES_DB').Trim()
if ($db -ne 'connectone_dev') { throw "Refusing to run: container database is '$db', expected connectone_dev" }
if (-not $Reset) { throw "This regenerates (TRUNCATEs) all ConnectOne users, customers and transactions. Re-run with -Reset to confirm." }
if (Get-NetTCPConnection -LocalPort 18080 -State Listen -ErrorAction SilentlyContinue) {
    Write-Warning "The API is running on 18080; stop it for a clean load (background jobs may touch rows)."
}
$pw = $env:CONNECTONE_SEED_PASSWORD
if (-not $pw) { $pw = [Environment]::GetEnvironmentVariable('CONNECTONE_SEED_PASSWORD', 'User') }
if (-not $pw -or $pw.Length -lt 12) { throw "Set CONNECTONE_SEED_PASSWORD (>= 12 chars) first; see docs/LOCAL_SETUP.md" }
$env:SEED_PW = $pw
$here = Split-Path $MyInvocation.MyCommand.Path
$sw = [Diagnostics.Stopwatch]::StartNew()
try {
    Get-Content "$here\dataset\large_dataset.sql" -Raw |
        docker exec -i -e SEED_PW $container sh -c "psql -U `"`$POSTGRES_USER`" -d `"`$POSTGRES_DB`" -q -v cust=$Customers -v base=$Base"
    if ($LASTEXITCODE -ne 0) { throw "Dataset generation failed" }
    Write-Host ("Generated in {0:n1}s" -f $sw.Elapsed.TotalSeconds)
    Get-Content "$here\dataset\verify_dataset.sql" -Raw |
        docker exec -i $container sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -q'
} finally { Remove-Item Env:SEED_PW -ErrorAction SilentlyContinue }
