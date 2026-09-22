# Windows PowerShell test runner
$ErrorActionPreference = "Continue"
$RootDir = Split-Path -Parent $PSScriptRoot
Set-Location $RootDir

Write-Host "=== NSS Management Platform: Running Automated Tests ===" -ForegroundColor Cyan
$failed = $false

# 1. Frontend
if (Test-Path "frontend") {
    Write-Host "[TEST] Running frontend type-check..." -ForegroundColor Cyan
    Push-Location frontend
    npx tsc --noEmit
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  [FAIL] Frontend type checking failed." -ForegroundColor Red
        $failed = $true
    } else {
        Write-Host "  [PASS] Frontend type checking passed." -ForegroundColor Green
    }
    Pop-Location
}

# 2. Analytics
if (Get-Command python -ErrorAction SilentlyContinue) {
    Write-Host "[TEST] Testing analytics service..." -ForegroundColor Cyan
    python -c "
from analytics.main import summarize_service_hours
res = summarize_service_hours([{'volunteer_id': 1, 'hours': 5}, {'volunteer_id': 1, 'hours': 3}])
assert res.loc[0, 'hours'] == 8, 'Calculation mismatch'
print('  [PASS] Analytics service hours summary test passed.')
"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  [FAIL] Analytics test failed." -ForegroundColor Red
        $failed = $true
    }
}

# 3. Backend
if (Get-Command docker -ErrorAction SilentlyContinue) {
    Write-Host "[TEST] Verifying backend compilation and tests via Docker build..." -ForegroundColor Cyan
    docker build -t nss-backend-test:latest ./backend
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  [FAIL] Backend build/test failed." -ForegroundColor Red
        $failed = $true
    } else {
        Write-Host "  [PASS] Backend compilation and build test passed." -ForegroundColor Green
    }
}

if ($failed) {
    Write-Host "[ERROR] One or more test suites failed." -ForegroundColor Red
    exit 1
} else {
    Write-Host "[INFO] All tests completed successfully." -ForegroundColor Green
    exit 0
}
