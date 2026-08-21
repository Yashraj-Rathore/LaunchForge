[CmdletBinding()]
param(
    [string]$ClusterName = 'launchforge-prompt12',
    [switch]$SkipBuild,
    [switch]$KeepCluster
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function ConvertTo-ProofBase64Url {
    param([byte[]]$Bytes)
    return [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

$proofRepoRoot = Split-Path -Parent $PSScriptRoot
$proofNamespace = 'launchforge'
$proofRelease = 'prompt12'
$proofFullname = 'prompt12-launchforge'
$proofComposeProject = 'launchforge-prompt12'
$proofSdkKey = 'lf_srv_{0}_{1}' -f `
    (ConvertTo-ProofBase64Url ([byte[]](32..43))), `
    (ConvertTo-ProofBase64Url ([byte[]](0..31)))
$proofPepper = 'prompt12-local-pepper-at-least-32-bytes'
$proofDatabasePassword = 'prompt12-local-postgres-only'
$proofRedisManagementPassword = 'prompt12-local-management-redis-only'
$proofRedisConfigEdgePassword = 'prompt12-local-config-edge-redis-only'
$proofRedisEventWorkerPassword = 'prompt12-local-event-worker-redis-only'
$proofDemoProcess = $null
$proofPortForwardProcess = $null

function Resolve-ProofExecutable {
    param([string]$Name, [string]$WingetPackagePattern)
    $wingetRoot = Join-Path $env:LOCALAPPDATA 'Microsoft\WinGet\Packages'
    $candidate = Get-ChildItem -Path $wingetRoot -Filter "$Name.exe" -Recurse -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -like "*$WingetPackagePattern*" } |
        Select-Object -First 1
    if ($null -ne $candidate) {
        return $candidate.FullName
    }
    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if ($null -eq $command) {
        throw "Required executable '$Name' was not found. Install the pinned Prompt 12 toolchain first."
    }
    return $command.Source
}

function Invoke-ProofCommand {
    param([string]$Executable, [string[]]$Arguments)
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed ($LASTEXITCODE): $Executable $($Arguments -join ' ')"
    }
}

function Wait-ProofCondition {
    param(
        [scriptblock]$Condition,
        [string]$Description,
        [int]$Attempts = 60,
        [int]$DelaySeconds = 2
    )
    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        if (& $Condition) {
            return
        }
        Start-Sleep -Seconds $DelaySeconds
    }
    throw "Timed out waiting for $Description."
}

function Start-ProofPortForward {
    $stdout = Join-Path $env:TEMP 'launchforge-prompt12-port-forward.out.log'
    $stderr = Join-Path $env:TEMP 'launchforge-prompt12-port-forward.err.log'
    $script:proofPortForwardProcess = Start-Process -FilePath $proofKubectl -ArgumentList @(
        '--context', "kind-$ClusterName", '-n', $proofNamespace, 'port-forward',
        'service/launchforge-config-edge', '18082:8082'
    ) -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden -PassThru
    Wait-ProofCondition -Description 'Config Edge port-forward' -Attempts 30 -Condition {
        try {
            Invoke-WebRequest -UseBasicParsing -TimeoutSec 1 -Uri 'http://127.0.0.1:18082/actuator/health/liveness' | Out-Null
            return $true
        } catch {
            return $false
        }
    }
}

function Stop-ProofProcess {
    param($Process)
    if ($null -ne $Process -and -not $Process.HasExited) {
        Stop-Process -Id $Process.Id -Force
        $Process.WaitForExit()
    }
}

function Get-ProofDemoResult {
    return Invoke-RestMethod -TimeoutSec 3 -Uri 'http://127.0.0.1:18080/demo/proof-subject?plan=pro'
}

function Wait-ProofDemoRevision {
    param([long]$Revision)
    Wait-ProofCondition -Description "SDK revision $Revision" -Attempts 60 -Condition {
        try {
            return (Get-ProofDemoResult).snapshotRevision -eq $Revision
        } catch {
            return $false
        }
    }
}

