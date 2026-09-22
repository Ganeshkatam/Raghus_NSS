# Windows PowerShell setup script for NSS project
$ErrorActionPreference = "Stop"
$RootDir = Split-Path -Parent $PSScriptRoot
Set-Location $RootDir

Write-Host "=== NSS Management Platform: Project Setup ===" -ForegroundColor Cyan

# 1. Environment file setup
if (-not (Test-Path ".env")) {
    if (Test-Path ".env.example") {
        Write-Host "[INFO] Creating .env from .env.example..." -ForegroundColor Green
        Copy-Item .env.example .env
    } else {
        Write-Host "[WARN] .env.example not found, skipping .env generation." -ForegroundColor Yellow
    }
} else {
    Write-Host "[INFO] Existing .env file found." -ForegroundColor Gray
}

# 2. Prerequisites inspection
Write-Host "[INFO] Checking development tools..." -ForegroundColor Cyan
foreach ($tool in @("git", "docker", "node", "npm", "java", "python")) {
    $found = Get-Command $tool -ErrorAction SilentlyContinue
    if ($found) {
        Write-Host "  [OK] $tool is available: $($found.Source)" -ForegroundColor Green
    } else {
        Write-Host "  [WARN] $tool is not installed or not in PATH." -ForegroundColor Yellow
    }
}

# 3. Frontend dependency installation
if ((Test-Path "frontend") -and (Get-Command npm -ErrorAction SilentlyContinue)) {
    Write-Host "[INFO] Installing frontend dependencies..." -ForegroundColor Cyan
    Push-Location frontend
    npm install
    Pop-Location
}

# 4. Analytics environment setup
if ((Get-Command python -ErrorAction SilentlyContinue) -and (Test-Path "analytics")) {
    Write-Host "[INFO] Setting up Python virtual environment for analytics..." -ForegroundColor Cyan
    if (-not (Test-Path "analytics/.venv")) {
        python -m venv analytics/.venv
    }
    if (Test-Path "analytics/requirements.txt") {
        if (Test-Path "analytics/.venv/Scripts/pip.exe") {
            & "analytics/.venv/Scripts/pip.exe" install -r analytics/requirements.txt
        }
    }
}

Write-Host "[INFO] Setup completed successfully." -ForegroundColor Green
