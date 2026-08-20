[CmdletBinding()]
param(
    [ValidateSet('Start', 'Verify', 'Stop', 'Reset')]
    [string]$Action = 'Start',
    [switch]$ConfirmReset,
    [switch]$NoBuild
)

$ErrorActionPreference = 'Stop'
$demoRepoRoot = Split-Path -Parent $PSScriptRoot
$demoProjectName = 'launchforge-demo'
$demoEnvPath = Join-Path $demoRepoRoot '.env'
$demoEnvTemplatePath = Join-Path $demoRepoRoot '.env.example'
$demoProfiles = @(
    '--profile', 'identity',
    '--profile', 'distribution',
    '--profile', 'platform',
    '--profile', 'demo'
)

function New-RandomHex {
    param([int]$ByteCount)
    $bytes = New-Object byte[] $ByteCount
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    }
    finally {
        $generator.Dispose()
    }
    return ([System.BitConverter]::ToString($bytes)).Replace('-', '').ToLowerInvariant()
}

function Initialize-DemoEnvironment {
    if (-not (Test-Path -LiteralPath $demoEnvPath)) {
        Copy-Item -LiteralPath $demoEnvTemplatePath -Destination $demoEnvPath
        Write-Host 'Created ignored .env with fresh local-only demo values.' -ForegroundColor Cyan
    }

    $content = [System.IO.File]::ReadAllText($demoEnvPath)
    $replacements = [ordered]@{
        'replace-with-a-local-only-password' = "local-db-$(New-RandomHex 16)"
        'replace-with-the-same-local-only-password' = $null
        'replace-with-a-local-only-admin-password' = "local-admin-$(New-RandomHex 12)"
        'replace-with-a-local-only-demo-password' = "Northstar-$(New-RandomHex 8)!"
        'replace-with-at-least-32-random-bytes' = (New-RandomHex 32)
        'replace-with-a-local-only-clickhouse-password' = "local-clickhouse-$(New-RandomHex 16)"
        'replace-with-a-local-only-grafana-password' = "local-grafana-$(New-RandomHex 16)"
    }
    $databaseMatch = [regex]::Match(
        $content,
        '(?m)^LAUNCHFORGE_POSTGRES_PASSWORD=(?<value>[^\r\n]+)$'
    )
    $databasePassword = if ($databaseMatch.Success -and
        $databaseMatch.Groups['value'].Value -ne 'replace-with-a-local-only-password') {
        $databaseMatch.Groups['value'].Value
    }
    else {
        $replacements['replace-with-a-local-only-password']
    }
    $replacements['replace-with-the-same-local-only-password'] = $databasePassword

    foreach ($placeholder in $replacements.Keys) {
        $content = $content.Replace($placeholder, [string]$replacements[$placeholder])
    }
    [System.IO.File]::WriteAllText(
        $demoEnvPath,
        $content,
        (New-Object System.Text.UTF8Encoding($false))
    )
}

function Invoke-DemoCompose {
    param([string[]]$Command)
    $dockerArguments = @('compose', '--project-name', $demoProjectName) + $demoProfiles + $Command
    & docker @dockerArguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose failed with exit code $LASTEXITCODE"
    }
}

function Get-DemoEnvironmentValue {
    param([string]$Name)
    $content = [System.IO.File]::ReadAllText($demoEnvPath)
    $match = [regex]::Match($content, "(?m)^$([regex]::Escape($Name))=(?<value>[^\r\n]+)$")
    if (-not $match.Success) {
        throw "$Name is missing from .env"
    }
    return $match.Groups['value'].Value
}

function Assert-HttpReady {
    param([string]$Name, [string]$Uri)
    try {
        $response = Invoke-WebRequest -Uri $Uri -UseBasicParsing -TimeoutSec 10
    }
    catch {
        throw "$Name is not ready at $Uri. $($_.Exception.Message)"
    }
    if ($response.StatusCode -lt 200 -or $response.StatusCode -ge 300) {
        throw "$Name returned HTTP $($response.StatusCode) at $Uri"
    }
}

