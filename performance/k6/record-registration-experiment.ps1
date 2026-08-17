param(
    [ValidatePattern('^[a-z0-9][a-z0-9_-]{0,62}$')]
    [string] $ProjectName = 'payflow-performance-registration',

    [ValidateRange(1, 65535)]
    [int] $AppPort = 18084,

    [ValidateRange(1, 65535)]
    [int] $MailpitPort = 18028
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$SaturationP95Ms = 1500.0
$SaturationUnexpectedFailureRate = 0.01
$MinimumAchievementRatio = 0.95
$RecoveryBudgetSeconds = 30
$RampRates = @(2, 4, 8, 16)

function Assert-NativeSuccess {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Step
    )

    if ($LASTEXITCODE -ne 0) {
        throw "$Step failed with exit code $LASTEXITCODE."
    }
}

function New-LocalTestKey {
    $Bytes = New-Object byte[] 32
    $Rng = [Security.Cryptography.RandomNumberGenerator]::Create()

    try {
        $Rng.GetBytes($Bytes)
        return [Convert]::ToBase64String($Bytes)
    }
    finally {
        [Array]::Clear($Bytes, 0, $Bytes.Length)
        $Rng.Dispose()
    }
}

function New-RegistrationPassword {
    $Bytes = New-Object byte[] 16
    $Rng = [Security.Cryptography.RandomNumberGenerator]::Create()

    try {
        $Rng.GetBytes($Bytes)
        return 'Pf!' + (
            ($Bytes | ForEach-Object {
                $_.ToString('x2')
            }) -join ''
        )
    }
    finally {
        [Array]::Clear($Bytes, 0, $Bytes.Length)
        $Rng.Dispose()
    }
}

function Save-Environment {
    param(
        [Parameter(Mandatory = $true)]
        [string[]] $Names
    )

    $Saved = @{}

    foreach ($Name in $Names) {
        $Path = "Env:$Name"
        $Exists = Test-Path $Path

        $Saved[$Name] = [pscustomobject] @{
            Exists = $Exists
            Value = if ($Exists) {
                (Get-Item $Path).Value
            }
            else {
                $null
            }
        }
    }

    return $Saved
}

function Restore-Environment {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable] $Saved
    )

    foreach ($Name in $Saved.Keys) {
        if ($Saved[$Name].Exists) {
            Set-Item `
                -Path "Env:$Name" `
                -Value $Saved[$Name].Value
        }
        else {
            Remove-Item `
                -Path "Env:$Name" `
                -ErrorAction SilentlyContinue
        }
    }
}

function Wait-HttpSuccess {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Url,

        [Parameter(Mandatory = $true)]
        [string] $Name,

        [int] $TimeoutSeconds = 120
    )

    $Deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)

    while ([DateTime]::UtcNow -lt $Deadline) {
        try {
            $Response = Invoke-WebRequest `
                -Uri $Url `
                -UseBasicParsing `
                -TimeoutSec 5 `
                -ErrorAction Stop

            if (
                $Response.StatusCode -ge 200 -and
                $Response.StatusCode -lt 300
            ) {
                Write-Host "$Name PASS." -ForegroundColor Green
                return
            }
        }
        catch {
            # Bounded readiness polling.
        }

        Start-Sleep -Seconds 2
    }

    throw "$Name did not become healthy within the bounded wait."
}

