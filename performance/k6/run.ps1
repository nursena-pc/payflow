param(
    [ValidateSet(
        'harness-smoke',
        'account-action-request',
        'mfa-challenge-confirm',
        'step-up-grant'
    )]
    [string] $Scenario = 'harness-smoke',

    [ValidatePattern('^[a-z0-9][a-z0-9_-]{0,62}$')]
    [string] $ProjectName = 'payflow-performance'
)

$ErrorActionPreference = 'Stop'

$HadGrafanaAdminPassword = Test-Path Env:GRAFANA_ADMIN_PASSWORD
$PreviousGrafanaAdminPassword = $env:GRAFANA_ADMIN_PASSWORD

if (-not $HadGrafanaAdminPassword) {
    $env:GRAFANA_ADMIN_PASSWORD = 'payflow-performance-compose-validation-only'
}

function Assert-NativeSuccess {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Step
    )

    if ($LASTEXITCODE -ne 0) {
        throw "$Step failed with exit code $LASTEXITCODE."
    }
}

$RepositoryRoot = (
    Resolve-Path (Join-Path $PSScriptRoot '..\..')
).Path

$ResultsDirectory = Join-Path $RepositoryRoot 'performance\results'

New-Item `
    -ItemType Directory `
    -Force `
    -Path $ResultsDirectory `
    | Out-Null

$ComposeArguments = @(
    '-p', $ProjectName,
    '-f', 'compose.yml',
    '-f', 'performance/k6/compose.yml',
    '--profile', 'app',
    '--profile', 'loadtest'
)

$ScenarioFile = switch ($Scenario) {
    'harness-smoke' {
        '/work/scenarios/harness-smoke.js'
    }
    'account-action-request' {
        '/work/scenarios/account-action-request.js'
    }
    'mfa-challenge-confirm' {
        '/work/scenarios/mfa-challenge-confirm.js'
    }
    'step-up-grant' {
        '/work/scenarios/step-up-grant.js'
    }
    default {
        throw "Unsupported scenario: $Scenario"
    }
}

Push-Location $RepositoryRoot

try {
    Write-Host "`n=== K6 VERSION ===" -ForegroundColor Cyan

    docker compose @ComposeArguments run --rm --no-deps k6 version
    Assert-NativeSuccess 'k6 version check'

    Write-Host "`n=== COMPOSE VALIDATION ===" -ForegroundColor Cyan

    docker compose @ComposeArguments config --quiet
    Assert-NativeSuccess 'Docker Compose validation'

    Write-Host "`n=== HARNESS PREREQUISITE ===" -ForegroundColor Cyan
    Write-Host 'The PayFlow app stack must already be healthy.'
    Write-Host 'This runner never starts or mutates the application stack.'

    Write-Host "`n=== RUN $Scenario ===" -ForegroundColor Cyan

    docker compose @ComposeArguments run --rm --no-deps k6 run $ScenarioFile
    Assert-NativeSuccess "k6 scenario $Scenario"
}
finally {
    Pop-Location

    if ($HadGrafanaAdminPassword) {
        $env:GRAFANA_ADMIN_PASSWORD = $PreviousGrafanaAdminPassword
    }
    else {
        Remove-Item `
            Env:GRAFANA_ADMIN_PASSWORD `
            -ErrorAction SilentlyContinue
    }
}
