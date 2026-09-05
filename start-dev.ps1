#Requires -Version 7.0
<#
.SYNOPSIS
    Starts the full Eventora dev stack in a single PowerShell 7 (x64) tab.
    The backend runs as the `app` container from docker-compose; Stripe and the
    Next.js frontend run as background jobs. All output is multiplexed,
    colour-coded and timestamped — no extra windows or tabs.

.DESCRIPTION
    Default mode runs the backend from the `ticketing-backend:local` image, so
    nothing but Docker and Node is needed on the host — no JDK, no Maven.

    The image is compared against the mtimes of src/main, pom.xml and Dockerfile
    on every run. If the sources are newer, the image is rebuilt before starting,
    because a stale image silently runs code you have already replaced.

.PARAMETER HostBackend
    Run the backend with `mvn spring-boot:run` on the host instead of in a
    container. Use for debugger attach or devtools reload. The `app` container is
    stopped first so it cannot hold port 8088.

.PARAMETER Rebuild
    Force `docker compose build app` even when the image looks current.

.PARAMETER NoRebuild
    Never rebuild, even when the image is older than the sources. The staleness
    warning is still printed.

.PARAMETER SkipDocker
    Skip docker-compose entirely. Assumes every container is already running.

.PARAMETER SkipStripe
    Skip the Stripe webhook listener. Useful when not testing payments.

.PARAMETER StopOnExit
    Run `docker compose down` on Ctrl+C. By default the containers are left
    running so the next start is instant.

.EXAMPLE
    .\start-dev.ps1
    .\start-dev.ps1 -Rebuild
    .\start-dev.ps1 -HostBackend
    .\start-dev.ps1 -SkipDocker -SkipStripe
#>

param(
    [switch]$HostBackend,
    [switch]$Rebuild,
    [switch]$NoRebuild,
    [switch]$SkipDocker,
    [switch]$SkipStripe,
    [switch]$StopOnExit
)

#region ── Config ─────────────────────────────────────────────────────────────
$ErrorActionPreference = 'Continue'
$Root         = $PSScriptRoot
$BackendPort  = 8088
$BackendUrl   = "http://localhost:$BackendPort"
$FrontendUrl  = "http://localhost:3000"
$WebhookPath  = "/api/v1/payments/webhook"
$AppService   = 'app'
$AppImage     = 'ticketing-backend:local'

# Everything except `app` — used when the backend runs on the host instead.
$InfraServices = @('postgres', 'redis', 'rabbitmq', 'pgadmin', 'redis-commander', 'mailhog')

# The container advertises itself healthy only after start_period (60 s), and a
# cold Flyway migration on first boot is slower still.
$BackendTimeoutSec = if ($HostBackend) { 120 } else { 180 }
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

# An open port only proves *something* is listening — a half-started app or a
# leftover process both pass that test. Ask the actuator instead.
function Test-BackendUp {
    try {
        $r = Invoke-RestMethod -Uri "$BackendUrl/actuator/health" -TimeoutSec 3 -ErrorAction Stop
        return $r.status -eq 'UP'
    } catch {
        return $false
    }
}

function Drain-Job ($Job, $Tag, $Color) {
    if ($null -eq $Job) { return }
    Receive-Job $Job -ErrorAction SilentlyContinue |
        Where-Object   { -not [string]::IsNullOrWhiteSpace($_) } |
        ForEach-Object { Write-Log $Tag $_ $Color $C.DIM }
}

# Docker Desktop ships `docker compose` (v2); older installs only have the
# standalone `docker-compose` binary. Resolve once, use everywhere.
$script:ComposeV2 = $false
function Resolve-Compose {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { return $false }
    & docker compose version *> $null
    if ($LASTEXITCODE -eq 0) { $script:ComposeV2 = $true; return $true }
    if (Get-Command docker-compose -ErrorAction SilentlyContinue) { return $true }
    return $false
}

# Takes ONE array argument, deliberately. With
# [Parameter(ValueFromRemainingArguments)] PowerShell parses any token starting
# with '-' as a parameter *name*, so `Invoke-Compose up -d` silently bound just
# @('up') and ran an attached `docker compose up` that never returns.
function Invoke-Compose {
    param([Parameter(Mandatory = $true)][string[]]$ComposeArgs)
    Push-Location $Root
    try {
        if ($script:ComposeV2) { & docker compose @ComposeArgs 2>&1 }
        else                   { & docker-compose  @ComposeArgs 2>&1 }
    } finally {
        Pop-Location
    }
}