function Get-SummaryMetricValue {
    param(
        [Parameter(Mandatory = $true)]
        $Summary,

        [Parameter(Mandatory = $true)]
        [string] $MetricName,

        [Parameter(Mandatory = $true)]
        [string] $ValueName,

        [double] $DefaultValue = [double]::NaN
    )

    $MetricsProperty = $Summary.PSObject.Properties['metrics']

    if ($null -eq $MetricsProperty) {
        throw 'k6 compatibility summary metrics object is missing.'
    }

    $MetricProperty =
        $MetricsProperty.Value.PSObject.Properties[$MetricName]

    if ($null -eq $MetricProperty) {
        if (-not [double]::IsNaN($DefaultValue)) {
            return $DefaultValue
        }

        throw "Summary metric is missing: $MetricName"
    }

    $ValueProperty =
        $MetricProperty.Value.PSObject.Properties[$ValueName]

    if (
        $null -eq $ValueProperty -and
        [string]::Equals(
            $ValueName,
            'rate',
            [StringComparison]::Ordinal
        )
    ) {
        $ValueProperty =
            $MetricProperty.Value.PSObject.Properties['value']
    }

    if ($null -eq $ValueProperty) {
        if (-not [double]::IsNaN($DefaultValue)) {
            return $DefaultValue
        }

        throw "Summary metric value is missing: $MetricName.$ValueName"
    }

    return [double] $ValueProperty.Value
}

function Format-Decimal {
    param(
        [double] $Value,
        [string] $Pattern = '0.00'
    )

    return $Value.ToString(
        $Pattern,
        [Globalization.CultureInfo]::InvariantCulture
    )
}

