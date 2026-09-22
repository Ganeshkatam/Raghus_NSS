# Docker services stopper for Windows PowerShell
$ErrorActionPreference = "Stop"
$RootDir = Split-Path -Parent $PSScriptRoot
Set-Location $RootDir

Write-Host "[INFO] Stopping Docker services..." -ForegroundColor Cyan
docker compose down @args
Write-Host "[INFO] Docker services stopped." -ForegroundColor Green