# Pipeline-bound so compose progress prints as it arrives. Collecting into a
# variable first meant a slow `up` sat silent with no sign it was alive.
function Write-ComposeOutput {
    param([Parameter(ValueFromPipeline = $true)]$Line)
    process {
        if ($null -ne $Line -and -not [string]::IsNullOrWhiteSpace([string]$Line)) {
            Write-Log 'DOCKER' ([string]$Line) $C.DOCKER $C.DIM
        }
    }
}

# Newest write across everything baked into the backend image.
function Get-NewestSourceWriteUtc {
    $targets = @(
        (Join-Path $Root 'src\main'),
        (Join-Path $Root 'pom.xml'),
        (Join-Path $Root 'Dockerfile')
    ) | Where-Object { Test-Path $_ }

    if (-not $targets) { return $null }

    ($targets |
        ForEach-Object { Get-ChildItem $_ -Recurse -File -ErrorAction SilentlyContinue } |
        Measure-Object -Property LastWriteTimeUtc -Maximum).Maximum
}

function Get-ImageBuildUtc {
    $raw = & docker image inspect $AppImage --format '{{.Created}}' 2>$null
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($raw)) { return $null }
    try {
        # Docker emits 9 fractional digits; .NET parses at most 7. Drop them.
        $trimmed = ([string]$raw).Trim() -replace '\.\d+Z$', 'Z'
        return [datetime]::Parse(
            $trimmed, $null,
            [System.Globalization.DateTimeStyles]::RoundtripKind
        ).ToUniversalTime()
    } catch {
        return $null
    }
}
#endregion

#region ── Banner ─────────────────────────────────────────────────────────────
# Both guarded: Clear-Host throws "handle is invalid" when stdout is redirected
# (CI, `> run.log`), and without UTF-8 the box-drawing glyphs below become mojibake
# on a legacy console codepage.
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }
try { Clear-Host } catch { }

$mode = if ($HostBackend) { 'host (maven)' } else { 'container' }
Write-Host ''
Write-Host '  ╔════════════════════════════════════════════════╗' -ForegroundColor Cyan
Write-Host '  ║     Eventora  ·  Dev Stack Launcher            ║' -ForegroundColor Cyan
Write-Host '  ║     Docker  ·  Backend  ·  Stripe  ·  UI       ║' -ForegroundColor Cyan
Write-Host '  ╚════════════════════════════════════════════════╝' -ForegroundColor Cyan
Write-Host ''
Write-Host "  Root    : $Root"                                  -ForegroundColor DarkGray
Write-Host "  Time    : $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor DarkGray
Write-Host "  Backend : $mode"                                  -ForegroundColor DarkGray
Write-Host ''
#endregion

#region ── 0 · Preflight ──────────────────────────────────────────────────────
# Resolve compose unconditionally: even under -SkipDocker the backend log-follow
# job needs to know whether it can pass v2-only flags like --no-log-prefix.
$composeOk = Resolve-Compose

if (-not $SkipDocker) {
    if (-not $composeOk) {
        Write-Log 'DOCKER' 'Neither `docker compose` nor `docker-compose` is available. Install Docker Desktop.' $C.DOCKER $C.ERR
        return
    }

    & docker info *> $null
    if ($LASTEXITCODE -ne 0) {
        Write-Log 'DOCKER' 'Docker daemon is not responding — start Docker Desktop and re-run.' $C.DOCKER $C.ERR
        return
    }
}
#endregion

