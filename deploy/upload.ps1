# 이 PC → 서버로 코드 · 비밀 · 저장 파일 올리고 설치 (Windows PowerShell)
# 실행:  powershell -File deploy\upload.ps1 -Ip <서버 공인 IP> -Key <SSH 키 파일> [-User ubuntu]
param(
    [Parameter(Mandatory = $true)][string]$Ip,
    [Parameter(Mandatory = $true)][string]$Key,
    [string]$User = "ubuntu"
)
$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)
$tmp = Join-Path $env:TEMP "seat-forecast-upload"
if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
New-Item -ItemType Directory $tmp | Out-Null

# 1) git 이 관리하는 파일 + 아직 커밋 안 한 새 파일 (gitignore 제외)
$files = git ls-files -co --exclude-standard
# 2) git 에는 없지만 서버에 필요한 것: 인증키 · 모델 · 경로/정류장 저장 파일
$extra = "api_key.py", "board_model.pkl", "odsay_cache.json", "transit_cache.json", "trip_odsay_cache.json",
         "gg_stations.json", "daytype_cache.json", "events_cache.json", "nearby_stops.json"
$list = Join-Path $tmp "files.txt"
($files + ($extra | Where-Object { Test-Path $_ })) | Set-Content -Encoding utf8 $list
$tar = Join-Path $tmp "seat-forecast.tar.gz"
tar -czf $tar -T $list
"묶음: {0:N1} MB" -f ((Get-Item $tar).Length / 1MB)

# SSH 키 권한 (Windows OpenSSH 는 본인만 읽을 수 있어야 함)
icacls $Key /inheritance:r /grant:r "$($env:USERNAME):(R)" | Out-Null
$ssh = @("-i", $Key, "-o", "StrictHostKeyChecking=accept-new")
scp @ssh $tar "${User}@${Ip}:~/seat-forecast.tar.gz"
ssh @ssh "${User}@${Ip}" "mkdir -p ~/seat-forecast && tar -xzf ~/seat-forecast.tar.gz -C ~/seat-forecast && bash ~/seat-forecast/deploy/setup_server.sh"
