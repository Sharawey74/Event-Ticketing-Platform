<#
.SYNOPSIS
Starts the full Eventora development stack on Windows.
#>

$ErrorActionPreference = "Stop"

Write-Host "🚀 Starting Eventora Development Environment..." -ForegroundColor Cyan

# 1. Start Docker Containers
Write-Host "`n📦 Starting Docker containers..." -ForegroundColor Yellow
docker-compose up -d

Write-Host "⏳ Waiting for databases to be ready (10 seconds)..." -ForegroundColor DarkGray
Start-Sleep -Seconds 10

# 2. Start Spring Boot Backend in a new window
Write-Host "`n☕ Starting Spring Boot backend on port 8088 (Opening new window)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Title 'Backend (Spring Boot)'; .\mvnw spring-boot:run"

# Wait for backend to be ready by checking port 8088
Write-Host "⏳ Waiting for Spring Boot to initialize..." -ForegroundColor DarkGray
$backendReady = $false
$retryCount = 0
while (-not $backendReady -and $retryCount -lt 60) {
    try {
        $connection = Test-NetConnection -ComputerName localhost -Port 8088 -InformationLevel Quiet -WarningAction SilentlyContinue
        if ($connection) {
            $backendReady = $true
            Write-Host "✅ Backend is up and running!" -ForegroundColor Green
        } else {
            Start-Sleep -Seconds 2
            Write-Host -NoNewline "."
            $retryCount++
        }
    } catch {
        Start-Sleep -Seconds 2
        Write-Host -NoNewline "."
        $retryCount++
    }
}
Write-Host ""

# 3. Start Stripe Webhook Listener in a new window
Write-Host "💳 Starting Stripe webhook listener (Opening new window)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Title 'Stripe Webhook'; stripe listen --forward-to localhost:8088/api/v1/payments/webhook"

# 4. Start Next.js Frontend in CURRENT window
Write-Host "`n💻 Starting Next.js frontend in current window..." -ForegroundColor Yellow
Set-Location .\frontend
npm run dev
