# Upload code, secrets and caches from this PC to the server, then install (Windows PowerShell)
# Usage:  powershell -ExecutionPolicy Bypass -File deploy\upload.ps1 -Ip <server public IP> -Key <SSH key file> [-User ubuntu]
param(
    [Parameter(Mandatory = $true)][string]$Ip,
    [Parameter(Mandatory = $true)][string]$Key,
    [string]$User = "ubuntu"
)
$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)
$tar = Join-Path (Get-Location) "deploy\seat-forecast.tar.gz"
python -X utf8 deploy\pack.py $tar
if ($LASTEXITCODE -ne 0) { throw "pack failed" }

icacls $Key /inheritance:r /grant:r "$($env:USERNAME):(R)" | Out-Null
$ssh = @("-i", $Key, "-o", "StrictHostKeyChecking=accept-new")
scp @ssh $tar "${User}@${Ip}:~/seat-forecast.tar.gz"
ssh @ssh "${User}@${Ip}" "mkdir -p ~/seat-forecast && tar -xzf ~/seat-forecast.tar.gz -C ~/seat-forecast && bash ~/seat-forecast/deploy/setup_server.sh"
Remove-Item $tar
