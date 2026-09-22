# Docker services starter for Windows PowerShell
$ErrorActionPreference = "Stop"
$RootDir = Split-Path -Parent $PSScriptRoot
Set-Location $RootDir

Write-Host "[INFO] Starting Docker services for NSS..." -ForegroundColor Cyan
docker compose up -d --build @args
docker compose ps