function Invoke-ProofSeed {
    param([string]$Path)
    Get-Content -Raw $Path |
        & docker compose --project-directory $proofRepoRoot --project-name $proofComposeProject exec -T postgres `
            psql --set ON_ERROR_STOP=1 -U launchforge -d launchforge
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to apply local proof fixture $Path."
    }
}

$proofKind = Resolve-ProofExecutable -Name 'kind' -WingetPackagePattern 'Kubernetes.kind'
$proofKubectl = Resolve-ProofExecutable -Name 'kubectl' -WingetPackagePattern 'Kubernetes.kubectl'
$proofHelm = Resolve-ProofExecutable -Name 'helm' -WingetPackagePattern 'Helm.Helm'
$proofMaven = Join-Path $proofRepoRoot 'mvnw.cmd'
$proofKindConfig = Join-Path $proofRepoRoot 'deploy\local\kind\kind-config.yaml'
$proofValues = Join-Path $proofRepoRoot 'deploy\local\kind\values.yaml'
$proofChart = Join-Path $proofRepoRoot 'deploy\helm\launchforge'
$proofRevisionOne = Join-Path $proofRepoRoot 'deploy\local\kind\seed-revision-1.sql'
$proofRevisionTwo = Join-Path $proofRepoRoot 'deploy\local\kind\seed-revision-2.sql'
$proofMaterializationKeyGenerator = Join-Path $proofRepoRoot 'eng\MaterializationKeyPairGenerator.java'
$proofMaterializationKeys = @(& java $proofMaterializationKeyGenerator)
if ($LASTEXITCODE -ne 0 -or $proofMaterializationKeys.Count -ne 2) {
    throw 'Unable to generate the proof Ed25519 materialization key pair.'
}
$proofMaterializationPrivateKey = $proofMaterializationKeys[0]
$proofMaterializationVerificationKeys = "kind-v1:$($proofMaterializationKeys[1])"

Push-Location $proofRepoRoot
try {
    $env:LAUNCHFORGE_POSTGRES_PASSWORD = $proofDatabasePassword
    $env:LAUNCHFORGE_KEYCLOAK_ADMIN_PASSWORD = 'prompt12-local-keycloak-admin-only'
    $env:LAUNCHFORGE_DEMO_USER_PASSWORD = 'prompt12-local-demo-user-only'
    $env:LAUNCHFORGE_CLICKHOUSE_PASSWORD = 'prompt12-local-clickhouse-only'
    $env:LAUNCHFORGE_GRAFANA_ADMIN_PASSWORD = 'prompt12-local-grafana-only'
    $env:LAUNCHFORGE_SDK_KEY_PEPPER = $proofPepper
    $env:LAUNCHFORGE_REDIS_MANAGEMENT_PASSWORD = $proofRedisManagementPassword
    $env:LAUNCHFORGE_REDIS_CONFIG_EDGE_PASSWORD = $proofRedisConfigEdgePassword
    $env:LAUNCHFORGE_REDIS_EVENT_WORKER_PASSWORD = $proofRedisEventWorkerPassword
    $env:LAUNCHFORGE_MATERIALIZATION_SIGNING_KEY_ID = 'kind-v1'
    $env:LAUNCHFORGE_MATERIALIZATION_SIGNING_PRIVATE_KEY = $proofMaterializationPrivateKey
    $env:LAUNCHFORGE_MATERIALIZATION_VERIFICATION_KEYS = $proofMaterializationVerificationKeys
    $env:LAUNCHFORGE_BIND_ADDRESS = '0.0.0.0'
    $env:LAUNCHFORGE_KAFKA_ADVERTISED_HOST = 'host.docker.internal'
    $env:LAUNCHFORGE_CONTAINER_OIDC_ORIGIN = 'http://host.docker.internal:8081'

    if (-not $SkipBuild) {
        Write-Host 'Building the five non-root Prompt 12 images...'
        Invoke-ProofCommand docker @('build', '-f', 'deploy/docker/Dockerfile.migrator', '-t', 'launchforge-migrator:prompt12', '.')
        Invoke-ProofCommand docker @('build', '-f', 'deploy/docker/Dockerfile.java', '--build-arg', 'MODULE=backend/launchforge-control-api', '--build-arg', 'ARTIFACT=backend/launchforge-control-api/target/launchforge-control-api-0.1.0-SNAPSHOT-exec.jar', '--build-arg', 'APP_PORT=8080', '-t', 'launchforge-management:prompt12', '.')
        Invoke-ProofCommand docker @('build', '-f', 'deploy/docker/Dockerfile.java', '--build-arg', 'MODULE=backend/launchforge-config-edge', '--build-arg', 'ARTIFACT=backend/launchforge-config-edge/target/launchforge-config-edge-0.1.0-SNAPSHOT-exec.jar', '--build-arg', 'APP_PORT=8082', '-t', 'launchforge-config-edge:prompt12', '.')
        Invoke-ProofCommand docker @('build', '-f', 'deploy/docker/Dockerfile.java', '--build-arg', 'MODULE=backend/launchforge-event-worker', '--build-arg', 'ARTIFACT=backend/launchforge-event-worker/target/launchforge-event-worker-0.1.0-SNAPSHOT-exec.jar', '--build-arg', 'APP_PORT=8083', '-t', 'launchforge-event-worker:prompt12', '.')
        Invoke-ProofCommand docker @('build', '-f', 'deploy/docker/Dockerfile.web', '-t', 'launchforge-web:prompt12', '.')
    }

    Invoke-ProofCommand $proofMaven @('--batch-mode', '--no-transfer-progress', '-pl', 'demos/spring-demo', '-am', 'package', '-DskipTests')

    & $proofKind get clusters | Select-String -SimpleMatch $ClusterName | ForEach-Object {
        Invoke-ProofCommand $proofKind @('delete', 'cluster', '--name', $ClusterName)
    }
    Write-Host 'Creating cgroup-v1-compatible kind v1.34.8 cluster from the release-pinned node digest...'
    Invoke-ProofCommand $proofKind @(
        'create', 'cluster', '--name', $ClusterName, '--config', $proofKindConfig,
        '--image', 'kindest/node:v1.34.8@sha256:02722c2dedddcfc00febf5d27fbeb9b7b2c14294c82109ff4a85d89ac9ba3256',
        '--wait', '180s'
    )
    foreach ($image in @(
        'launchforge-migrator:prompt12', 'launchforge-management:prompt12',
        'launchforge-config-edge:prompt12', 'launchforge-event-worker:prompt12',
        'launchforge-web:prompt12'
    )) {
        Invoke-ProofCommand $proofKind @('load', 'docker-image', '--name', $ClusterName, $image)
    }

    Write-Host "Resetting only the isolated $proofComposeProject dependency volumes..."
    Invoke-ProofCommand docker @(
        'compose', '--project-directory', $proofRepoRoot, '--project-name', $proofComposeProject,
        '--profile', 'identity', '--profile', 'distribution', 'down', '--volumes', '--remove-orphans'
    )
    Write-Host 'Starting external PostgreSQL, OIDC, Kafka, and Redis dependencies...'
    Invoke-ProofCommand docker @(
        'compose', '--project-name', $proofComposeProject, '--profile', 'identity', '--profile', 'distribution',
        'up', '-d', '--wait', '--force-recreate', 'postgres', 'keycloak', 'kafka', 'redis'
    )

    Invoke-ProofCommand $proofKubectl @('--context', "kind-$ClusterName", 'create', 'namespace', $proofNamespace)
    Invoke-ProofCommand $proofKubectl @(
        '--context', "kind-$ClusterName", '-n', $proofNamespace, 'create', 'secret', 'generic',
        'launchforge-runtime', "--from-literal=database-password=$proofDatabasePassword",
        "--from-literal=sdk-key-pepper=$proofPepper", '--from-literal=clickhouse-password=unused-local-proof',
        "--from-literal=redis-management-password=$proofRedisManagementPassword",
        "--from-literal=redis-config-edge-password=$proofRedisConfigEdgePassword",
        "--from-literal=redis-event-worker-password=$proofRedisEventWorkerPassword",
        "--from-literal=materialization-signing-private-key=$proofMaterializationPrivateKey",
        "--from-literal=materialization-verification-keys=$proofMaterializationVerificationKeys"
    )

    Write-Host 'Installing Helm release; the pre-install migration Job blocks workload creation...'
    Invoke-ProofCommand $proofHelm @(
        'upgrade', '--install', $proofRelease, $proofChart, '--namespace', $proofNamespace,
        '--values', $proofValues, '--wait', '--timeout', '10m'
    )
    Invoke-ProofCommand $proofKubectl @('--context', "kind-$ClusterName", '-n', $proofNamespace, 'wait', '--for=condition=available', '--timeout=300s', "deployment/$proofFullname-management", "deployment/$proofFullname-config-edge", "deployment/$proofFullname-event-worker", "deployment/$proofFullname-web")

    $migrationJob = (& $proofKubectl --context "kind-$ClusterName" -n $proofNamespace get jobs -l app.kubernetes.io/component=migrations -o 'jsonpath={.items[0].metadata.name}')
    Invoke-ProofCommand $proofKubectl @('--context', "kind-$ClusterName", '-n', $proofNamespace, 'wait', '--for=condition=complete', '--timeout=60s', "job/$migrationJob")
    $migrationCount = (& docker compose --project-name $proofComposeProject exec -T postgres psql -At -U launchforge -d launchforge -c 'SELECT count(*) FROM flyway_schema_history WHERE success')
    Write-Host "Migration evidence: job=$migrationJob successful-migrations=$migrationCount"

    Invoke-ProofSeed $proofRevisionOne
    Wait-ProofCondition -Description 'Redis revision 1 projection' -Attempts 60 -Condition {
        $revision = (& docker compose --project-name $proofComposeProject exec -T `
            -e REDISCLI_AUTH=$proofRedisEventWorkerPassword redis `
            redis-cli --user launchforge-event-worker HGET `
            'launchforge:config:snapshot:63000000-0000-0000-0000-000000000001' revision)
        return $revision.Trim() -eq '1'
    }

    Start-ProofPortForward
    $env:LAUNCHFORGE_BASE_URI = 'http://127.0.0.1:18082'
    $env:LAUNCHFORGE_SDK_KEY = $proofSdkKey
    $env:LAUNCHFORGE_STREAMING = 'true'
    $demoJar = Join-Path $proofRepoRoot 'demos\spring-demo\target\launchforge-spring-demo-0.1.0-SNAPSHOT-exec.jar'
    $proofDemoProcess = Start-Process -FilePath 'java' -ArgumentList @('-jar', $demoJar, '--server.port=18080') -RedirectStandardOutput (Join-Path $env:TEMP 'launchforge-prompt12-demo.out.log') -RedirectStandardError (Join-Path $env:TEMP 'launchforge-prompt12-demo.err.log') -WindowStyle Hidden -PassThru
    Wait-ProofDemoRevision 1
    $initial = Get-ProofDemoResult
    if (-not $initial.newCheckout -or $initial.snapshotRevision -ne 1) {
        throw 'Initial SDK evaluation did not use published revision 1.'
    }
    Write-Host 'SDK evidence: revision 1 evaluated newCheckout=true locally.'

    Stop-ProofProcess $proofPortForwardProcess
    $proofPortForwardProcess = $null
    $edgePod = (& $proofKubectl --context "kind-$ClusterName" -n $proofNamespace get pods -l app.kubernetes.io/component=config-edge -o 'jsonpath={.items[0].metadata.name}')
    Invoke-ProofCommand $proofKubectl @('--context', "kind-$ClusterName", '-n', $proofNamespace, 'delete', 'pod', $edgePod, '--wait=false')
    $duringEdgeRestart = Get-ProofDemoResult
    if (-not $duringEdgeRestart.newCheckout -or $duringEdgeRestart.snapshotRevision -ne 1) {
        throw 'SDK last-known-good evaluation changed during the Config Edge outage.'
    }
    Write-Host 'Resilience evidence: SDK retained revision 1 while Config Edge was unreachable.'
    Invoke-ProofCommand $proofKubectl @('--context', "kind-$ClusterName", '-n', $proofNamespace, 'rollout', 'status', '--timeout=300s', "deployment/$proofFullname-config-edge")
    Start-ProofPortForward

    Invoke-ProofSeed $proofRevisionTwo
    Wait-ProofDemoRevision 2
    $updated = Get-ProofDemoResult
    if ($updated.newCheckout -or $updated.snapshotRevision -ne 2) {
        throw 'SDK did not reconnect and advance to published revision 2.'
    }
    Write-Host 'Reconnect evidence: SDK advanced to revision 2 and evaluated newCheckout=false.'

    Stop-ProofProcess $proofPortForwardProcess
    $proofPortForwardProcess = $null
    foreach ($component in @('management', 'config-edge', 'event-worker', 'web')) {
        Invoke-ProofCommand $proofKubectl @('--context', "kind-$ClusterName", '-n', $proofNamespace, 'rollout', 'restart', "deployment/$proofFullname-$component")
    }
    $duringRollout = Get-ProofDemoResult
    if ($duringRollout.newCheckout -or $duringRollout.snapshotRevision -ne 2) {
        throw 'SDK last-known-good evaluation changed during rolling replacement.'
    }
    foreach ($component in @('management', 'config-edge', 'event-worker', 'web')) {
        Invoke-ProofCommand $proofKubectl @('--context', "kind-$ClusterName", '-n', $proofNamespace, 'rollout', 'status', '--timeout=300s', "deployment/$proofFullname-$component")
    }
    Start-ProofPortForward
    Wait-ProofDemoRevision 2
    $databaseRevision = (& docker compose --project-name $proofComposeProject exec -T postgres psql -At -U launchforge -d launchforge -c "SELECT current_revision FROM environments WHERE id = '63000000-0000-0000-0000-000000000001'").Trim()
    $redisRevision = (& docker compose --project-name $proofComposeProject exec -T `
        -e REDISCLI_AUTH=$proofRedisEventWorkerPassword redis `
        redis-cli --user launchforge-event-worker HGET `
        'launchforge:config:snapshot:63000000-0000-0000-0000-000000000001' revision).Trim()
    if ($databaseRevision -ne '2' -or $redisRevision -ne '2') {
        throw "Revision mismatch after rolling replacement: PostgreSQL=$databaseRevision Redis=$redisRevision"
    }
    Write-Host 'PASS: migration ordering, edge restart, rolling replacement, SDK reconnect/LKG, and revision 2 correctness verified.'
} finally {
    Stop-ProofProcess $proofPortForwardProcess
    Stop-ProofProcess $proofDemoProcess
    if (-not $KeepCluster) {
        & $proofKind delete cluster --name $ClusterName
        & docker compose --project-directory $proofRepoRoot --project-name $proofComposeProject --profile identity --profile distribution down --volumes --remove-orphans
    } else {
        Write-Host "Cluster kind-$ClusterName and Compose dependencies were retained for inspection."
    }
    Pop-Location
}
