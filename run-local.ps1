# run-local.ps1 — launch UniSubmit locally.
#
# Modes (set $Mode at the top):
#   "h2"       — H2 in-memory DB, no external DB needed (default)
#   "supabase" — Remote Supabase PostgreSQL (requires network access)
#
# Usage:  .\run-local.ps1

$ErrorActionPreference = "Stop"

# Always run from the project root, regardless of the caller's directory
Set-Location $PSScriptRoot

# Load .env file if it exists
$envFile = Join-Path $PSScriptRoot ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $key, $value = $line -split '=', 2
            [System.Environment]::SetEnvironmentVariable($key.Trim(), $value.Trim())
        }
    }
    Write-Host "[INFO] Environment variables loaded from .env" -ForegroundColor Green
}

# ── Choose your mode ─────────────────────────────────────────────────────────
$Mode = "h2"   # change to "supabase" to connect to the remote DB
# ─────────────────────────────────────────────────────────────────────────────

# 1. Java setup: Find JDK 17 (Eclipse Adoptium system install, then portable ~/.jdks)
$temurinDir = Get-ChildItem -Path "C:\Program Files\Eclipse Adoptium" -Filter "jdk-17*" -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $temurinDir) {
    $temurinDir = Get-ChildItem -Path (Join-Path $env:USERPROFILE ".jdks") -Filter "jdk-17*" -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
}
if ($temurinDir) {
    $env:JAVA_HOME = $temurinDir.FullName
    Write-Host "[INFO] Using JDK 17: $env:JAVA_HOME" -ForegroundColor Cyan
} else {
    # Fallback to hardcoded JetBrains WebStorm JBR or existing JAVA_HOME
    if (-not $env:JAVA_HOME) {
        $env:JAVA_HOME = "C:\Program Files\JetBrains\WebStorm 2026.1.3\jbr"
    }
    Write-Host "[INFO] Fallback JDK path: $env:JAVA_HOME" -ForegroundColor Yellow
}
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

if ($Mode -eq "supabase") {
    # ── Supabase mode: connect to the remote cloud database ──────────────────
    Write-Host "[INFO] Starting with SUPABASE (remote PostgreSQL)..." -ForegroundColor Cyan

    $env:JDBC_DATABASE_URL = "jdbc:postgresql://aws-0-eu-west-1.pooler.supabase.com:5432/postgres?sslmode=require"
    $env:PGUSER            = "postgres.pzeerzaglvjzefbkadcf"

    $pwFile = Join-Path $PSScriptRoot ".db-password"
    if (-not (Test-Path $pwFile)) {
        Write-Host "Missing .db-password file. Create it with your Supabase password:" -ForegroundColor Yellow
        Write-Host '  Set-Content -Path .db-password -Value "<your-supabase-password>" -NoNewline' -ForegroundColor Yellow
        exit 1
    }
    $env:PGPASSWORD = (Get-Content $pwFile -Raw).Trim()

    & ".\mvnw.cmd" spring-boot:run

} else {
    # ── H2 mode: fully local, no external DB required ─────────────────────────
    Write-Host "[INFO] Starting with H2 in-memory database (local profile)..." -ForegroundColor Green
    Write-Host "[INFO] App:        http://localhost:8080" -ForegroundColor Green
    Write-Host "[INFO] H2 Console: http://localhost:8080/h2-console" -ForegroundColor Green

    & ".\mvnw.cmd" spring-boot:run "-Dspring-boot.run.profiles=local"
}
