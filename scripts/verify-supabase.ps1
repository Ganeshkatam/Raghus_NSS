# PowerShell script to verify Supabase database connectivity
$RootDir = Split-Path -Parent $PSScriptRoot
Set-Location $RootDir

Write-Host "=== NSS Management Platform: Verifying Supabase Connectivity ===" -ForegroundColor Cyan

# 1. Check TCP Port 5432
Write-Host "[1/3] Testing TCP network handshake to aws-0-ap-south-1.pooler.supabase.com:5432..." -ForegroundColor Cyan
$tcpTest = Test-NetConnection -ComputerName "aws-0-ap-south-1.pooler.supabase.com" -Port 5432 -WarningAction SilentlyContinue
if ($tcpTest.TcpTestSucceeded) {
    Write-Host "  [OK] Network reachability confirmed (RemoteAddress: $($tcpTest.RemoteAddress))." -ForegroundColor Green
} else {
    Write-Host "  [FAIL] Cannot connect to Supabase port 5432. Check internet or firewall." -ForegroundColor Red
}

# 2. Check .env configuration
Write-Host "[2/3] Checking .env configuration file..." -ForegroundColor Cyan
if (Test-Path ".env") {
    Write-Host "  [OK] .env file found at $RootDir\.env" -ForegroundColor Green
} else {
    Write-Host "  [WARN] .env file not found. Copying from .env.example..." -ForegroundColor Yellow
    Copy-Item ".env.example" ".env"
}

# 3. Execute JDBC Verification via Gradle
Write-Host "[3/3] Running JDBC database connection check..." -ForegroundColor Cyan
Push-Location "$RootDir\backend"
./gradlew test --tests 'edu.college.nss.SupabaseConnectionVerificationTest' --info
Pop-Location
