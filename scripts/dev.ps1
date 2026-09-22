# Windows PowerShell dev environment starter
param(
    [string]$Target = "all"
)

$RootDir = Split-Path -Parent $PSScriptRoot
Set-Location $RootDir

Write-Host "=== NSS Management Platform: Starting Dev Environment ===" -ForegroundColor Cyan

if (Test-Path ".env") {
    Get-Content ".env" | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not ($line.StartsWith("#")) -and ($line -match "=")) {
            $parts = $line -split "=", 2
            [System.Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim())
        }
    }
}

if ($Target -eq "infra") {
    Write-Host "[INFO] Starting database and cache infrastructure..." -ForegroundColor Cyan
    docker compose up -d postgres redis
} elseif ($Target -eq "all") {
    Write-Host "[INFO] Starting complete Docker Compose development stack..." -ForegroundColor Cyan
    docker compose up -d --build
} else {
    Write-Host "[INFO] Starting requested services: $Target..." -ForegroundColor Cyan
    docker compose up -d $Target
}

& "$PSScriptRoot/health-check.ps1" -Retries 10 -Delay 2

Write-Host "[INFO] Environment ready." -ForegroundColor Green
Write-Host "  - PostgreSQL: localhost:5432 (DB: nss)"
Write-Host "  - Redis:      localhost:6379"
Write-Host "  - Backend:    http://localhost:8080/api/v1"
