# Windows PowerShell build script
$ErrorActionPreference = "Stop"
$RootDir = Split-Path -Parent $PSScriptRoot
Set-Location $RootDir

Write-Host "=== NSS Management Platform: Building All Components ===" -ForegroundColor Cyan

# 1. Frontend
if (Test-Path "frontend") {
    Write-Host "[INFO] Building frontend..." -ForegroundColor Cyan
    Push-Location frontend
    npm run build
    Pop-Location
}

# 2. Backend
if (Get-Command docker -ErrorAction SilentlyContinue) {
    Write-Host "[INFO] Building backend..." -ForegroundColor Cyan
    docker build -t nss-backend:latest ./backend
}

# 3. Analytics
if (Get-Command python -ErrorAction SilentlyContinue) {
    Write-Host "[INFO] Validating analytics module..." -ForegroundColor Cyan
    python -m py_compile analytics/main.py
}

Write-Host "[INFO] All components built successfully." -ForegroundColor Green