#region ── 1 · Docker ─────────────────────────────────────────────────────────
if ($SkipDocker) {
    Write-Log 'DOCKER' 'Skipped  (-SkipDocker).' $C.DOCKER $C.WARN
}
elseif ($HostBackend) {
    # Free port 8088 before Maven claims it, or the JVM dies on bind and the
    # container answers the health check in its place — a silent wrong-code run.
    Write-Log 'DOCKER' "Stopping the '$AppService' container so the host backend owns :$BackendPort..." $C.DOCKER
    Invoke-Compose @('stop', $AppService) | Write-ComposeOutput

    Write-Log 'DOCKER' 'Starting infrastructure  (postgres · redis · rabbitmq · tooling)...' $C.DOCKER
    Invoke-Compose (@('up', '-d') + $InfraServices) | Write-ComposeOutput
    Write-Log 'DOCKER' 'Infrastructure ready  (compose waited on healthchecks).' $C.DOCKER $C.OK
}
else {
    # ── Staleness check ────────────────────────────────────────────────────────
    $doBuild   = [bool]$Rebuild
    $imageUtc  = Get-ImageBuildUtc

    if (-not $imageUtc) {
        Write-Log 'DOCKER' "Image '$AppImage' not found — building it." $C.DOCKER $C.WARN
        $doBuild = $true
    }
    else {
        $srcUtc = Get-NewestSourceWriteUtc
        if ($srcUtc -and $srcUtc -gt $imageUtc) {
            Write-Log 'DOCKER' "Image is STALE — built $($imageUtc.ToLocalTime().ToString('yyyy-MM-dd HH:mm')), sources changed $($srcUtc.ToLocalTime().ToString('yyyy-MM-dd HH:mm'))." $C.DOCKER $C.WARN
            if ($NoRebuild) {
                Write-Log 'DOCKER' 'Running it anyway (-NoRebuild) — the backend will NOT include your latest changes.' $C.DOCKER $C.ERR
            } else {
                $doBuild = $true
            }
        }
        elseif (-not $Rebuild) {
            Write-Log 'DOCKER' "Image is current  (built $($imageUtc.ToLocalTime().ToString('yyyy-MM-dd HH:mm')))." $C.DOCKER $C.DIM
        }
    }

    if ($doBuild) {
        Write-Log 'DOCKER' 'Building backend image — this takes a few minutes on a cold cache...' $C.DOCKER
        Invoke-Compose @('build', $AppService) | Write-ComposeOutput
        if ($LASTEXITCODE -ne 0) {
            Write-Log 'DOCKER' 'Image build FAILED — aborting.' $C.DOCKER $C.ERR
            return
        }
        Write-Log 'DOCKER' 'Image built.' $C.DOCKER $C.OK
    }

    Write-Log 'DOCKER' 'Starting full stack  (infrastructure + backend)...' $C.DOCKER
    Invoke-Compose @('up', '-d') | Write-ComposeOutput
    Write-Log 'DOCKER' 'Containers up  (compose waited on infrastructure healthchecks).' $C.DOCKER $C.OK
}
#endregion

#region ── 2 · Backend ────────────────────────────────────────────────────────
$backendJob = $null

if ($HostBackend) {
    Write-Log 'BACKEND' 'Launching Spring Boot on the host  (profile = local)...' $C.BACKEND

    $backendJob = Start-Job -Name 'Backend' -ScriptBlock {
        param($root)
        try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }
        Set-Location $root
        $env:SPRING_PROFILES_ACTIVE = 'local'
        & mvn spring-boot:run 2>&1
    } -ArgumentList $Root
}
elseif (-not $SkipDocker -or (Test-Port $BackendPort)) {
    Write-Log 'BACKEND' "Following '$AppService' container logs..." $C.BACKEND

    $backendJob = Start-Job -Name 'Backend' -ScriptBlock {
        param($root, $svc, $v2)
        try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }
        Set-Location $root
        if ($v2) { & docker compose logs -f --tail 40 --no-log-prefix $svc 2>&1 }
        else     { & docker-compose  logs -f --tail 40 $svc 2>&1 }
    } -ArgumentList $Root, $AppService, $script:ComposeV2
}

Write-Log 'SYS' "Waiting for /actuator/health to report UP  (max $BackendTimeoutSec s)..." $C.SYS

$deadline = [DateTime]::Now.AddSeconds($BackendTimeoutSec)
$ready    = $false
while ([DateTime]::Now -lt $deadline -and -not $ready) {
    Drain-Job $backendJob 'BACKEND' $C.BACKEND
    if (Test-BackendUp) { $ready = $true } else { Start-Sleep -Seconds 2 }
}

