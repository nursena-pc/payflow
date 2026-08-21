$ErrorActionPreference = 'Stop'

$RedisTest = 'V016RedisOutageRecoveryRehearsalTest'
$KafkaTest = 'V016KafkaOutageRecoveryRehearsalTest'
$FocusedTests = "$RedisTest,$KafkaTest"

function Git-Scalar {
    param(
        [Parameter(Mandatory)][string[]] $Arguments
    )

    $Output = @(& git @Arguments 2>&1)
    $ExitCode = $LASTEXITCODE

    if ($ExitCode -ne 0) {
        throw "git $($Arguments -join ' ') failed:`n$($Output -join "`n")"
    }

    return (($Output | ForEach-Object { "$_" }) -join "`n").Trim()
}

function Resolve-Java21 {
    if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        throw 'JAVA_HOME must point to the PayFlow Java 21 JDK.'
    }

    $Java = Join-Path $env:JAVA_HOME 'bin\java.exe'

    if (-not (Test-Path -LiteralPath $Java -PathType Leaf)) {
        throw "JAVA_HOME does not contain bin\java.exe: $env:JAVA_HOME"
    }

    $QuotedJava = '"' + $Java + '"'

    $VersionOutput = @(
        & cmd.exe /d /s /c "$QuotedJava -version 2>&1"
    )

    $ExitCode = $LASTEXITCODE

    if ($ExitCode -ne 0) {
        throw "JAVA_HOME java -version failed with exit code $ExitCode."
    }

    $VersionText = ($VersionOutput -join "`n")

    $MajorMatch = [regex]::Match(
        $VersionText,
        '(?im)^(?:openjdk|java) version "([0-9]+)(?:\.|")'
    )

    if (
        -not $MajorMatch.Success -or
        [int] $MajorMatch.Groups[1].Value -ne 21
    ) {
        throw 'The outage/recovery rehearsal requires JAVA_HOME Java 21.'
    }

    return [pscustomobject]@{
        Executable = $Java
        VersionText = $VersionText
    }
}

function Assert-FileContains {
    param(
        [Parameter(Mandatory)][string] $Path,
        [Parameter(Mandatory)][string[]] $Needles
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Required rehearsal file is missing: $Path"
    }

    $Content = [System.IO.File]::ReadAllText(
        (Resolve-Path -LiteralPath $Path)
    )

    foreach ($Needle in $Needles) {
        if (-not $Content.Contains($Needle)) {
            throw "Required rehearsal marker missing from ${Path}: $Needle"
        }
    }
}

Write-Host '=== 1. VERIFY CLEAN REPOSITORY CHECKPOINT ===' `
    -ForegroundColor Cyan

$RepoRoot = Git-Scalar -Arguments @(
    'rev-parse',
    '--show-toplevel'
)

Set-Location -LiteralPath $RepoRoot

$Branch = Git-Scalar -Arguments @(
    'branch',
    '--show-current'
)

if ([string]::IsNullOrWhiteSpace($Branch)) {
    throw 'The outage/recovery rehearsal requires an attached Git branch.'
}

$Head = Git-Scalar -Arguments @(
    'rev-parse',
    'HEAD'
)

$Status = @(
    git status --porcelain=v1 --untracked-files=all
)

if ($LASTEXITCODE -ne 0) {
    throw 'Could not inspect the Git working tree.'
}

if ($Status.Count -ne 0) {
    $Status | Out-Host
    throw 'The outage/recovery rehearsal requires a clean working tree.'
}

$IgnoredRuntime = @(
    git check-ignore .runtime 2>$null
)

if (
    $LASTEXITCODE -ne 0 -or
    $IgnoredRuntime.Count -eq 0
) {
    throw '.runtime/ must remain ignored before rehearsal evidence is written.'
}

Write-Host "Branch: $Branch"
Write-Host "HEAD  : $Head"
Write-Host 'Repository checkpoint PASS.' `
    -ForegroundColor Green

