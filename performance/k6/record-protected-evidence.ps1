param(
    [ValidatePattern('^[a-z0-9][a-z0-9_-]{0,62}$')]
    [string] $ProjectName = 'payflow-performance-evidence',

    [ValidateRange(1, 65535)]
    [int] $AppPort = 18082,

    [ValidateRange(1, 65535)]
    [int] $MailpitPort = 18026
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$SteadyP95BudgetMs = 750.0
$SteadyP99BudgetMs = 1500.0
$SteadyUnexpectedFailureBudget = 0.005
$SteadyMinimumAchievementRatio = 0.95
$SaturationP95Ms = 1500.0
$SaturationUnexpectedFailureRate = 0.01
$RecoveryBudgetSeconds = 30
$ClientDecisionLimit = 20
$SaturationRates = @(10, 20, 40, 80)

function Assert-NativeSuccess {
    param([Parameter(Mandatory = $true)][string] $Step)

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

function Save-Environment {
    param([Parameter(Mandatory = $true)][string[]] $Names)

    $Saved = @{}

    foreach ($Name in $Names) {
        $Path = "Env:$Name"
        $Exists = Test-Path $Path
        $Saved[$Name] = [pscustomobject] @{
            Exists = $Exists
            Value = if ($Exists) { (Get-Item $Path).Value } else { $null }
        }
    }

    return $Saved
}

function Restore-Environment {
    param([Parameter(Mandatory = $true)][hashtable] $Saved)

    foreach ($Name in $Saved.Keys) {
        if ($Saved[$Name].Exists) {
            Set-Item -Path "Env:$Name" -Value $Saved[$Name].Value
        }
        else {
            Remove-Item -Path "Env:$Name" -ErrorAction SilentlyContinue
        }
    }
}

function Wait-HttpSuccess {
    param(
        [Parameter(Mandatory = $true)][string] $Url,
        [Parameter(Mandatory = $true)][string] $Name,
        [int] $TimeoutSeconds = 90
    )

    $Deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)

    while ([DateTime]::UtcNow -lt $Deadline) {
        try {
            $Response = Invoke-WebRequest `
                -Uri $Url `
                -UseBasicParsing `
                -TimeoutSec 5 `
                -ErrorAction Stop

            if ($Response.StatusCode -ge 200 -and $Response.StatusCode -lt 300) {
                Write-Host "$Name PASS." -ForegroundColor Green
                return
            }
        }
        catch {
            # Bounded readiness polling; no response body is printed.
        }

        Start-Sleep -Seconds 2
    }

    throw "$Name did not become healthy within the bounded wait."
}

function Get-PrometheusText {
    param([Parameter(Mandatory = $true)][string] $MetricsUrl)

    try {
        $Response = Invoke-WebRequest `
            -Uri $MetricsUrl `
            -UseBasicParsing `
            -TimeoutSec 10 `
            -ErrorAction Stop
    }
    catch {
        throw 'Unable to read the bounded PayFlow Prometheus endpoint.'
    }

    return [string] $Response.Content
}

function Get-DecisionCounter {
    param(
        [Parameter(Mandatory = $true)][string] $Text,
        [Parameter(Mandatory = $true)][string] $Outcome,
        [Parameter(Mandatory = $true)][string] $Reason
    )

    $Metric = 'payflow_security_abuse_protection_decisions_total'
    [double] $Total = 0

    foreach ($Line in ($Text -split "`r?`n")) {
        if (-not $Line.StartsWith("$Metric{")) {
            continue
        }

        if ($Line -notlike '*workflow="email-verification-request"*') {
            continue
        }

        if ($Line -notlike "*outcome=`"$Outcome`"*") {
            continue
        }

        if ($Line -notlike "*reason=`"$Reason`"*") {
            continue
        }

        if ($Line -notmatch '\s([-+]?[0-9]+(?:\.[0-9]+)?(?:[eE][-+]?[0-9]+)?)$') {
            throw 'A bounded abuse-protection metric sample could not be parsed.'
        }

        $Total += [double]::Parse(
            $Matches[1],
            [Globalization.CultureInfo]::InvariantCulture
        )
    }

    return $Total
}

function Get-DecisionSnapshot {
    param([Parameter(Mandatory = $true)][string] $MetricsUrl)

    $Text = Get-PrometheusText -MetricsUrl $MetricsUrl

    return [pscustomobject] @{
        Allowed = Get-DecisionCounter -Text $Text -Outcome 'allowed' -Reason 'none'
        BlockedClient = Get-DecisionCounter -Text $Text -Outcome 'blocked' -Reason 'client'
        BlockedIdentity = Get-DecisionCounter -Text $Text -Outcome 'blocked' -Reason 'identity'
        BlockedBoth = Get-DecisionCounter -Text $Text -Outcome 'blocked' -Reason 'both'
        DependencyBypass = Get-DecisionCounter `
            -Text $Text `
            -Outcome 'dependency_bypass' `
            -Reason 'dependency_failure'
    }
}

function Get-DeltaInt {
    param([double] $After, [double] $Before)
    return [int] [Math]::Round($After - $Before)
}

function Get-SummaryMetricValue {
    param(
        [Parameter(Mandatory = $true)] $Summary,
        [Parameter(Mandatory = $true)][string] $MetricName,
        [Parameter(Mandatory = $true)][string] $ValueName,
        [double] $DefaultValue = [double]::NaN
    )

    $MetricProperty = $Summary.metrics.PSObject.Properties[$MetricName]

    if ($null -eq $MetricProperty) {
        if (-not [double]::IsNaN($DefaultValue)) {
            return $DefaultValue
        }

        throw "Summary metric is missing: $MetricName"
    }

    $ValueProperty = $MetricProperty.Value.values.PSObject.Properties[$ValueName]

    if ($null -eq $ValueProperty) {
        if (-not [double]::IsNaN($DefaultValue)) {
            return $DefaultValue
        }

        throw "Summary metric value is missing: $MetricName.$ValueName"
    }

    return [double] $ValueProperty.Value
}

function Format-Decimal {
    param([double] $Value, [string] $Pattern = '0.00')

    return $Value.ToString(
        $Pattern,
        [Globalization.CultureInfo]::InvariantCulture
    )
}

$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\\..')).Path
$ResultsRoot = Join-Path $RepositoryRoot 'performance\results\evidence'
$MetricsUrl = "http://localhost:$AppPort/actuator/prometheus"
$HealthUrl = "http://localhost:$AppPort/api/v1/system/health"
$RunId = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')
$RunDirectory = Join-Path $ResultsRoot $RunId
$CandidateJsonPath = Join-Path $RunDirectory 'candidate-protected-workflow-evidence.json'
$CandidateMarkdownPath = Join-Path $RunDirectory 'candidate-protected-workflow-evidence.md'

$ComposeArguments = @(
    '-p', $ProjectName,
    '-f', 'compose.yml',
    '-f', 'performance/k6/compose.yml',
    '--profile', 'app',
    '--profile', 'loadtest'
)

function Invoke-EvidencePhase {
    param(
        [Parameter(Mandatory = $true)][string] $Name,
        [Parameter(Mandatory = $true)][int] $Rate,
        [Parameter(Mandatory = $true)][int] $DurationSeconds,
        [Parameter(Mandatory = $true)][string] $DurationText,
        [Parameter(Mandatory = $true)][string] $EmailPrefix
    )

    Write-Host "`n=== PHASE $Name ===" -ForegroundColor Cyan
    Write-Host "Rate     : $Rate iterations/s"
    Write-Host "Duration : $DurationText"

    docker compose @ComposeArguments exec -T redis redis-cli FLUSHDB | Out-Null
    Assert-NativeSuccess "Redis reset before $Name"

    $Before = Get-DecisionSnapshot -MetricsUrl $MetricsUrl

    $env:PAYFLOW_K6_EVIDENCE_RATE = [string] $Rate
    $env:PAYFLOW_K6_EVIDENCE_DURATION = $DurationText
    $env:PAYFLOW_K6_EVIDENCE_PRE_ALLOCATED_VUS = [string] ([Math]::Max(40, $Rate))
    $env:PAYFLOW_K6_EVIDENCE_MAX_VUS = [string] ([Math]::Max(200, $Rate * 3))
    $env:K6_ACCOUNT_ACTION_EMAIL_PREFIX = $EmailPrefix

    $SummaryFileName = "$Name.summary.json"
    $SummaryContainerPath = "/results/evidence/$RunId/$SummaryFileName"
    $SummaryHostPath = Join-Path $RunDirectory $SummaryFileName

    & (Join-Path $PSScriptRoot 'run.ps1') `
        -Scenario account-action-evidence `
        -ProjectName $ProjectName `
        -SummaryExportPath $SummaryContainerPath

    if (-not (Test-Path -LiteralPath $SummaryHostPath)) {
        throw "k6 summary export is missing for phase $Name."
    }

    $After = Get-DecisionSnapshot -MetricsUrl $MetricsUrl
    $Summary = Get-Content -LiteralPath $SummaryHostPath -Raw | ConvertFrom-Json

    $RequestCount = [int] [Math]::Round(
        (Get-SummaryMetricValue `
            -Summary $Summary `
            -MetricName 'payflow_evidence_requests' `
            -ValueName 'count')
    )
    $P50 = Get-SummaryMetricValue `
        -Summary $Summary `
        -MetricName 'payflow_evidence_request_duration' `
        -ValueName 'med'
    $P95 = Get-SummaryMetricValue `
        -Summary $Summary `
        -MetricName 'payflow_evidence_request_duration' `
        -ValueName 'p(95)'
    $P99 = Get-SummaryMetricValue `
        -Summary $Summary `
        -MetricName 'payflow_evidence_request_duration' `
        -ValueName 'p(99)'
    $UnexpectedFailureRate = Get-SummaryMetricValue `
        -Summary $Summary `
        -MetricName 'payflow_unexpected_failures' `
        -ValueName 'rate'
    $HealthFailureRate = Get-SummaryMetricValue `
        -Summary $Summary `
        -MetricName 'payflow_health_probe_failures' `
        -ValueName 'rate'
    $DroppedIterations = [int] [Math]::Round(
        (Get-SummaryMetricValue `
            -Summary $Summary `
            -MetricName 'dropped_iterations' `
            -ValueName 'count' `
            -DefaultValue 0)
    )

    $TargetIterations = $Rate * $DurationSeconds
    $AchievementRatio = if ($TargetIterations -gt 0) {
        $RequestCount / [double] $TargetIterations
    }
    else {
        0.0
    }

    $AllowedDelta = Get-DeltaInt -After $After.Allowed -Before $Before.Allowed
    $BlockedClientDelta = Get-DeltaInt `
        -After $After.BlockedClient `
        -Before $Before.BlockedClient
    $BlockedIdentityDelta = Get-DeltaInt `
        -After $After.BlockedIdentity `
        -Before $Before.BlockedIdentity
    $BlockedBothDelta = Get-DeltaInt `
        -After $After.BlockedBoth `
        -Before $Before.BlockedBoth
    $BypassDelta = Get-DeltaInt `
        -After $After.DependencyBypass `
        -Before $Before.DependencyBypass

    if ($AllowedDelta -gt $ClientDecisionLimit) {
        throw "Security boundary failed in ${Name}: allowed decisions exceeded the reviewed client limit."
    }

    if ($BlockedIdentityDelta -ne 0 -or $BlockedBothDelta -ne 0) {
        throw "Synthetic identities were not isolated during phase $Name."
    }

    if ($BypassDelta -ne 0) {
        throw "Dependency bypass occurred during phase $Name."
    }

    $Saturated = (
        $P95 -gt $SaturationP95Ms -or
        $UnexpectedFailureRate -ge $SaturationUnexpectedFailureRate -or
        $DroppedIterations -gt 0 -or
        $HealthFailureRate -gt 0
    )

    Write-Host "Requests : $RequestCount"
    Write-Host "p50 ms   : $(Format-Decimal $P50)"
    Write-Host "p95 ms   : $(Format-Decimal $P95)"
    Write-Host "p99 ms   : $(Format-Decimal $P99)"
    Write-Host "Allowed  : $AllowedDelta"
    Write-Host "Blocked  : $BlockedClientDelta"
    Write-Host "Dropped  : $DroppedIterations"
    Write-Host "Saturated: $Saturated"
    Write-Host 'No identity, address, token, Redis key, or raw counter was printed.'

    return [pscustomobject] @{
        name = $Name
        targetRatePerSecond = $Rate
        durationSeconds = $DurationSeconds
        targetIterations = $TargetIterations
        requests = $RequestCount
        achievedRatePerSecond = [Math]::Round($RequestCount / [double] $DurationSeconds, 3)
        achievementRatio = [Math]::Round($AchievementRatio, 6)
        p50Ms = [Math]::Round($P50, 3)
        p95Ms = [Math]::Round($P95, 3)
        p99Ms = [Math]::Round($P99, 3)
        unexpectedFailureRate = [Math]::Round($UnexpectedFailureRate, 6)
        healthProbeFailureRate = [Math]::Round($HealthFailureRate, 6)
        droppedIterations = $DroppedIterations
        allowedDecisions = $AllowedDelta
        blockedClientDecisions = $BlockedClientDelta
        blockedIdentityDecisions = $BlockedIdentityDelta
        blockedBothDecisions = $BlockedBothDelta
        dependencyBypassDecisions = $BypassDelta
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
    'PAYFLOW_K6_EVIDENCE_RATE',
    'PAYFLOW_K6_EVIDENCE_DURATION',
    'PAYFLOW_K6_EVIDENCE_PRE_ALLOCATED_VUS',
    'PAYFLOW_K6_EVIDENCE_MAX_VUS',
    'K6_ACCOUNT_ACTION_EMAIL_PREFIX'
)

$SavedEnvironment = Save-Environment -Names $EnvironmentNames
$StackAttempted = $false

try {
    Write-Host "=== PROTECTED-WORKFLOW EVIDENCE RECORDER ===" -ForegroundColor Cyan

    $Branch = git branch --show-current
    Assert-NativeSuccess 'git branch --show-current'
    $Commit = git rev-parse HEAD
    Assert-NativeSuccess 'git rev-parse HEAD'
    $Dirty = @(git status --porcelain)
    Assert-NativeSuccess 'git status --porcelain'

    if ($Dirty.Count -ne 0) {
        throw 'Accepted evidence requires a clean Git working tree.'
    }

    if ([string]::IsNullOrWhiteSpace($Branch) -or [string]::IsNullOrWhiteSpace($Commit)) {
        throw 'Git evidence identity could not be resolved.'
    }

    New-Item -ItemType Directory -Force -Path $RunDirectory | Out-Null

    $env:MAIL_CONTENT_ENCRYPTION_KEY = New-LocalTestKey
    $env:MFA_SECRET_ENCRYPTION_KEY = New-LocalTestKey
    $env:GRAFANA_ADMIN_PASSWORD = 'payflow-performance-evidence-local-only'
    $env:PAYFLOW_PERFORMANCE_APP_PORT = [string] $AppPort
    $env:PAYFLOW_PERFORMANCE_MAILPIT_PORT = [string] $MailpitPort
    $env:ABUSE_PROTECTION_ENABLED = 'true'

    $HostOs = [Environment]::OSVersion.VersionString
    $PowerShellVersion = $PSVersionTable.PSVersion.ToString()

    $DockerServerVersion = @(docker version --format '{{.Server.Version}}') | Select-Object -First 1
    Assert-NativeSuccess 'Docker server version'

    $DockerInfo = @(docker info --format '{{.NCPU}}|{{.MemTotal}}|{{.OperatingSystem}}') | Select-Object -First 1
    Assert-NativeSuccess 'Docker resource metadata'

    $DockerParts = ([string] $DockerInfo).Split('|')

    if ($DockerParts.Count -ne 3) {
        throw 'Docker resource metadata did not match the bounded format.'
    }

    $DockerCpu = [int] $DockerParts[0]
    $DockerMemoryBytes = [double]::Parse(
        $DockerParts[1],
        [Globalization.CultureInfo]::InvariantCulture
    )
    $DockerMemoryGiB = [Math]::Round($DockerMemoryBytes / 1GB, 2)
    $DockerOperatingSystem = $DockerParts[2]

    docker compose @ComposeArguments down -v --remove-orphans
    Assert-NativeSuccess 'clean evidence project'

    docker compose @ComposeArguments config --quiet
    Assert-NativeSuccess 'evidence Compose validation'

    $StackAttempted = $true

    docker compose @ComposeArguments up -d --build postgres redis kafka mailpit app
    Assert-NativeSuccess 'start evidence project'

    Wait-HttpSuccess -Url $HealthUrl -Name 'Evidence PayFlow health'

    $JavaLines = @(docker compose @ComposeArguments exec -T app java --version)
    Assert-NativeSuccess 'Java runtime version'
    $JavaVersion = [string] ($JavaLines | Select-Object -First 1)

    $K6Lines = @(docker compose @ComposeArguments run --rm --no-deps k6 version)
    Assert-NativeSuccess 'k6 runtime version'
    $K6Version = [string] ($K6Lines | Select-Object -First 1)

    $Phases = New-Object System.Collections.Generic.List[object]

    $Warmup = Invoke-EvidencePhase `
        -Name 'warmup' `
        -Rate 5 `
        -DurationSeconds 30 `
        -DurationText '30s' `
        -EmailPrefix 'pf-evidence-warmup'
    $Phases.Add($Warmup)

    $Steady = Invoke-EvidencePhase `
        -Name 'steady' `
        -Rate 10 `
        -DurationSeconds 120 `
        -DurationText '120s' `
        -EmailPrefix 'pf-evidence-steady'
    $Phases.Add($Steady)

    $SteadyAccepted = (
        $Steady.p95Ms -le $SteadyP95BudgetMs -and
        $Steady.p99Ms -le $SteadyP99BudgetMs -and
        $Steady.unexpectedFailureRate -lt $SteadyUnexpectedFailureBudget -and
        $Steady.droppedIterations -eq 0 -and
        $Steady.achievementRatio -ge $SteadyMinimumAchievementRatio -and
        $Steady.healthProbeFailureRate -eq 0
    )

    $FirstSaturatedRate = $null

    foreach ($Rate in $SaturationRates) {
        $PhaseName = "saturation-$Rate"
        $Phase = Invoke-EvidencePhase `
            -Name $PhaseName `
            -Rate $Rate `
            -DurationSeconds 60 `
            -DurationText '60s' `
            -EmailPrefix "pf-evidence-s$Rate"
        $Phases.Add($Phase)

        if ($null -eq $FirstSaturatedRate -and $Phase.saturated) {
            $FirstSaturatedRate = $Rate
        }
    }

    $OverloadRate = if ($null -eq $FirstSaturatedRate) {
        120
    }
    else {
        [int] [Math]::Ceiling([double] $FirstSaturatedRate * 1.5)
    }

    $Overload = Invoke-EvidencePhase `
        -Name 'overload' `
        -Rate $OverloadRate `
        -DurationSeconds 60 `
        -DurationText '60s' `
        -EmailPrefix 'pf-evidence-overload'
    $Phases.Add($Overload)

    Write-Host "`n=== RECOVERY ===" -ForegroundColor Cyan
    $RecoveryWatch = [Diagnostics.Stopwatch]::StartNew()
    $Recovered = $false

    while ($RecoveryWatch.Elapsed.TotalSeconds -le $RecoveryBudgetSeconds) {
        try {
            $RecoveryResponse = Invoke-WebRequest `
                -Uri $HealthUrl `
                -UseBasicParsing `
                -TimeoutSec 5 `
                -ErrorAction Stop

            if ($RecoveryResponse.StatusCode -ge 200 -and $RecoveryResponse.StatusCode -lt 300) {
                $Recovered = $true
                break
            }
        }
        catch {
            # Recovery remains bounded and retries until the frozen deadline.
        }

        Start-Sleep -Seconds 1
    }

    $RecoveryWatch.Stop()
    $RecoverySeconds = [Math]::Round($RecoveryWatch.Elapsed.TotalSeconds, 3)

    if (-not $Recovered) {
        throw 'PayFlow did not recover within the frozen 30-second budget.'
    }

    Write-Host "Recovery seconds: $(Format-Decimal $RecoverySeconds '0.000')"

    $Candidate = [ordered] @{
        schemaVersion = 1
        generatedAtUtc = [DateTime]::UtcNow.ToString('o')
        gitCommit = $Commit
        branch = $Branch
        environment = [ordered] @{
            hostOs = $HostOs
            powershell = $PowerShellVersion
            dockerServer = ([string] $DockerServerVersion).Trim()
            dockerOperatingSystem = $DockerOperatingSystem
            dockerCpuCount = $DockerCpu
            dockerMemoryGiB = $DockerMemoryGiB
            javaRuntime = $JavaVersion.Trim()
            k6Runtime = $K6Version.Trim()
            composeFiles = @('compose.yml', 'performance/k6/compose.yml')
            profiles = @('app', 'loadtest')
            abuseProtectionEnabled = $true
        }
        dataset = [ordered] @{
            workflow = 'email-verification-request'
            identities = 'disposable example.invalid synthetic identities'
            redisResetBetweenIndependentPhases = $true
            setupCostIncludedInMeasuredLatency = $false
        }
        contract = [ordered] @{
            warmup = '30s @ 5 iterations/s'
            steady = '120s @ 10 iterations/s'
            saturationRates = $SaturationRates
            saturationStageSeconds = 60
            overloadRate = $OverloadRate
            recoveryBudgetSeconds = $RecoveryBudgetSeconds
        }
        outcome = [ordered] @{
            steadyAccepted = $SteadyAccepted
            firstSaturatedRate = $FirstSaturatedRate
            recoverySeconds = $RecoverySeconds
            recoveredWithinBudget = $Recovered
        }
        phases = $Phases.ToArray()
        limitations = @(
            'Developer-workstation evidence only; not a production capacity certification.',
            'The representative account-action workflow becomes client-policy-limited after the reviewed twenty-decision boundary in each independently reset phase.',
            'Each phase resets only the isolated Redis test state before measurement; application and dependency containers remain otherwise unchanged.',
            'Results are not directly comparable across materially different Docker, CPU, memory, Java, or k6 environments.',
            'Raw k6 summaries remain ignored under performance/results/ and are not promoted automatically.'
        )
    }

    $Utf8NoBom = [Text.UTF8Encoding]::new($false)
    $CandidateJson = $Candidate | ConvertTo-Json -Depth 10
    [IO.File]::WriteAllText($CandidateJsonPath, $CandidateJson + "`n", $Utf8NoBom)

    $Rows = New-Object System.Collections.Generic.List[string]

    foreach ($Phase in $Candidate.phases) {
        $Rows.Add(
            "| $($Phase.name) | $($Phase.targetRatePerSecond) | $($Phase.durationSeconds) | $($Phase.requests) | $(Format-Decimal $Phase.p50Ms) | $(Format-Decimal $Phase.p95Ms) | $(Format-Decimal $Phase.p99Ms) | $(Format-Decimal ($Phase.unexpectedFailureRate * 100) '0.000')% | $($Phase.droppedIterations) | $($Phase.allowedDecisions) | $($Phase.blockedClientDecisions) | $($Phase.saturated) |"
        )
    }

    $FirstSaturationText = if ($null -eq $Candidate.outcome.firstSaturatedRate) {
        'not observed through 80 iterations/s'
    }
    else {
        "$($Candidate.outcome.firstSaturatedRate) iterations/s"
    }

    $Markdown = @"