if ($ready) {
    Write-Log 'BACKEND' "Live and healthy  →  $BackendUrl/actuator/health" $C.BACKEND $C.OK
} else {
    Write-Log 'BACKEND' "Not healthy after $BackendTimeoutSec s — proceeding anyway; see the log lines above." $C.BACKEND $C.ERR
    if (-not $HostBackend -and -not $SkipDocker) {
        Write-Log 'BACKEND' "Inspect with:  docker compose logs $AppService" $C.BACKEND $C.WARN
    }
}
#endregion

#region ── 3 · Stripe Job ─────────────────────────────────────────────────────
$stripeJob = $null

if ($SkipStripe) {
    Write-Log 'STRIPE' 'Skipped  (-SkipStripe).' $C.STRIPE $C.WARN
} elseif (-not (Get-Command stripe -ErrorAction SilentlyContinue)) {
    Write-Log 'STRIPE' 'stripe CLI not found in PATH — skipping.' $C.STRIPE $C.WARN
    Write-Log 'STRIPE' 'Without it checkout.session.completed never arrives; the confirmation page falls back to sync-payment.' $C.STRIPE $C.DIM
} else {
    $target = "$BackendUrl$WebhookPath"
    Write-Log 'STRIPE' "Forwarding events  →  $target" $C.STRIPE

    $stripeJob = Start-Job -Name 'Stripe' -ScriptBlock {
        param($target)
        try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }
        & stripe listen --forward-to $target 2>&1
    } -ArgumentList $target
}
#endregion

#region ── 4 · Frontend Job ───────────────────────────────────────────────────
Write-Log 'FRONTEND' "Launching Next.js  →  $FrontendUrl" $C.FRONTEND

$frontendJob = Start-Job -Name 'Frontend' -ScriptBlock {
    param($root)
    try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }
    Set-Location (Join-Path $root 'frontend')
    & npm run dev 2>&1
} -ArgumentList $Root
#endregion

#region ── Status box ─────────────────────────────────────────────────────────
Write-Host ''
Write-Host '  ┌─────────────────────────────────────────────────────┐' -ForegroundColor DarkGray
Write-Host '  │  All services launched  ·  streaming output below   │' -ForegroundColor DarkGray
Write-Host '  │  Press  Ctrl+C  to stop the foreground services     │' -ForegroundColor DarkGray
Write-Host '  └─────────────────────────────────────────────────────┘' -ForegroundColor DarkGray
Write-Host ''
Write-Host "  BACKEND   $BackendUrl   ($mode)" -ForegroundColor $C.BACKEND
Write-Host "  FRONTEND  $FrontendUrl"          -ForegroundColor $C.FRONTEND
if ($null -ne $stripeJob) {
    Write-Host "  STRIPE    $BackendUrl$WebhookPath" -ForegroundColor $C.STRIPE
}
if (-not $SkipDocker) {
    Write-Host "  TOOLING   pgAdmin :5050  ·  redis :8082  ·  rabbit :15672  ·  mail :8025" -ForegroundColor $C.DIM
}
Write-Host ''
Write-Host ('─' * 60) -ForegroundColor DarkGray
Write-Host ''
#endregion

#region ── Live output loop ───────────────────────────────────────────────────
$services = [System.Collections.Generic.List[hashtable]]::new()
if ($null -ne $backendJob) {
    $services.Add(@{ Job = $backendJob; Tag = 'BACKEND'; Color = $C.BACKEND })
}
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
    Write-Log 'SYS' 'Stopping background jobs...' $C.SYS $C.WARN
    foreach ($svc in $services) {
        if ($null -ne $svc.Job) {
            Stop-Job   $svc.Job -ErrorAction SilentlyContinue
            Remove-Job $svc.Job -Force -ErrorAction SilentlyContinue
            Write-Log 'SYS' "$($svc.Tag) stopped." $C.SYS $C.DIM
        }
    }

    if ($StopOnExit -and -not $SkipDocker) {
        Write-Log 'DOCKER' 'Tearing down containers  (-StopOnExit)...' $C.DOCKER $C.WARN
        Invoke-Compose @('down') | Write-ComposeOutput
        Write-Log 'DOCKER' 'Containers stopped.' $C.DOCKER $C.OK
    }
    elseif (-not $SkipDocker) {
        Write-Log 'SYS' 'Containers left running — stop them with:  docker compose down' $C.SYS $C.DIM
    }

    Write-Log 'SYS' 'Done.' $C.SYS $C.OK
    Write-Host ''
}
#endregion
