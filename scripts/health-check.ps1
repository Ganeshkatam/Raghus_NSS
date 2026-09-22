# Windows PowerShell health check script for NSS services
param(
    [int]$Retries = 15,
    [int]$Delay = 2
)

$RootDir = Split-Path -Parent $PSScriptRoot
Set-Location $RootDir

Write-Host "[INFO] Running health checks (Max retries: $Retries, Delay: ${Delay}s)..." -ForegroundColor Cyan

# 1. PostgreSQL check
Write-Host -NoNewline "[CHECK] PostgreSQL: "
$pgOk = $false
for ($i = 1; $i -le $Retries; $i++) {
    $res = docker exec nss-postgres pg_isready -U nss -d nss 2>&1
    if ($LASTEXITCODE -eq 0) {
        $pgOk = $true
        break
    }
    Start-Sleep -Seconds $Delay
}
if ($pgOk) { Write-Host "HEALTHY" -ForegroundColor Green } else { Write-Host "FAILED" -ForegroundColor Red }

# 2. Redis check
Write-Host -NoNewline "[CHECK] Redis: "
$redisOk = $false
for ($i = 1; $i -le $Retries; $i++) {
    $res = docker exec nss-redis redis-cli ping 2>&1
    if ($res -match "PONG") {
        $redisOk = $true
        break
    }
    Start-Sleep -Seconds $Delay
}
if ($redisOk) { Write-Host "HEALTHY" -ForegroundColor Green } else { Write-Host "FAILED" -ForegroundColor Red }

# 3. Backend check
Write-Host -NoNewline "[CHECK] Backend API (/api/v1): "
$backendOk = $false
for ($i = 1; $i -le $Retries; $i++) {
    try {
        $resp = Invoke-RestMethod -Uri "http://localhost:8080/api/v1" -Method Get -TimeoutSec 2 -ErrorAction Stop
        if ($resp.status -eq "ready") {
            $backendOk = $true
            break
        }
    } catch {
        Start-Sleep -Seconds $Delay
    }
}
if ($backendOk) {
    Write-Host "HEALTHY" -ForegroundColor Green
} else {
    Write-Host "WAITING/NOT RUNNING (optional if backend container is starting up)" -ForegroundColor Yellow
}

if ($pgOk -and $redisOk) {
    Write-Host "[INFO] Core infrastructure services are healthy." -ForegroundColor Green
    exit 0
} else {
    Write-Host "[ERROR] Core infrastructure health check failed." -ForegroundColor Red
    exit 1
}
