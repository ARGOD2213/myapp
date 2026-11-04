# Creates the 64 training tickets as GitHub Issues (labels: level-N, module, priority).
# Prereq: `gh auth login`. Usage:  .\scripts\create-github-issues.ps1 [-Repo ARGOD2213/myapp] [-Level 1]
# Re-running is safe: tickets whose title already exists are skipped.
param([string]$Repo = "ARGOD2213/myapp", [int]$Level = 0)
$ErrorActionPreference = 'Stop'
$csv = Import-Csv (Join-Path (Split-Path $PSScriptRoot -Parent) "docs\tickets\jira-import.csv")
$existing = gh issue list --repo $Repo --state all --limit 500 --json title | ConvertFrom-Json | ForEach-Object { $_.title }
$colors = @{ 'level-1'='c5def5'; 'level-2'='bfd4f2'; 'level-3'='d93f0b'; 'level-4'='0e8a16'; 'level-5'='fbca04'; 'level-6'='5319e7' }
foreach ($l in $colors.Keys) { gh label create $l --repo $Repo --color $colors[$l] --force | Out-Null }
foreach ($row in $csv) {
    $lvl = [int](($row.Labels -split ' ')[0] -replace 'level-', '')
    if ($Level -gt 0 -and $lvl -ne $Level) { continue }
    $title = "$($row.'Issue Key'): $($row.Summary)"
    if ($existing -contains $title) { continue }
    $module = ($row.Labels -split ' ')[1]
    gh label create $module --repo $Repo --color ededed --force | Out-Null
    $prio = "priority: " + $row.Priority.ToLower()
    gh label create $prio --repo $Repo --color ededed --force | Out-Null
    $body = ($row.Description -replace '\*(.+?)\*', '**$1**') + "`n`n_Type: $($row.'Issue Type')_"
    gh issue create --repo $Repo --title $title --body $body --label "level-$lvl,$module,$prio" | Out-Null
    Write-Host "created $title"
}