function Get-AppResourceSnapshot {
    param(
        [Parameter(Mandatory = $true)]
        [string[]] $ComposeArguments
    )

    $ContainerId = @(
        docker compose @ComposeArguments ps -q app
    ) | Select-Object -First 1
    Assert-NativeSuccess 'resolve registration app container'

    if ([string]::IsNullOrWhiteSpace([string] $ContainerId)) {
        throw 'Registration app container ID could not be resolved.'
    }

    $StatsLine = @(
        docker stats `
            --no-stream `
            --format '{{.CPUPerc}}|{{.MemUsage}}' `
            $ContainerId
    ) | Select-Object -First 1
    Assert-NativeSuccess 'registration app resource snapshot'

    $Parts = ([string] $StatsLine).Split('|')

    if ($Parts.Count -ne 2) {
        throw 'Docker resource snapshot did not match the bounded format.'
    }

    $CpuText = $Parts[0].Trim().TrimEnd('%')

    $CpuPercent = [double]::Parse(
        $CpuText,
        [Globalization.CultureInfo]::InvariantCulture
    )

    return [pscustomobject] @{
        cpuPercent = [Math]::Round($CpuPercent, 3)
        memoryUsage = $Parts[1].Trim()
    }
}

$RepositoryRoot = (
    Resolve-Path (Join-Path $PSScriptRoot '..\..')
).Path

$ResultsRoot = Join-Path `
    $RepositoryRoot `
    'performance\results\registration'

$HealthUrl = "http://localhost:$AppPort/api/v1/system/health"
$RunId = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')
$RunDirectory = Join-Path $ResultsRoot $RunId

$CandidateJsonPath = Join-Path `
    $RunDirectory `
    'candidate-registration-experiment.json'

$CandidateMarkdownPath = Join-Path `
    $RunDirectory `
    'candidate-registration-experiment.md'

$ComposeArguments = @(
    '-p', $ProjectName,
    '-f', 'compose.yml',
    '-f', 'performance/k6/compose.yml',
    '--profile', 'app',
    '--profile', 'loadtest'
)

function Invoke-RegistrationPhase {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Name,

        [Parameter(Mandatory = $true)]
        [int] $Rate,

        [Parameter(Mandatory = $true)]
        [int] $DurationSeconds,

        [Parameter(Mandatory = $true)]
        [string] $EmailPrefix
    )

    Write-Host "`n=== REGISTRATION PHASE $Name ===" `
        -ForegroundColor Cyan
    Write-Host "Rate     : $Rate registrations/s"
    Write-Host "Duration : ${DurationSeconds}s"

    $env:PAYFLOW_K6_REGISTRATION_RATE = [string] $Rate
    $env:PAYFLOW_K6_REGISTRATION_DURATION =
        "${DurationSeconds}s"
    $env:PAYFLOW_K6_REGISTRATION_PRE_ALLOCATED_VUS =
        [string] ([Math]::Max(8, $Rate * 2))
    $env:PAYFLOW_K6_REGISTRATION_MAX_VUS =
        [string] ([Math]::Max(64, $Rate * 8))
    $env:K6_REGISTRATION_EMAIL_PREFIX = $EmailPrefix

    $SummaryFileName = "$Name.summary.json"
    $SummaryContainerPath =
        "/results/registration/$RunId/$SummaryFileName"
    $SummaryHostPath = Join-Path `
        $RunDirectory `
        $SummaryFileName

    & (Join-Path $PSScriptRoot 'run.ps1') `
        -Scenario registration-experiment `
        -ProjectName $ProjectName `
        -SummaryExportPath $SummaryContainerPath |
        Out-Host

    if (-not (Test-Path -LiteralPath $SummaryHostPath)) {
        throw "k6 registration summary is missing for phase $Name."
    }

    $Summary = Get-Content `
        -LiteralPath $SummaryHostPath `
        -Raw |
        ConvertFrom-Json

    $RequestCount = [int] [Math]::Round(
        (
            Get-SummaryMetricValue `
                -Summary $Summary `
                -MetricName 'payflow_registration_requests' `
                -ValueName 'count'
        )
    )

    $CreatedCount = [int] [Math]::Round(
        (
            Get-SummaryMetricValue `
                -Summary $Summary `
                -MetricName 'payflow_registration_created' `
                -ValueName 'count'
        )
    )

    $P50 = Get-SummaryMetricValue `
        -Summary $Summary `
        -MetricName 'payflow_registration_request_duration' `
        -ValueName 'med'

    $P95 = Get-SummaryMetricValue `
        -Summary $Summary `
        -MetricName 'payflow_registration_request_duration' `
        -ValueName 'p(95)'

    $P99 = Get-SummaryMetricValue `
        -Summary $Summary `
        -MetricName 'payflow_registration_request_duration' `
        -ValueName 'p(99)'

    $UnexpectedFailureRate = Get-SummaryMetricValue `
        -Summary $Summary `
        -MetricName 'payflow_registration_unexpected_failures' `
        -ValueName 'rate'

    $HealthFailureRate = Get-SummaryMetricValue `
        -Summary $Summary `
        -MetricName 'payflow_registration_health_probe_failures' `
        -ValueName 'rate'

    $DroppedIterations = [int] [Math]::Round(
        (
            Get-SummaryMetricValue `
                -Summary $Summary `
                -MetricName 'dropped_iterations' `
                -ValueName 'count' `
                -DefaultValue 0
        )
    )

    $MaximumVus = [int] [Math]::Round(
        (
            Get-SummaryMetricValue `
                -Summary $Summary `
                -MetricName 'vus_max' `
                -ValueName 'value' `
                -DefaultValue 0
        )
    )

    $TargetIterations = $Rate * $DurationSeconds

    $AchievementRatio = if ($TargetIterations -gt 0) {
        $RequestCount / [double] $TargetIterations
    }
    else {
        0.0
    }

    $ResourceSnapshot = Get-AppResourceSnapshot `
        -ComposeArguments $ComposeArguments

    $Saturated = (
        $P95 -gt $SaturationP95Ms -or
        $UnexpectedFailureRate -ge
            $SaturationUnexpectedFailureRate -or
        $DroppedIterations -gt 0 -or
        $HealthFailureRate -gt 0
    )

    Write-Host "Requests : $RequestCount"
    Write-Host "Created  : $CreatedCount"
    Write-Host "p50 ms   : $(Format-Decimal $P50)"
    Write-Host "p95 ms   : $(Format-Decimal $P95)"
    Write-Host "p99 ms   : $(Format-Decimal $P99)"
    Write-Host (
        "Achieved : " +
        "$(Format-Decimal ($AchievementRatio * 100) '0.000')%"
    )
    Write-Host "Dropped  : $DroppedIterations"
    Write-Host "App CPU  : $($ResourceSnapshot.cpuPercent)%"
    Write-Host "App memory: $($ResourceSnapshot.memoryUsage)"
    Write-Host "Saturated: $Saturated"
    Write-Host (
        'No generated email, password, response body, token, ' +
        'client address, or datastore key was printed.'
    )

    return [pscustomobject] @{
        name = $Name
        targetRatePerSecond = $Rate
        durationSeconds = $DurationSeconds
        targetIterations = $TargetIterations
        requests = $RequestCount
        created = $CreatedCount
        achievedRatePerSecond = [Math]::Round(
            $RequestCount / [double] $DurationSeconds,
            3
        )
        achievementRatio = [Math]::Round(
            $AchievementRatio,
            6
        )
        p50Ms = [Math]::Round($P50, 3)
        p95Ms = [Math]::Round($P95, 3)
        p99Ms = [Math]::Round($P99, 3)
        unexpectedFailureRate = [Math]::Round(
            $UnexpectedFailureRate,
            6
        )
        healthProbeFailureRate = [Math]::Round(
            $HealthFailureRate,
            6
        )
        droppedIterations = $DroppedIterations
        maximumVus = $MaximumVus
        postPhaseAppCpuPercent =
            $ResourceSnapshot.cpuPercent
        postPhaseAppMemoryUsage =
            $ResourceSnapshot.memoryUsage
        saturated = $Saturated
    }
}

Push-Location $RepositoryRoot

$EnvironmentNames = @(
    'MAIL_CONTENT_ENCRYPTION_KEY',
    'MFA_SECRET_ENCRYPTION_KEY',
    'GRAFANA_ADMIN_PASSWORD',
    'PAYFLOW_PERFORMANCE_APP_PORT',
    'PAYFLOW_PERFORMANCE_MAILPIT_PORT',
    'ABUSE_PROTECTION_ENABLED',
    'PAYFLOW_K6_REGISTRATION_RATE',
    'PAYFLOW_K6_REGISTRATION_DURATION',
    'PAYFLOW_K6_REGISTRATION_PRE_ALLOCATED_VUS',
    'PAYFLOW_K6_REGISTRATION_MAX_VUS',
    'K6_REGISTRATION_EMAIL_PREFIX',
    'K6_REGISTRATION_PASSWORD'
)

$SavedEnvironment = Save-Environment `
    -Names $EnvironmentNames

$StackAttempted = $false

try {
    Write-Host `
        '=== REGISTRATION PERFORMANCE EXPERIMENT RECORDER ===' `
        -ForegroundColor Cyan

    $Branch = git branch --show-current
    Assert-NativeSuccess 'git branch --show-current'

    $Commit = git rev-parse HEAD
    Assert-NativeSuccess 'git rev-parse HEAD'

    $Dirty = @(
        git status --porcelain=v1 -uall
    )
    Assert-NativeSuccess 'git status --porcelain=v1 -uall'

    if ($Dirty.Count -ne 0) {
        throw (
            'Registration evidence requires a clean Git ' +
            'working tree.'
        )
    }

    if (
        [string]::IsNullOrWhiteSpace($Branch) -or
        [string]::IsNullOrWhiteSpace($Commit)
    ) {
        throw 'Git registration evidence identity could not be resolved.'
    }

    New-Item `
        -ItemType Directory `
        -Force `
        -Path $RunDirectory |
        Out-Null

    $env:MAIL_CONTENT_ENCRYPTION_KEY =
        New-LocalTestKey
    $env:MFA_SECRET_ENCRYPTION_KEY =
        New-LocalTestKey
    $env:GRAFANA_ADMIN_PASSWORD =
        'payflow-registration-evidence-local-only'
    $env:PAYFLOW_PERFORMANCE_APP_PORT =
        [string] $AppPort
    $env:PAYFLOW_PERFORMANCE_MAILPIT_PORT =
        [string] $MailpitPort

    # Keep the generalized feature enabled while measuring the
    # currently-unwired registration endpoint.
    $env:ABUSE_PROTECTION_ENABLED = 'true'

    $env:K6_REGISTRATION_PASSWORD =
        New-RegistrationPassword

    $HostOs = [Environment]::OSVersion.VersionString
    $PowerShellVersion =
        $PSVersionTable.PSVersion.ToString()

    $DockerServerVersion = @(
        docker version --format '{{.Server.Version}}'
    ) | Select-Object -First 1
    Assert-NativeSuccess 'Docker server version'

    $DockerInfo = @(
        docker info `
            --format '{{.NCPU}}|{{.MemTotal}}|{{.OperatingSystem}}'
    ) | Select-Object -First 1
    Assert-NativeSuccess 'Docker resource metadata'

    $DockerParts = ([string] $DockerInfo).Split('|')

    if ($DockerParts.Count -ne 3) {
        throw (
            'Docker resource metadata did not match the ' +
            'bounded format.'
        )
    }

    $DockerCpu = [int] $DockerParts[0]

    $DockerMemoryBytes = [double]::Parse(
        $DockerParts[1],
        [Globalization.CultureInfo]::InvariantCulture
    )

    $DockerMemoryGiB = [Math]::Round(
        $DockerMemoryBytes / 1GB,
        2
    )

    $DockerOperatingSystem = $DockerParts[2]

    docker compose `
        @ComposeArguments `
        down -v --remove-orphans
    Assert-NativeSuccess 'clean registration project'

    docker compose `
        @ComposeArguments `
        config --quiet
    Assert-NativeSuccess 'registration Compose validation'

    $StackAttempted = $true

    docker compose `
        @ComposeArguments `
        up -d --build postgres redis kafka mailpit app
    Assert-NativeSuccess 'start registration project'

    Wait-HttpSuccess `
        -Url $HealthUrl `
        -Name 'Registration PayFlow health'

    $JavaLines = @(
        docker compose `
            @ComposeArguments `
            exec -T app java --version
    )
    Assert-NativeSuccess 'Java runtime version'

    $JavaVersion = [string] (
        $JavaLines |
            Select-Object -First 1
    )

    $K6Lines = @(
        docker compose `
            @ComposeArguments `
            run --rm --no-deps k6 version
    )
    Assert-NativeSuccess 'k6 runtime version'

    $K6Version = [string] (
        $K6Lines |
            Select-Object -First 1
    )

    $Phases =
        New-Object System.Collections.Generic.List[object]

    $Warmup = Invoke-RegistrationPhase `
        -Name 'warmup' `
        -Rate 1 `
        -DurationSeconds 10 `
        -EmailPrefix 'pf-reg-warmup'
    $Phases.Add($Warmup)

    $Baseline = Invoke-RegistrationPhase `
        -Name 'baseline' `
        -Rate 1 `
        -DurationSeconds 30 `
        -EmailPrefix 'pf-reg-baseline'
    $Phases.Add($Baseline)

    $FirstSaturatedRate = $null

    foreach ($Rate in $RampRates) {
        $Phase = Invoke-RegistrationPhase `
            -Name "ramp-$Rate" `
            -Rate $Rate `
            -DurationSeconds 20 `
            -EmailPrefix "pf-reg-r$Rate"

        $Phases.Add($Phase)

        if (
            $null -eq $FirstSaturatedRate -and
            $Phase.saturated
        ) {
            $FirstSaturatedRate = $Rate
        }
    }

    Write-Host "`n=== REGISTRATION RECOVERY ===" `
        -ForegroundColor Cyan

    $RecoveryWatch =
        [Diagnostics.Stopwatch]::StartNew()

    $Recovered = $false

    while (
        $RecoveryWatch.Elapsed.TotalSeconds -le
        $RecoveryBudgetSeconds
    ) {
        try {
            $RecoveryResponse = Invoke-WebRequest `
                -Uri $HealthUrl `
                -UseBasicParsing `
                -TimeoutSec 5 `
                -ErrorAction Stop

            if (
                $RecoveryResponse.StatusCode -ge 200 -and
                $RecoveryResponse.StatusCode -lt 300
            ) {
                $Recovered = $true
                break
            }
        }
        catch {
            # Bounded recovery polling.
        }

        Start-Sleep -Seconds 1
    }

    $RecoveryWatch.Stop()

    $RecoverySeconds = [Math]::Round(
        $RecoveryWatch.Elapsed.TotalSeconds,
        3
    )

    if (-not $Recovered) {
        throw (
            'PayFlow did not recover within the 30-second ' +
            'registration experiment budget.'
        )
    }

    $BaselineComparable = (
        $Baseline.unexpectedFailureRate -eq 0 -and
        $Baseline.droppedIterations -eq 0 -and
        $Baseline.healthProbeFailureRate -eq 0 -and
        $Baseline.achievementRatio -ge
            $MinimumAchievementRatio
    )

    $Candidate = [ordered] @{
        schemaVersion = 1
        generatedAtUtc =
            [DateTime]::UtcNow.ToString('o')
        gitCommit = $Commit
        branch = $Branch
        environment = [ordered] @{
            hostOs = $HostOs
            powershell = $PowerShellVersion
            dockerServer =
                ([string] $DockerServerVersion).Trim()
            dockerOperatingSystem =
                $DockerOperatingSystem
            dockerCpuCount = $DockerCpu
            dockerMemoryGiB = $DockerMemoryGiB
            javaRuntime = $JavaVersion.Trim()
            k6Runtime = $K6Version.Trim()
            composeFiles = @(
                'compose.yml',
                'performance/k6/compose.yml'
            )
            profiles = @('app', 'loadtest')
            abuseProtectionFeatureEnabled = $true
            registrationProtectionWired = $false
        }
        dataset = [ordered] @{
            workflow = 'registration'
            identities =
                'disposable example.invalid synthetic identities'
            password =
                'runtime-generated and never persisted in evidence'
            setupCostIncludedInMeasuredLatency = $true
            databaseResetBetweenPhases = $false
        }
        contract = [ordered] @{
            endpoint = 'POST /api/v1/auth/register'
            existingPublicStatuses = @(201, 400, 409)
            warmup = '10s @ 1 registration/s'
            baseline = '30s @ 1 registration/s'
            rampRates = $RampRates
            rampStageSeconds = 20
            saturationP95Ms = $SaturationP95Ms
            saturationUnexpectedFailureRate =
                $SaturationUnexpectedFailureRate
            recoveryBudgetSeconds =
                $RecoveryBudgetSeconds
        }
        outcome = [ordered] @{
            baselineComparable = $BaselineComparable
            firstSaturatedRate = $FirstSaturatedRate
            recoverySeconds = $RecoverySeconds
            recoveredWithinBudget = $Recovered
            decision = 'REVIEW_REQUIRED'
        }
        phases = $Phases.ToArray()
        limitations = @(
            'Developer-workstation experiment only; not a production capacity certification.',
            'Registration includes BCrypt hashing, user persistence, account-action credential preparation, and verification-mail enqueue work.',
            'Post-phase Docker CPU and memory values are bounded snapshots, not sustained or peak resource telemetry.',
            'The database is intentionally not reset between phases so every generated registration remains unique within one fresh experiment stack.',
            'This recorder measures the currently-unwired registration path and does not activate generalized registration protection.',
            'The ACTIVATE or DEFER decision requires explicit review of this candidate; the recorder never changes production wiring automatically.'
        )
    }

    $Utf8NoBom = [Text.UTF8Encoding]::new($false)

    $CandidateJson =
        $Candidate |
        ConvertTo-Json -Depth 10

    [IO.File]::WriteAllText(
        $CandidateJsonPath,
        $CandidateJson + "`n",
        $Utf8NoBom
    )

    $Rows =
        New-Object System.Collections.Generic.List[string]

    foreach ($Phase in $Candidate.phases) {
        $Rows.Add(
            "| $($Phase.name) | " +
            "$($Phase.targetRatePerSecond) | " +
            "$($Phase.durationSeconds) | " +
            "$($Phase.requests) | " +
            "$($Phase.created) | " +
            "$(Format-Decimal $Phase.p50Ms) | " +
            "$(Format-Decimal $Phase.p95Ms) | " +
            "$(Format-Decimal $Phase.p99Ms) | " +
            "$(Format-Decimal ($Phase.achievementRatio * 100) '0.000')% | " +
            "$(Format-Decimal ($Phase.unexpectedFailureRate * 100) '0.000')% | " +
            "$($Phase.droppedIterations) | " +
            "$($Phase.postPhaseAppCpuPercent)% | " +
            "$($Phase.saturated) |"
        )
    }

    $FirstSaturationText = if (
        $null -eq $Candidate.outcome.firstSaturatedRate
    ) {
        'not observed through 16 registrations/s'
    }
    else {
        (
            "$($Candidate.outcome.firstSaturatedRate) " +
            'registrations/s'
        )
    }

    $Markdown = @"
# Candidate registration performance experiment

> Candidate only. Generated under ignored performance/results/
> and requires explicit review before any tracked decision.

- Git commit: $Commit
- Generated UTC: $($Candidate.generatedAtUtc)
- Endpoint: POST /api/v1/auth/register
- Registration protection wired: false
- Generalized abuse-protection feature: enabled
- Host OS: $HostOs
- Docker server: $(([string] $DockerServerVersion).Trim())
- Docker environment: $DockerOperatingSystem; $DockerCpu CPU; $DockerMemoryGiB GiB
- Java runtime: $($JavaVersion.Trim())
- k6 runtime: $($K6Version.Trim())
- Dataset: disposable example.invalid identities; runtime-only generated password

| Phase | Target reg/s | Seconds | Requests | 201 Created | p50 ms | p95 ms | p99 ms | Achieved | Unexpected | Dropped | Post-phase app CPU | Saturated |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
$($Rows -join "`n")

Baseline comparable: **$BaselineComparable**.
First saturation: **$FirstSaturationText**.
Recovery: **$RecoverySeconds seconds** within the 30-second budget.
Decision: **REVIEW_REQUIRED**.

## Limitations

- Developer-workstation experiment only; not production capacity certification.
- Registration includes BCrypt hashing, persistence, account-action credential preparation, and verification-mail enqueue work.
- Docker CPU and memory values are post-phase snapshots rather than sustained or peak telemetry.
- The database is not reset between phases; the experiment uses unique disposable identities in one fresh stack.
- This run does not wire generalized abuse protection into registration.
- ACTIVATE or DEFER remains an explicit review decision and is never applied automatically.
"@

    [IO.File]::WriteAllText(
        $CandidateMarkdownPath,
        $Markdown.Trim() + "`n",
        $Utf8NoBom
    )

    Write-Host `
        "`n=== REGISTRATION CANDIDATE COMPLETE ===" `
        -ForegroundColor Green

    Write-Host "Baseline comparable: $BaselineComparable"
    Write-Host "First saturation  : $FirstSaturationText"
    Write-Host "Recovery seconds  : $RecoverySeconds"
    Write-Host (
        "Candidate JSON     : " +
        "performance/results/registration/$RunId/" +
        'candidate-registration-experiment.json'
    )
    Write-Host (
        "Candidate MD       : " +
        "performance/results/registration/$RunId/" +
        'candidate-registration-experiment.md'
    )
    Write-Host (
        'Decision remains REVIEW_REQUIRED; no production ' +
        'registration wiring was changed.'
    )
}
finally {
    if ($StackAttempted) {
        docker compose `
            @ComposeArguments `
            down -v --remove-orphans

        if ($LASTEXITCODE -ne 0) {
            Write-Warning (
                'Registration experiment project cleanup ' +
                'returned a non-zero exit code.'
            )
        }
    }

    Restore-Environment `
        -Saved $SavedEnvironment

    Pop-Location
}
