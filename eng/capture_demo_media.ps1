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

    $mediaMount = "type=bind,source=$captureMediaPath,target=/workspace/demos/demo-media"
    & docker run --rm --network host --mount $mediaMount `
        --env LAUNCHFORGE_DEMO_CAPTURE `
        --env LAUNCHFORGE_DEMO_STEP_DELAY_MS `
        --env LAUNCHFORGE_E2E_BASE_URL `
        --env LAUNCHFORGE_DEMO_STOREFRONT_URL `
        --env LAUNCHFORGE_E2E_PASSWORD `
        $captureImage
    if ($LASTEXITCODE -ne 0) {
        throw "Playwright demo capture failed with exit code $LASTEXITCODE"
    }
    Write-Host 'Captured real-system screenshots and videos under demos/demo-media/.' -ForegroundColor Green
}
finally {
    Remove-Item Env:LAUNCHFORGE_DEMO_CAPTURE -ErrorAction SilentlyContinue
    Remove-Item Env:LAUNCHFORGE_DEMO_STEP_DELAY_MS -ErrorAction SilentlyContinue
    Remove-Item Env:LAUNCHFORGE_E2E_BASE_URL -ErrorAction SilentlyContinue
    Remove-Item Env:LAUNCHFORGE_DEMO_STOREFRONT_URL -ErrorAction SilentlyContinue
    Remove-Item Env:LAUNCHFORGE_E2E_PASSWORD -ErrorAction SilentlyContinue
    if (-not $LeaveCapturedState) {
        & $captureDemoScript -Action Reset -ConfirmReset -NoBuild
    }
}
