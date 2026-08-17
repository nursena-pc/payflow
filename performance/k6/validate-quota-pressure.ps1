param(
    [ValidatePattern('^[a-z0-9][a-z0-9_-]{0,62}$')]
    [string] $ProjectName = 'payflow-performance-quota',

    [ValidateRange(1, 65535)]
    [int] $AppPort = 18081
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$ExpectedRequests = 40
$ExpectedClientLimit = 20
$MetricsUrl = "http://localhost:$AppPort/actuator/prometheus"

function Get-PrometheusText {
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
        [Parameter(Mandatory = $true)]
        [string] $Text,

        [Parameter(Mandatory = $true)]
        [string] $Outcome,

        [Parameter(Mandatory = $true)]
        [string] $Reason
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

function Get-Delta {
    param(
        [double] $After,
        [double] $Before
    )

    return [int] [Math]::Round($After - $Before)
}

$Before = Get-PrometheusText
$BeforeAllowed = Get-DecisionCounter `
    -Text $Before `
    -Outcome 'allowed' `
    -Reason 'none'
$BeforeBlockedClient = Get-DecisionCounter `
    -Text $Before `
    -Outcome 'blocked' `
    -Reason 'client'
$BeforeBlockedIdentity = Get-DecisionCounter `
    -Text $Before `
    -Outcome 'blocked' `
    -Reason 'identity'
$BeforeBlockedBoth = Get-DecisionCounter `
    -Text $Before `
    -Outcome 'blocked' `
    -Reason 'both'
$BeforeBypass = Get-DecisionCounter `
    -Text $Before `
    -Outcome 'dependency_bypass' `
    -Reason 'dependency_failure'

& (Join-Path $PSScriptRoot 'run.ps1') `
    -Scenario account-action-quota-pressure `
    -ProjectName $ProjectName

if ($LASTEXITCODE -ne 0) {
    throw "Quota-pressure k6 scenario failed with exit code $LASTEXITCODE."
}

$After = Get-PrometheusText
$AllowedDelta = Get-Delta `
    -After (Get-DecisionCounter -Text $After -Outcome 'allowed' -Reason 'none') `
    -Before $BeforeAllowed
$BlockedClientDelta = Get-Delta `
    -After (Get-DecisionCounter -Text $After -Outcome 'blocked' -Reason 'client') `
    -Before $BeforeBlockedClient
$BlockedIdentityDelta = Get-Delta `
    -After (Get-DecisionCounter -Text $After -Outcome 'blocked' -Reason 'identity') `
    -Before $BeforeBlockedIdentity
$BlockedBothDelta = Get-Delta `
    -After (Get-DecisionCounter -Text $After -Outcome 'blocked' -Reason 'both') `
    -Before $BeforeBlockedBoth
$BypassDelta = Get-Delta `
    -After (Get-DecisionCounter -Text $After -Outcome 'dependency_bypass' -Reason 'dependency_failure') `
    -Before $BeforeBypass

$ExpectedBlocked = $ExpectedRequests - $ExpectedClientLimit

if ($AllowedDelta -ne 20) {
    throw "Expected exactly 20 allowed decisions; observed $AllowedDelta."
}

if ($BlockedClientDelta -ne 20) {
    throw "Expected exactly 20 client-blocked decisions; observed $BlockedClientDelta."
}

if ($BlockedIdentityDelta -ne 0 -or $BlockedBothDelta -ne 0) {
    throw 'Quota-pressure identities were not isolated as required.'
}

if ($BypassDelta -ne 0) {
    throw 'Dependency bypass occurred during quota-pressure validation.'
}

if (($AllowedDelta + $BlockedClientDelta) -ne $ExpectedRequests) {
    throw 'Quota-pressure decision accounting does not match request count.'
}

if ($BlockedClientDelta -ne $ExpectedBlocked) {
    throw 'Quota-pressure blocking does not match the configured client boundary.'
}

Write-Host 'Quota-pressure validation PASS.' -ForegroundColor Green
Write-Host "Requests       : $ExpectedRequests"
Write-Host "Allowed        : $AllowedDelta"
Write-Host "Blocked-client : $BlockedClientDelta"
Write-Host "Bypass         : $BypassDelta"
Write-Host 'No identity, client address, credential, Redis key, or raw counter is printed.'
