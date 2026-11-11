# Stops the three ConnectOne JVMs started by start-all.ps1 (infrastructure containers keep running).
Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
    Where-Object { $_.CommandLine -match 'core-service-.*\.jar|notification-service-.*\.jar|api-gateway-.*\.jar|ConnectOneApplication' } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force; Write-Host "stopped java pid $($_.ProcessId)" }
