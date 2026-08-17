param(
    [ValidateSet(
        'harness-smoke',
        'account-action-request',
        'account-action-quota-pressure',
        'account-action-evidence',
        'mfa-challenge-confirm',
        'step-up-grant'
    )]
    [string] $Scenario = 'harness-smoke',

    [ValidatePattern('^[a-z0-9][a-z0-9_-]{0,62}$')]
    [string] $ProjectName = 'payflow-performance',

    [string] $SummaryExportPath = ''
)

$ErrorActionPreference = 'Stop'

if (-not [string]::IsNullOrWhiteSpace($SummaryExportPath)) {
    if (
        $SummaryExportPath -notmatch '^/results/[A-Za-z0-9][A-Za-z0-9._/-]*\.json$' -or
        $SummaryExportPath.Contains('..') -or
        $SummaryExportPath.Contains('\\')
    ) {
        throw 'SummaryExportPath must be a bounded JSON path under /results/.'
    }
}

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
    'account-action-quota-pressure' {
        '/work/scenarios/account-action-quota-pressure.js'
    }
    'account-action-evidence' {
        '/work/scenarios/account-action-evidence.js'
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

    $EffectiveComposeLines = @(
        docker compose @ComposeArguments config
    )
    Assert-NativeSuccess 'Docker Compose validation'

    $EffectiveCompose = $EffectiveComposeLines -join "`n"
    $ReservedExecutionOptions = @(
        'K6_DURATION'
        'K6_VUS'
        'K6_ITERATIONS'
        'K6_STAGES'
    )

    foreach ($ReservedName in $ReservedExecutionOptions) {
        $ReservedPattern = '(?m)^\s+' + [regex]::Escape($ReservedName) + ':\s*'

        if ($EffectiveCompose -match $ReservedPattern) {
            throw "Effective k6 container environment exports reserved execution option $ReservedName; script scenarios would be overridden."
        }
    }

    Write-Host "`n=== HARNESS PREREQUISITE ===" -ForegroundColor Cyan
    Write-Host 'The PayFlow app stack must already be healthy.'
    Write-Host 'This runner never starts or mutates the application stack.'

    Write-Host "`n=== RUN $Scenario ===" -ForegroundColor Cyan

    $K6Arguments = @('run')

    if (-not [string]::IsNullOrWhiteSpace($SummaryExportPath)) {
        $K6Arguments += @(
            '--summary-trend-stats',
            'med,p(95),p(99)',
            '--summary-export',
            $SummaryExportPath
        )
    }

    $K6Arguments += $ScenarioFile

    docker compose @ComposeArguments run --rm --no-deps k6 @K6Arguments
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