Write-Host ''
Write-Host '=== 2. VERIFY REHEARSAL CONTRACT FILES ===' `
    -ForegroundColor Cyan

Assert-FileContains `
    -Path 'src\test\java\com\nursena\payflow\configuration\V016RedisOutageRecoveryRehearsalTest.java' `
    -Needles @(
        'pauseContainerCmd',
        'unpauseContainerCmd',
        'LOGIN_RATE_LIMIT_UNAVAILABLE',
        'password-recovery-request.dependency-failure-mode=FAIL_CLOSED',
        'registration.enabled=false'
    )

Assert-FileContains `
    -Path 'src\test\java\com\nursena\payflow\configuration\V016KafkaOutageRecoveryRehearsalTest.java' `
    -Needles @(
        'pauseContainerCmd',
        'unpauseContainerCmd',
        'REPLAY_FAILED',
        'REPLAYED',
        'awaitReplayAttempt',
        'processedCount'
    )

Write-Host 'Committed focused rehearsal contracts PASS.' `
    -ForegroundColor Green

Write-Host ''
Write-Host '=== 3. VERIFY JAVA 21 + DOCKER ===' `
    -ForegroundColor Cyan

$Java = Resolve-Java21

Write-Host (
    ($Java.VersionText -split "`r?`n")[0]
)

$DockerVersion = @(
    docker version --format '{{.Server.Version}}' 2>&1
)

if ($LASTEXITCODE -ne 0) {
    throw 'Docker daemon is unavailable.'
}

$DockerServer = (
    ($DockerVersion | ForEach-Object { "$_" }) -join ''
).Trim()

Write-Host "Docker server: $DockerServer"
Write-Host 'Toolchain PASS.' `
    -ForegroundColor Green

Write-Host ''
Write-Host '=== 4. PREPARE IGNORED SANITIZED EVIDENCE DIRECTORY ===' `
    -ForegroundColor Cyan

$Stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$RuntimeRoot = Join-Path `
    $RepoRoot `
    ".runtime\dependency-outage-recovery\$Stamp"

New-Item `
    -ItemType Directory `
    -Path $RuntimeRoot `
    -Force |
    Out-Null

$EvidencePath = Join-Path `
    $RuntimeRoot `
    'evidence.txt'

Write-Host "Evidence root: $RuntimeRoot"

Write-Host ''
Write-Host '=== 5. RUN FOCUSED REDIS + KAFKA OUTAGE/RECOVERY CONTRACTS ===' `
    -ForegroundColor Cyan

& .\mvnw.cmd `
    -B `
    -ntp `
    "-Dtest=$FocusedTests" `
    test

if ($LASTEXITCODE -ne 0) {
    throw 'Focused Redis/Kafka outage-recovery rehearsal failed.'
}

$SurefireRoot = Join-Path `
    $RepoRoot `
    'target\surefire-reports'

$ExpectedReports = @(
    "TEST-com.nursena.payflow.configuration.$RedisTest.xml",
    "TEST-com.nursena.payflow.configuration.$KafkaTest.xml"
)

[long] $Tests = 0
[long] $Failures = 0
[long] $Errors = 0
[long] $Skipped = 0

foreach ($ReportName in $ExpectedReports) {
    $ReportPath = Join-Path `
        $SurefireRoot `
        $ReportName

    if (-not (Test-Path -LiteralPath $ReportPath -PathType Leaf)) {
        throw "Focused Surefire report is missing: $ReportPath"
    }

    [xml] $Xml = Get-Content `
        -LiteralPath $ReportPath `
        -Raw

    $Tests += [long] $Xml.testsuite.tests
    $Failures += [long] $Xml.testsuite.failures
    $Errors += [long] $Xml.testsuite.errors
    $Skipped += [long] $Xml.testsuite.skipped
}

Write-Host (
    "Focused tests: {0}, failures: {1}, errors: {2}, skipped: {3}" -f
        $Tests,
        $Failures,
        $Errors,
        $Skipped
)

if (
    $Tests -ne 2 -or
    $Failures -ne 0 -or
    $Errors -ne 0 -or
    $Skipped -ne 0
) {
    throw 'Focused rehearsal result must be exactly 2 / 0 / 0 / 0.'
}

Write-Host 'Focused runtime rehearsal PASS.' `
    -ForegroundColor Green

