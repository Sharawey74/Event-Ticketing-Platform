#Requires -Version 7.0
<#
.SYNOPSIS
    Starts the full Eventora dev stack in a single PowerShell 7 (x64) tab.
    All four processes run as background jobs with multiplexed, colour-coded,
    timestamped output — no extra windows or tabs.

.PARAMETER SkipDocker
    Skip docker-compose up. Useful when containers are already running.

.PARAMETER SkipStripe
    Skip the Stripe webhook listener. Useful when not testing payments.

.EXAMPLE
    .\start-dev.ps1
    .\start-dev.ps1 -SkipDocker
    .\start-dev.ps1 -SkipDocker -SkipStripe
#>

param(
    [switch]$SkipDocker,
    [switch]$SkipStripe
)

#region ── Config ─────────────────────────────────────────────────────────────
$ErrorActionPreference = 'Continue'
$Root         = $PSScriptRoot
$BackendPort  = 8088
$BackendUrl   = "http://localhost:$BackendPort"
$FrontendUrl  = "http://localhost:3000"
$WebhookPath  = "/api/v1/payments/webhook"
#endregion

#region ── Colour palette ─────────────────────────────────────────────────────
$C = @{
    DOCKER   = 'Yellow'
    BACKEND  = 'Cyan'
    STRIPE   = 'Magenta'
    FRONTEND = 'Green'
    SYS      = 'White'
    OK       = 'Green'
    WARN     = 'Yellow'
    ERR      = 'Red'
    DIM      = 'DarkGray'
}
#endregion

#region ── Helpers ────────────────────────────────────────────────────────────
function Write-Log {
    param(
        [string]$Tag,
        [string]$Msg,
        [string]$TagColor = 'White',
        [string]$MsgColor = 'Gray'
    )
    if ([string]::IsNullOrWhiteSpace($Msg)) { return }
    $ts = Get-Date -Format 'HH:mm:ss'
    Write-Host $ts         -NoNewline -ForegroundColor $C.DIM
    Write-Host " [$Tag]"   -NoNewline -ForegroundColor $TagColor
    Write-Host " $($Msg.TrimEnd())"   -ForegroundColor $MsgColor
}

function Test-Port ([int]$Port) {
    try {
        $tcp = [System.Net.Sockets.TcpClient]::new()
        $ok  = $tcp.ConnectAsync('localhost', $Port).Wait(500) -and $tcp.Connected
        $tcp.Dispose()
        return $ok
    } catch {
        return $false
    }
}

function Drain-Job ($Job, $Tag, $Color) {
    Receive-Job $Job -ErrorAction SilentlyContinue |
        Where-Object   { -not [string]::IsNullOrWhiteSpace($_) } |
        ForEach-Object { Write-Log $Tag $_ $Color $C.DIM }
}
#endregion

#region ── Banner ─────────────────────────────────────────────────────────────
Clear-Host
Write-Host ''
Write-Host '  ╔════════════════════════════════════════════════╗' -ForegroundColor Cyan
Write-Host '  ║     Eventora  ·  Dev Stack Launcher            ║' -ForegroundColor Cyan
Write-Host '  ║     Docker  ·  Backend  ·  Stripe  ·  UI       ║' -ForegroundColor Cyan
Write-Host '  ╚════════════════════════════════════════════════╝' -ForegroundColor Cyan
Write-Host ''
Write-Host "  Root : $Root" -ForegroundColor DarkGray
Write-Host "  Time : $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor DarkGray
Write-Host ''
#endregion

#region ── 1 · Docker ─────────────────────────────────────────────────────────
if ($SkipDocker) {
    Write-Log 'DOCKER' 'Skipped  (-SkipDocker).' $C.DOCKER $C.WARN
} else {
    Write-Log 'DOCKER' 'Starting containers  (postgres · redis · rabbitmq)...' $C.DOCKER

    Push-Location $Root
    $dockerOut = & docker-compose up -d 2>&1
    Pop-Location

    $dockerOut |
        Where-Object { $_ } |
        ForEach-Object { Write-Log 'DOCKER' $_ $C.DOCKER $C.DIM }

    Write-Log 'DOCKER' 'Waiting 8 s for services to initialise...' $C.DOCKER $C.WARN
    Start-Sleep -Seconds 8
    Write-Log 'DOCKER' 'Infrastructure ready.' $C.DOCKER $C.OK
}
#endregion

#region ── 2 · Backend Job ────────────────────────────────────────────────────
Write-Log 'BACKEND' 'Launching Spring Boot  (profile = local)...' $C.BACKEND