# Candidate protected-workflow performance evidence

> Candidate only. This file is generated under ignored performance/results/
> and must be reviewed before any promotion into docs/performance/evidence/.

- Git commit: $Commit
- Generated UTC: $($Candidate.generatedAtUtc)
- Host OS: $HostOs
- Docker server: $(([string] $DockerServerVersion).Trim())
- Docker environment: $DockerOperatingSystem; $DockerCpu CPU; $DockerMemoryGiB GiB
- Java runtime: $($JavaVersion.Trim())
- k6 runtime: $($K6Version.Trim())
- Compose: compose.yml + performance/k6/compose.yml; profiles app, loadtest
- Abuse protection: enabled
- Dataset: disposable example.invalid identities; isolated Redis reset before each independent phase

| Phase | Target it/s | Seconds | Requests | p50 ms | p95 ms | p99 ms | Unexpected | Dropped | Allowed | Blocked client | Saturated |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
$($Rows -join "`n")

Steady-state accepted: **$SteadyAccepted**.
First saturation: **$FirstSaturationText**.
Overload rate: **$OverloadRate iterations/s**.
Recovery: **$RecoverySeconds seconds**, within the frozen 30-second budget.

## Limitations

- Developer-workstation evidence only; this is not a production capacity certification.
- The representative account-action workflow becomes client-policy-limited after the reviewed twenty-decision boundary in each independently reset phase.
- Each phase resets only the isolated Redis test state before measurement; application and dependency containers otherwise remain unchanged.
- Results are not directly comparable across materially different Docker, CPU, memory, Java, or k6 environments.
- Raw k6 summaries remain ignored under performance/results/ and are not promoted automatically.
"@

    [IO.File]::WriteAllText($CandidateMarkdownPath, $Markdown.Trim() + "`n", $Utf8NoBom)

    Write-Host "`n=== CANDIDATE EVIDENCE COMPLETE ===" -ForegroundColor Green
    Write-Host "Steady accepted : $SteadyAccepted"
    Write-Host "First saturation: $FirstSaturationText"
    Write-Host "Overload rate   : $OverloadRate"
    Write-Host "Recovery seconds: $RecoverySeconds"
    Write-Host "Candidate JSON  : performance/results/evidence/$RunId/candidate-protected-workflow-evidence.json"
    Write-Host "Candidate MD    : performance/results/evidence/$RunId/candidate-protected-workflow-evidence.md"
    Write-Host 'Candidate files remain ignored and require separate review before promotion.'
}
finally {
    if ($StackAttempted) {
        docker compose @ComposeArguments down -v --remove-orphans

        if ($LASTEXITCODE -ne 0) {
            Write-Warning 'Evidence project cleanup returned a non-zero exit code.'
        }
    }

    Restore-Environment -Saved $SavedEnvironment
    Pop-Location
}