Write-Host ''
Write-Host '=== 6. VERIFY REPOSITORY HYGIENE ===' `
    -ForegroundColor Cyan

& git diff --check

if ($LASTEXITCODE -ne 0) {
    throw 'git diff --check failed.'
}

$FinalHead = Git-Scalar -Arguments @(
    'rev-parse',
    'HEAD'
)

if ($FinalHead -ne $Head) {
    throw 'HEAD changed while the rehearsal was running.'
}

$FinalStatus = @(
    git status --porcelain=v1 --untracked-files=all
)

if ($LASTEXITCODE -ne 0) {
    throw 'Could not inspect final Git working tree.'
}

if ($FinalStatus.Count -ne 0) {
    $FinalStatus | Out-Host
    throw 'Tracked repository content changed during the rehearsal.'
}

Write-Host 'Repository hygiene PASS.' `
    -ForegroundColor Green

Write-Host ''
Write-Host '=== 7. WRITE SANITIZED EVIDENCE ===' `
    -ForegroundColor Cyan

$Evidence = @"
PayFlow v0.16.0 Redis/Kafka Outage-Recovery Rehearsal
Issue: #178
Branch: $Branch
HEAD: $Head
Java major: 21
Docker server: $DockerServer

Focused tests: 2
Failures: 0
Errors: 0
Skipped: 0

Redis:
  isolated dependency outage: PASS
  login limiter fail-closed HTTP 503: PASS
  dependency-detail leakage check: PASS
  non-registration password-recovery FAIL_CLOSED side-effect suppression: PASS
  registration abuse protection activation: NONE
  PostgreSQL durable user fingerprint preserved: PASS
  host endpoint recovery: PASS
  running client automatic recovery: PASS
  bounded Redis-failure metrics: PASS

Kafka:
  healthy PostgreSQL-backed outbox publication: PASS
  semantic JSON delivery comparison: PASS
  broker isolation: PASS
  durable outbox retry lifecycle: PASS
  broker recovery publication: PASS
  transfer-completed audit/idempotency boundary: PASS
  durable DLT intake: PASS
  replay outage -> REPLAY_FAILED: PASS
  replay recovery -> REPLAYED: PASS
  acknowledgement ambiguity: delayed earlier attempt tolerated
  replay origin and attempt accounting: PASS
  processed/audit boundary after possible duplicate delivery: single-record PASS
  payment transaction count: unchanged
  ledger entry count: unchanged

Repository tree: CLEAN
Repository mutation during rehearsal: NONE
Release/tag mutation: NONE

Limitations:
  local isolated Testcontainers rehearsal only
  no Redis HA/persistence certification
  no Kafka multi-broker/partition failover certification
  no zero-data-loss claim
  no production RPO/RTO or disaster-recovery certification
"@

$EvidenceCrlf = (
    $Evidence.Replace("`r`n", "`n")
).Replace("`r", "`n").Replace("`n", "`r`n")

[System.IO.File]::WriteAllText(
    $EvidencePath,
    $EvidenceCrlf,
    [System.Text.UTF8Encoding]::new($true)
)

$EvidenceHash = (
    Get-FileHash `
        -LiteralPath $EvidencePath `
        -Algorithm SHA256
).Hash.ToLowerInvariant()

Write-Host ''
Write-Host '=============================================' `
    -ForegroundColor Green
Write-Host 'v0.16 REDIS/KAFKA OUTAGE REHEARSAL PASS' `
    -ForegroundColor Green
Write-Host '=============================================' `
    -ForegroundColor Green
Write-Host "Branch       : $Branch"
Write-Host "HEAD         : $Head"
Write-Host 'Focused tests: 2 / 0 / 0 / 0'
Write-Host 'Redis        : outage + fail-closed + recovery PASS'
Write-Host 'Abuse flow   : password recovery fail-closed PASS'
Write-Host 'Kafka outbox : outage + retry + recovery PASS'
Write-Host 'Kafka DLT    : intake + replay recovery PASS'
Write-Host 'Idempotency  : single durable processing boundary PASS'
Write-Host 'Tree         : CLEAN'
Write-Host "Evidence     : $EvidencePath"
Write-Host "SHA256       : $EvidenceHash"
