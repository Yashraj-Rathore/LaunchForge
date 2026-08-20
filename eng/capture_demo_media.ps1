[CmdletBinding()]
param(
    [ValidateRange(1000, 5000)]
    [int]$StepDelayMs = 4000,
    [switch]$LeaveCapturedState
)

$ErrorActionPreference = 'Stop'
$captureRepoRoot = Split-Path -Parent $PSScriptRoot
$captureDemoScript = Join-Path $PSScriptRoot 'demo.ps1'
$captureEnvPath = Join-Path $captureRepoRoot '.env'
$captureMediaPath = Join-Path $captureRepoRoot 'demos/demo-media'
$captureImage = 'launchforge-demo-capture:local'
$captureContainer = "launchforge-demo-capture-$PID"
$captureContainerCreated = $false
$captureArtifacts = @(
    '01-flag-workspace.png',
    '02-targeting-and-rollout.png',
    '03-deterministic-simulator.png',
    '04-storefront-ten-percent.png',
    '05-live-rollout-update.png',
    '06-kill-switch.png',
    '07-immutable-revisions.png',
    '08-audit-trail.png',
    'launchforge-admin-tour.webm',
    'northstar-live-update.webm'
)

function Get-CaptureEnvironmentValue {
    param([string]$Name)
    $content = [System.IO.File]::ReadAllText($captureEnvPath)
    $match = [regex]::Match($content, "(?m)^$([regex]::Escape($Name))=(?<value>[^\r\n]+)$")
    if (-not $match.Success) {
        throw "$Name is missing from .env"
    }
    return $match.Groups['value'].Value
}

Set-Location -LiteralPath $captureRepoRoot
& $captureDemoScript -Action Reset -ConfirmReset

$env:LAUNCHFORGE_DEMO_CAPTURE = 'true'
$env:LAUNCHFORGE_DEMO_STEP_DELAY_MS = [string]$StepDelayMs
$env:LAUNCHFORGE_E2E_BASE_URL = 'http://127.0.0.1:8080'
$env:LAUNCHFORGE_DEMO_STOREFRONT_URL = 'http://127.0.0.1:5174'
$env:LAUNCHFORGE_E2E_PASSWORD = Get-CaptureEnvironmentValue -Name 'LAUNCHFORGE_DEMO_USER_PASSWORD'

try {
    & docker build --file deploy/docker/Dockerfile.demo-capture --tag $captureImage .
    if ($LASTEXITCODE -ne 0) {
        throw "Playwright capture image build failed with exit code $LASTEXITCODE"
    }

    & docker create --name $captureContainer --network host `
        --env LAUNCHFORGE_DEMO_CAPTURE `
        --env LAUNCHFORGE_DEMO_STEP_DELAY_MS `
        --env LAUNCHFORGE_E2E_BASE_URL `
        --env LAUNCHFORGE_DEMO_STOREFRONT_URL `
        --env LAUNCHFORGE_E2E_PASSWORD `
        $captureImage
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to create the Playwright capture container. Exit code: $LASTEXITCODE"
    }
    $captureContainerCreated = $true

    & docker start --attach $captureContainer
    if ($LASTEXITCODE -ne 0) {
        throw "Playwright demo capture failed with exit code $LASTEXITCODE"
    }

    foreach ($artifact in $captureArtifacts) {
        $containerSource = "${captureContainer}:/workspace/demos/demo-media/$artifact"
        $localDestination = Join-Path $captureMediaPath $artifact
        & docker cp $containerSource $localDestination
        if ($LASTEXITCODE -ne 0) {
            throw "Unable to copy captured artifact $artifact. Exit code: $LASTEXITCODE"
        }
    }
    Write-Host 'Captured real-system screenshots and videos under demos/demo-media/.' -ForegroundColor Green
}
finally {
    if ($captureContainerCreated) {
        & docker rm --force $captureContainer | Out-Null
    }
    Remove-Item Env:LAUNCHFORGE_DEMO_CAPTURE -ErrorAction SilentlyContinue
    Remove-Item Env:LAUNCHFORGE_DEMO_STEP_DELAY_MS -ErrorAction SilentlyContinue
    Remove-Item Env:LAUNCHFORGE_E2E_BASE_URL -ErrorAction SilentlyContinue
    Remove-Item Env:LAUNCHFORGE_DEMO_STOREFRONT_URL -ErrorAction SilentlyContinue
    Remove-Item Env:LAUNCHFORGE_E2E_PASSWORD -ErrorAction SilentlyContinue
    if (-not $LeaveCapturedState) {
        & $captureDemoScript -Action Reset -ConfirmReset -NoBuild
    }
}
