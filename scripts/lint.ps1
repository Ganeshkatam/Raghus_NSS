# Windows PowerShell linting script
$ErrorActionPreference = "Continue"
$RootDir = Split-Path -Parent $PSScriptRoot
Set-Location $RootDir

Write-Host "=== NSS Management Platform: Running Code Quality Checks ===" -ForegroundColor Cyan
$failed = $false

# 1. Environment secrets check
Write-Host "[LINT] Checking for tracked environment secrets..." -ForegroundColor Cyan
$trackedSecrets = git ls-files | Where-Object { $_ -match '(^|/)\.env($|\..*)' -and $_ -notmatch '\.env\.example$' }
if ($trackedSecrets) {
    Write-Host "  [FAIL] Tracked .env file detected: $trackedSecrets" -ForegroundColor Red
    $failed = $true
} else {
    Write-Host "  [PASS] No tracked .env files found." -ForegroundColor Green
}

# 2. Frontend
if (Test-Path "frontend") {
    Write-Host "[LINT] Checking frontend TypeScript..." -ForegroundColor Cyan
    Push-Location frontend
    npx tsc --noEmit
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  [FAIL] Frontend TypeScript errors found." -ForegroundColor Red
        $failed = $true
    } else {
        Write-Host "  [PASS] Frontend TypeScript check passed." -ForegroundColor Green
    }
    Pop-Location
}

# 3. Analytics
if (Get-Command python -ErrorAction SilentlyContinue) {
    Write-Host "[LINT] Checking Python syntax..." -ForegroundColor Cyan
    python -m py_compile analytics/main.py
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  [FAIL] Python syntax error detected." -ForegroundColor Red
        $failed = $true
    } else {
        Write-Host "  [PASS] Python syntax check passed." -ForegroundColor Green
    }
}

if ($failed) {
    Write-Host "[ERROR] Lint or quality checks failed." -ForegroundColor Red
    exit 1
} else {
    Write-Host "[INFO] All lint and quality checks passed." -ForegroundColor Green
    exit 0
}