function Assert-DemoSeed {
    $databaseUser = Get-DemoEnvironmentValue -Name 'LAUNCHFORGE_POSTGRES_USER'
    $databaseName = Get-DemoEnvironmentValue -Name 'LAUNCHFORGE_POSTGRES_DB'
    $query = @'
SELECT
  (SELECT COUNT(*) FROM environments WHERE project_id = '40000000-0000-0000-0000-000000000001'),
  (SELECT COUNT(*) FROM flags WHERE project_id = '40000000-0000-0000-0000-000000000001'),
  (SELECT COUNT(*) FROM flag_environment_configs WHERE project_id = '40000000-0000-0000-0000-000000000001'),
  (SELECT COUNT(*) FROM environment_revisions WHERE environment_id = '50000000-0000-0000-0000-000000000001'),
  (SELECT COUNT(*) FROM browser_client_keys WHERE environment_id = '50000000-0000-0000-0000-000000000001');
'@
    $dockerArguments = @(
        'compose', '--project-name', $demoProjectName,
        'exec', '-T', 'postgres',
        'psql', '--set', 'ON_ERROR_STOP=1', '--username', $databaseUser, '--dbname', $databaseName,
        '--tuples-only', '--no-align', '--command', $query
    )
    $result = (& docker @dockerArguments | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) {
        throw 'Unable to verify the deterministic PostgreSQL seed.'
    }
    if ($result -ne '3|4|12|1|1') {
        throw "Unexpected deterministic seed counts: $result"
    }
}

function Assert-BrowserSnapshot {
    $key = 'lf_client_' + 'NorthstarDemoClientKey0000000001'
    $uri = "http://127.0.0.1:8082/sdk/v1/client/$key/snapshot"
    for ($attempt = 1; $attempt -le 30; $attempt++) {
        try {
            $response = Invoke-WebRequest -Uri $uri -UseBasicParsing -TimeoutSec 5 -Headers @{
                Accept = 'application/json'
                Origin = 'http://127.0.0.1:5174'
            }
            $snapshot = $response.Content | ConvertFrom-Json
            if ($response.StatusCode -eq 200 -and $snapshot.revision -ge 1 -and
                $snapshot.flags.'new-checkout') {
                return
            }
        }
        catch {
            if ($attempt -eq 30) {
                throw "Config Edge did not serve the seeded browser snapshot. $($_.Exception.Message)"
            }
        }
        Start-Sleep -Seconds 1
    }
    throw 'Config Edge did not serve the seeded browser snapshot within 30 seconds.'
}

function Test-DemoStack {
    Assert-HttpReady -Name 'Admin console' -Uri 'http://127.0.0.1:8080/healthz'
    Assert-HttpReady -Name 'Config Edge' -Uri 'http://127.0.0.1:8082/actuator/health/readiness'
    Assert-HttpReady -Name 'Northstar storefront' -Uri 'http://127.0.0.1:5174/healthz'
    Assert-DemoSeed
    Assert-BrowserSnapshot
    Write-Host 'Demo verification passed: services, deterministic seed, and real browser snapshot are ready.' -ForegroundColor Green
}

function Show-DemoAccess {
    $demoPassword = Get-DemoEnvironmentValue -Name 'LAUNCHFORGE_DEMO_USER_PASSWORD'
    Write-Host ''
    Write-Host 'LaunchForge demo is ready' -ForegroundColor Green
    Write-Host '  Admin console:       http://127.0.0.1:8080'
    Write-Host '  Northstar storefront: http://127.0.0.1:5174'
    Write-Host '  Config Edge:          http://127.0.0.1:8082'
    Write-Host '  Sign-in user:         owner'
    Write-Host "  Local demo password:  $demoPassword"
    Write-Host ''
    Write-Host 'Use only this generated local password. It is stored in ignored .env and is not a production credential.'
    Write-Host 'Walkthrough: docs/16_DEMO_PORTFOLIO.md'
}

function Start-DemoStack {
    Initialize-DemoEnvironment
    Invoke-DemoCompose -Command @('config', '--quiet')
    $up = @('up', '-d', '--wait')
    if (-not $NoBuild) {
        $up += '--build'
    }
    Invoke-DemoCompose -Command $up
    Test-DemoStack
    Show-DemoAccess
}

Set-Location -LiteralPath $demoRepoRoot
switch ($Action) {
    'Start' {
        Start-DemoStack
    }
    'Verify' {
        if (-not (Test-Path -LiteralPath $demoEnvPath)) {
            throw '.env does not exist. Run the Start action first.'
        }
        Test-DemoStack
        Show-DemoAccess
    }
    'Stop' {
        Initialize-DemoEnvironment
        Invoke-DemoCompose -Command @('down', '--remove-orphans')
        Write-Host 'Stopped the isolated demo stack; deterministic data is preserved.' -ForegroundColor Cyan
    }
    'Reset' {
        if (-not $ConfirmReset) {
            throw 'Reset removes only launchforge-demo containers and named volumes. Re-run with -ConfirmReset.'
        }
        Initialize-DemoEnvironment
        Invoke-DemoCompose -Command @('down', '--volumes', '--remove-orphans')
        Write-Host 'Removed the isolated launchforge-demo volumes. Rebuilding the deterministic baseline.' -ForegroundColor Yellow
        Start-DemoStack
    }
}