$backendJob = Start-Job -Name 'Backend' -ScriptBlock {
    param($root)
    Set-Location $root
    $env:SPRING_PROFILES_ACTIVE = 'local'
    & mvn spring-boot:run 2>&1
} -ArgumentList $Root

# Stream backend output while waiting for the HTTP port to open
Write-Log 'SYS' "Waiting for backend on :$BackendPort  (max 120 s)..." $C.SYS

$deadline = [DateTime]::Now.AddSeconds(120)
$ready    = $false
while ([DateTime]::Now -lt $deadline -and -not $ready) {
    Drain-Job $backendJob 'BACKEND' $C.BACKEND
    if (Test-Port $BackendPort) { $ready = $true }
    else                        { Start-Sleep -Seconds 2 }
}

if ($ready) {
    Write-Log 'BACKEND' "Live  →  $BackendUrl/actuator/health" $C.BACKEND $C.OK
} else {
    Write-Log 'BACKEND' "Port $BackendPort not open after 120 s — proceeding anyway." $C.BACKEND $C.ERR
}
#endregion

#region ── 3 · Stripe Job ─────────────────────────────────────────────────────
$stripeJob = $null

if ($SkipStripe) {
    Write-Log 'STRIPE' 'Skipped  (-SkipStripe).' $C.STRIPE $C.WARN
} elseif (-not (Get-Command stripe -ErrorAction SilentlyContinue)) {
    Write-Log 'STRIPE' 'stripe CLI not found in PATH — skipping.' $C.STRIPE $C.WARN
} else {
    $target = "$BackendUrl$WebhookPath"
    Write-Log 'STRIPE' "Forwarding events  →  $target" $C.STRIPE

    $stripeJob = Start-Job -Name 'Stripe' -ScriptBlock {
        param($target)
        & stripe listen --forward-to $target 2>&1
    } -ArgumentList $target
}
#endregion

#region ── 4 · Frontend Job ───────────────────────────────────────────────────
Write-Log 'FRONTEND' "Launching Next.js  →  $FrontendUrl" $C.FRONTEND

$frontendJob = Start-Job -Name 'Frontend' -ScriptBlock {
    param($root)
    Set-Location (Join-Path $root 'frontend')
    & npm run dev 2>&1
} -ArgumentList $Root
#endregion

#region ── Status box ─────────────────────────────────────────────────────────
Write-Host ''
Write-Host '  ┌─────────────────────────────────────────────────────┐' -ForegroundColor DarkGray
Write-Host '  │  All services launched  ·  streaming output below   │' -ForegroundColor DarkGray
Write-Host '  │  Press  Ctrl+C  to gracefully stop everything       │' -ForegroundColor DarkGray
Write-Host '  └─────────────────────────────────────────────────────┘' -ForegroundColor DarkGray
Write-Host ''
Write-Host "  BACKEND   $BackendUrl" -ForegroundColor $C.BACKEND
Write-Host "  FRONTEND  $FrontendUrl" -ForegroundColor $C.FRONTEND
if ($null -ne $stripeJob) {
    Write-Host "  STRIPE    $BackendUrl$WebhookPath" -ForegroundColor $C.STRIPE
}
Write-Host ''
Write-Host ('─' * 60) -ForegroundColor DarkGray
Write-Host ''
#endregion

#region ── Live output loop ───────────────────────────────────────────────────
$services = [System.Collections.Generic.List[hashtable]]::new()
$services.Add(@{ Job = $backendJob;  Tag = 'BACKEND';  Color = $C.BACKEND  })
$services.Add(@{ Job = $frontendJob; Tag = 'FRONTEND'; Color = $C.FRONTEND })
if ($null -ne $stripeJob) {
    $services.Add(@{ Job = $stripeJob; Tag = 'STRIPE'; Color = $C.STRIPE })
}

try {
    while ($true) {
        foreach ($svc in $services) {
            Drain-Job $svc.Job $svc.Tag $svc.Color
        }
        Start-Sleep -Milliseconds 250
    }
}
finally {
    Write-Host ''
    Write-Host ('─' * 60) -ForegroundColor DarkGray
    Write-Log 'SYS' 'Stopping all background jobs...' $C.SYS $C.WARN
    foreach ($svc in $services) {
        if ($null -ne $svc.Job) {
            Stop-Job   $svc.Job -ErrorAction SilentlyContinue
            Remove-Job $svc.Job -Force -ErrorAction SilentlyContinue
            Write-Log 'SYS' "$($svc.Tag) stopped." $C.SYS $C.DIM
        }
    }
    Write-Log 'SYS' 'All processes stopped. Goodbye.' $C.SYS $C.OK
    Write-Host ''
}
#endregion
