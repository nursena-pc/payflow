# PayFlow clean-environment release rehearsal.
#
# Creates a detached worktree from an exact reviewed commit and proves the
# release candidate without reusing generated target/.runtime output.
#
# Evidence is local-only under ignored .runtime/release-rehearsal/<commit>/.
# The script does not create commits, tags, pull requests, or GitHub Releases.
#
# Requirements:
# - PowerShell 5.1+
# - Git
# - Docker + Docker Compose with !override support
# - Java 21
#
# Security boundary:
# - synthetic configuration values are generated at runtime;
# - JWT key material lives only in an isolated Docker-managed volume;
# - raw application logs and private keys are not retained as evidence;
# - no signing, SLSA, reproducible-build, attestation, certification, or
#   publication claim is made.

param(
    [Parameter(Mandatory)]
    [ValidatePattern('^[0-9a-f]{40}$')]
    [string] $ExpectedHead,

    [ValidatePattern('^[0-9]+\.[0-9]+\.[0-9]+$')]
    [string] $ExpectedReleaseCandidateVersion
)

$ErrorActionPreference = 'Stop'

$ExpectedMavenVersion = '3.9.16'
$ExpectedJavaMajor = '21'
$ExpectedWrapperDistributionSha256 =
    '5af3b743dd8b876b5c45da33b676251e5f1687712644abb4ee519ca56e1d89ce'

$RequiredConfigName = 'MAIL_CONTENT_ENCRYPTION_KEY'
$RequiredConfigMarker = 'MAIL_CONTENT_ENCRYPTION_KEY must be set'

function Invoke-Captured {
    param(
        [Parameter(Mandatory)]
        [string] $FilePath,

        [Parameter(Mandatory)]
        [string[]] $Arguments,

        [string] $WorkingDirectory
    )

    $OldLocation = Get-Location
    $OldErrorActionPreference = $ErrorActionPreference

    $HasNativePreference = $null -ne (
        Get-Variable `
            -Name PSNativeCommandUseErrorActionPreference `
            -ErrorAction SilentlyContinue
    )

    if ($HasNativePreference) {
        $OldNativePreference =
            $PSNativeCommandUseErrorActionPreference
    }

    try {
        if (-not [string]::IsNullOrWhiteSpace($WorkingDirectory)) {
            Set-Location -LiteralPath $WorkingDirectory
        }

        $ErrorActionPreference = 'Continue'

        if ($HasNativePreference) {
            $PSNativeCommandUseErrorActionPreference = $false
        }

        $Output = @(& $FilePath @Arguments 2>&1)
        $ExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $OldErrorActionPreference

        if ($HasNativePreference) {
            $PSNativeCommandUseErrorActionPreference =
                $OldNativePreference
        }

        Set-Location -LiteralPath $OldLocation
    }

    [pscustomobject]@{
        ExitCode = [int] $ExitCode
        Lines = @($Output | ForEach-Object { "$_" })
        Text = ((@($Output | ForEach-Object { "$_" })) -join "`n").Trim()
    }
}

function Invoke-Required {
    param(
        [Parameter(Mandatory)]
        [string] $FilePath,

        [Parameter(Mandatory)]
        [string[]] $Arguments,

        [Parameter(Mandatory)]
        [string] $FailureMessage,

        [string] $WorkingDirectory
    )

    $Result =
        Invoke-Captured `
            -FilePath $FilePath `
            -Arguments $Arguments `
            -WorkingDirectory $WorkingDirectory

    if ($Result.ExitCode -ne 0) {
        if (-not [string]::IsNullOrWhiteSpace($Result.Text)) {
            Write-Host $Result.Text
        }

        throw "$FailureMessage Exit code: $($Result.ExitCode)"
    }

    $Result
}

function Assert-Equal {
    param(
        [Parameter(Mandatory)]
        [string] $Label,

        [Parameter(Mandatory)]
        [string] $Actual,

        [Parameter(Mandatory)]
        [string] $Expected
    )

    if ($Actual -ne $Expected) {
        throw "$Label mismatch. Expected $Expected; actual $Actual."
    }
}

function Get-FileSha256 {
    param(
        [Parameter(Mandatory)]
        [string] $Path
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Missing file: $Path"
    }

    (
        Get-FileHash `
            -LiteralPath $Path `
            -Algorithm SHA256
    ).Hash.ToLowerInvariant()
}

function Write-Utf8NoBom {
    param(
        [Parameter(Mandatory)]
        [string] $Path,

        [Parameter(Mandatory)]
        [string] $Text
    )

    [IO.File]::WriteAllText(
        $Path,
        $Text,
        [Text.UTF8Encoding]::new($false)
    )
}

function New-RandomBase64 {
    param(
        [int] $ByteCount = 32
    )

    $Bytes =
        New-Object byte[] $ByteCount

    $Rng =
        [Security.Cryptography.RandomNumberGenerator]::Create()

    try {
        $Rng.GetBytes($Bytes)
    }
    finally {
        $Rng.Dispose()
    }

    [Convert]::ToBase64String($Bytes)
}

function Save-Environment {
    param(
        [Parameter(Mandatory)]
        [string[]] $Names
    )

    $Snapshot = @{}

    foreach ($Name in $Names) {
        $Item =
            Get-Item `
                -LiteralPath "Env:$Name" `
                -ErrorAction SilentlyContinue

        if ($null -eq $Item) {
            $Snapshot[$Name] = [pscustomobject]@{
                Exists = $false
                Value = $null
            }
        }
        else {
            $Snapshot[$Name] = [pscustomobject]@{
                Exists = $true
                Value = [string] $Item.Value
            }
        }
    }

    $Snapshot
}

function Restore-Environment {
    param(
        [Parameter(Mandatory)]
        [hashtable] $Snapshot
    )

    foreach ($Name in $Snapshot.Keys) {
        if ([bool] $Snapshot[$Name].Exists) {
            Set-Item `
                -LiteralPath "Env:$Name" `
                -Value ([string] $Snapshot[$Name].Value)
        }
        else {
            Remove-Item `
                -LiteralPath "Env:$Name" `
                -ErrorAction SilentlyContinue
        }
    }
}

function Get-TestSummary {
    param(
        [Parameter(Mandatory)]
        [string] $ReportsDirectory
    )

    $Files =
        @(
            Get-ChildItem `
                -LiteralPath $ReportsDirectory `
                -Filter 'TEST-*.xml' `
                -File `
                -ErrorAction Stop
        )

    if ($Files.Count -eq 0) {
        throw 'No Surefire XML reports were produced.'
    }

    [long] $Tests = 0
    [long] $Failures = 0
    [long] $Errors = 0
    [long] $Skipped = 0

    foreach ($File in $Files) {
        [xml] $Xml =
            Get-Content `
                -LiteralPath $File.FullName `
                -Raw `
                -Encoding UTF8

        $Suite =
            $Xml.testsuite

        $Tests += [long] $Suite.tests
        $Failures += [long] $Suite.failures
        $Errors += [long] $Suite.errors
        $Skipped += [long] $Suite.skipped
    }

    [pscustomobject]@{
        Tests = $Tests
        Failures = $Failures
        Errors = $Errors
        Skipped = $Skipped
    }
}

Write-Host '=== CLEAN-ENVIRONMENT RELEASE REHEARSAL ===' `
    -ForegroundColor Cyan

$RepoRoot = (
    Invoke-Required `
        -FilePath 'git' `
        -Arguments @('rev-parse', '--show-toplevel') `
        -FailureMessage 'Not inside a Git repository.'
).Text.Trim()

Set-Location -LiteralPath $RepoRoot

$Head = (
    Invoke-Required `
        -FilePath 'git' `
        -Arguments @('rev-parse', 'HEAD') `
        -FailureMessage 'Unable to resolve HEAD.'
).Text.Trim()

Assert-Equal `
    -Label 'Source HEAD' `
    -Actual $Head `
    -Expected $ExpectedHead

$SourceStatus = (
    Invoke-Required `
        -FilePath 'git' `
        -Arguments @(
            'status',
            '--porcelain=v1',
            '--untracked-files=all'
        ) `
        -FailureMessage 'Unable to inspect source working tree.'
).Text

if (-not [string]::IsNullOrWhiteSpace($SourceStatus)) {
    throw 'Source working tree must be clean.'
}

$IgnoreProbe =
    Invoke-Captured `
        -FilePath 'git' `
        -Arguments @('check-ignore', '-q', '.runtime/')

if ($IgnoreProbe.ExitCode -ne 0) {
    throw '.runtime/ must remain ignored.'
}

$TreeSha = (
    Invoke-Required `
        -FilePath 'git' `
        -Arguments @('rev-parse', 'HEAD^{tree}') `
        -FailureMessage 'Unable to resolve Git tree SHA.'
).Text.Trim()

$EvidenceDir =
    Join-Path `
        $RepoRoot `
        ".runtime/release-rehearsal/$ExpectedHead"

if (Test-Path -LiteralPath $EvidenceDir) {
    Remove-Item `
        -LiteralPath $EvidenceDir `
        -Recurse `
        -Force
}

New-Item `
    -ItemType Directory `
    -Path $EvidenceDir `
    -Force |
Out-Null

$SafeEvidencePath =
    Join-Path $EvidenceDir 'current-safe.json'

$VerifyLogPath =
    Join-Path $EvidenceDir 'maven-verify.log'

$ChecksumEvidencePath =
    Join-Path $EvidenceDir 'artifact.sha256'

$Worktree =
    Join-Path `
        ([IO.Path]::GetTempPath()) `
        ('payflow-release-rehearsal-' + [guid]::NewGuid().ToString('N'))

$ComposeProject =
    (
        'payflow-release-rehearsal-' +
        $ExpectedHead.Substring(0, 8) +
        '-' +
        $PID
    ).ToLowerInvariant()

$JwtVolume =
    (
        'payflow-release-rehearsal-jwt-' +
        [guid]::NewGuid().ToString('N')
    ).ToLowerInvariant()

$OverridePath =
    Join-Path `
        ([IO.Path]::GetTempPath()) `
        ('payflow-release-rehearsal-' + [guid]::NewGuid().ToString('N') + '.yml')

$EnvNames = @(
    'GRAFANA_ADMIN_PASSWORD',
    'MAIL_CONTENT_ENCRYPTION_KEY',
    'MFA_SECRET_ENCRYPTION_KEY',
    'REHEARSAL_JWT_VOLUME'
)

$EnvironmentSnapshot =
    Save-Environment -Names $EnvNames

$DockerExe = $null
$WorktreeCreated = $false
$JwtVolumeCreated = $false
$ComposeReady = $false
$Passed = $false

try {
    Invoke-Required `
        -FilePath 'git' `
        -Arguments @(
            'worktree',
            'add',
            '--detach',
            $Worktree,
            $ExpectedHead
        ) `
        -FailureMessage 'Unable to create isolated rehearsal worktree.' |
    Out-Null

    $WorktreeCreated = $true

    if (
        (Test-Path -LiteralPath (Join-Path $Worktree 'target')) -or
        (Test-Path -LiteralPath (Join-Path $Worktree '.runtime'))
    ) {
        throw 'Fresh worktree unexpectedly contains generated output.'
    }

    $WorktreeStatus = (
        Invoke-Required `
            -FilePath 'git' `
            -Arguments @(
                'status',
                '--porcelain=v1',
                '--untracked-files=all'
            ) `
            -FailureMessage 'Unable to inspect isolated worktree.' `
            -WorkingDirectory $Worktree
    ).Text

    if (-not [string]::IsNullOrWhiteSpace($WorktreeStatus)) {
        throw 'Fresh isolated worktree is not clean.'
    }

    $MvnwCmd =
        Join-Path $Worktree 'mvnw.cmd'

    $MavenVersionOutput =
        Invoke-Required `
            -FilePath $MvnwCmd `
            -Arguments @('-version') `
            -FailureMessage 'Maven Wrapper version probe failed.' `
            -WorkingDirectory $Worktree

    $MavenLine =
        @(
            $MavenVersionOutput.Lines |
                Where-Object { $_ -match '^Apache Maven\s+' }
        )

    $JavaLine =
        @(
            $MavenVersionOutput.Lines |
                Where-Object { $_ -match '^Java version:\s+' }
        )

    if (
        $MavenLine.Count -ne 1 -or
        $JavaLine.Count -ne 1
    ) {
        throw 'Unable to identify Maven/Java version lines.'
    }

    $MavenVersion =
        (
            $MavenLine[0] -replace '^Apache Maven\s+', ''
        ).Split(' ')[0].Trim()

    Assert-Equal `
        -Label 'Maven Wrapper' `
        -Actual $MavenVersion `
        -Expected $ExpectedMavenVersion

    $JavaMatch =
        [regex]::Match(
            $JavaLine[0],
            '^Java version:\s*([^,\s]+)'
        )

    if (-not $JavaMatch.Success) {
        throw 'Unable to parse Java version.'
    }

    $JavaVersion =
        $JavaMatch.Groups[1].Value

    if ($JavaVersion -notmatch "^$ExpectedJavaMajor(?:\.|$)") {
        throw "Expected Java major $ExpectedJavaMajor; got $JavaVersion."
    }

    $ProjectVersion = (
        Invoke-Required `
            -FilePath $MvnwCmd `
            -Arguments @(
                '-q',
                '-DforceStdout',
                'help:evaluate',
                '-Dexpression=project.version'
            ) `
            -FailureMessage 'Unable to resolve project version.' `
            -WorkingDirectory $Worktree
    ).Text.Trim()

    $ReleaseCandidateMode =
        $PSBoundParameters.ContainsKey(
            'ExpectedReleaseCandidateVersion'
        )

    if ($ReleaseCandidateMode) {
        Assert-Equal `
            -Label 'Release-candidate project version' `
            -Actual $ProjectVersion `
            -Expected $ExpectedReleaseCandidateVersion
    }
    elseif (
        $ProjectVersion -notmatch
            '^[0-9]+\.[0-9]+\.[0-9]+-SNAPSHOT$'
    ) {
        throw "Default release rehearsal mode expects a snapshot project version; got $ProjectVersion."
    }

    $MvnwMode = (
        Invoke-Required `
            -FilePath 'git' `
            -Arguments @('ls-tree', 'HEAD', '--', 'mvnw') `
            -FailureMessage 'Unable to inspect committed mvnw mode.' `
            -WorkingDirectory $Worktree
    ).Text.Trim()

    if ($MvnwMode -notmatch '^100755\s+blob\s+') {
        throw "Committed mvnw mode is not 100755: $MvnwMode"
    }

    $WrapperText =
        Get-Content `
            -LiteralPath (
                Join-Path `
                    $Worktree `
                    '.mvn/wrapper/maven-wrapper.properties'
            ) `
            -Raw `
            -Encoding UTF8

    $WrapperShaMatch =
        [regex]::Match(
            $WrapperText,
            '(?m)^distributionSha256Sum=([0-9a-fA-F]{64})$'
        )

    if (-not $WrapperShaMatch.Success) {
        throw 'Maven Wrapper distribution SHA-256 pin is missing.'
    }

    Assert-Equal `
        -Label 'Wrapper distribution SHA-256' `
        -Actual $WrapperShaMatch.Groups[1].Value.ToLowerInvariant() `
        -Expected $ExpectedWrapperDistributionSha256

    # Parse Dockerfile as logical lines rather than with a multiline
    # end-of-line-sensitive regex. Dockerfile is covered by the repository's
    # generic text=auto rule, so a Windows checkout may use CRLF while GitHub
    # and Linux checkouts use LF. The immutable digest contract must be
    # independent of host checkout line endings.
    $DockerfileLines =
        @(
            Get-Content `
                -LiteralPath (Join-Path $Worktree 'Dockerfile') `
                -Encoding UTF8 |
                ForEach-Object {
                    $_.TrimEnd()
                }
        )

    $BuilderMatches =
        @(
            $DockerfileLines |
                ForEach-Object {
                    [regex]::Match(
                        $_,
                        '^FROM\s+(\S+@sha256:[0-9a-f]{64})\s+AS\s+build$'
                    )
                } |
                Where-Object {
                    $_.Success
                }
        )

    $RuntimeMatches =
        @(
            $DockerfileLines |
                ForEach-Object {
                    [regex]::Match(
                        $_,
                        '^FROM\s+(\S+@sha256:[0-9a-f]{64})$'
                    )
                } |
                Where-Object {
                    $_.Success
                }
        )

    if (
        $BuilderMatches.Count -ne 1 -or
        $RuntimeMatches.Count -ne 1
    ) {
        throw (
            'Immutable Docker builder/runtime pins are incomplete or ' +
            'ambiguous.'
        )
    }

    $BuilderImage =
        $BuilderMatches[0].Groups[1].Value

    $RuntimeImage =
        $RuntimeMatches[0].Groups[1].Value

    $ReleaseWorkflow =
        Get-Content `
            -LiteralPath (
                Join-Path `
                    $Worktree `
                    '.github/workflows/release.yml'
            ) `
            -Raw `
            -Encoding UTF8

    foreach (
        $Contract in @(
            './mvnw -q -DforceStdout help:evaluate -Dexpression=project.version',
            './mvnw -B -ntp clean verify',
            'sha256sum "${JAR_NAME}" > "${JAR_NAME}.sha256"',
            'target/payflow-${{ steps.metadata.outputs.version }}.jar',
            'target/payflow-${{ steps.metadata.outputs.version }}.jar.sha256',
            'gh release create "${GITHUB_REF_NAME}"'
        )
    ) {
        if (-not $ReleaseWorkflow.Contains($Contract)) {
            throw "Release workflow contract missing: $Contract"
        }
    }

    $Verify =
        Invoke-Captured `
            -FilePath $MvnwCmd `
            -Arguments @('-B', '-ntp', 'clean', 'verify') `
            -WorkingDirectory $Worktree

    $Verify.Lines |
        Set-Content `
            -LiteralPath $VerifyLogPath `
            -Encoding UTF8

    if ($Verify.ExitCode -ne 0) {
        throw "Complete Maven verification failed. Log: $VerifyLogPath"
    }

    if ($Verify.Text -notmatch 'BUILD SUCCESS') {
        throw 'Complete Maven verification did not report BUILD SUCCESS.'
    }

    $TestSummary =
        Get-TestSummary `
            -ReportsDirectory (
                Join-Path $Worktree 'target/surefire-reports'
            )

    if (
        $TestSummary.Tests -le 0 -or
        $TestSummary.Failures -ne 0 -or
        $TestSummary.Errors -ne 0 -or
        $TestSummary.Skipped -ne 0
    ) {
        throw (
            'Complete Maven verification test summary is not clean: ' +
            "$($TestSummary.Tests)/$($TestSummary.Failures)/" +
            "$($TestSummary.Errors)/$($TestSummary.Skipped)"
        )
    }

    $VerifyLogSha256 =
        Get-FileSha256 -Path $VerifyLogPath

    $JarName =
        "payflow-$ProjectVersion.jar"

    $JarPath =
        Join-Path `
            (Join-Path $Worktree 'target') `
            $JarName

    if (-not (Test-Path -LiteralPath $JarPath -PathType Leaf)) {
        throw "Expected executable JAR missing: target/$JarName"
    }

    $JarInfo =
        Get-Item -LiteralPath $JarPath

    $ArtifactSha256 =
        Get-FileSha256 -Path $JarPath

    Write-Utf8NoBom `
        -Path $ChecksumEvidencePath `
        -Text "$ArtifactSha256  $JarName`n"

    $ChecksumText =
        Get-Content `
            -LiteralPath $ChecksumEvidencePath `
            -Raw `
            -Encoding UTF8

    $ChecksumMatch =
        [regex]::Match(
            $ChecksumText.Trim(),
            '^([0-9a-f]{64})\s{2}(.+)$'
        )

    if (-not $ChecksumMatch.Success) {
        throw 'Checksum evidence is not sha256sum-compatible.'
    }

    Assert-Equal `
        -Label 'Checksum artifact SHA-256' `
        -Actual $ChecksumMatch.Groups[1].Value `
        -Expected $ArtifactSha256

    Assert-Equal `
        -Label 'Checksum artifact name' `
        -Actual $ChecksumMatch.Groups[2].Value `
        -Expected $JarName

    $ChecksumEvidenceSha256 =
        Get-FileSha256 -Path $ChecksumEvidencePath

    $Gitleaks =
        Invoke-Captured `
            -FilePath 'powershell.exe' `
            -Arguments @(
                '-NoProfile',
                '-ExecutionPolicy',
                'Bypass',
                '-File',
                (
                    Join-Path `
                        $Worktree `
                        'scripts/security/verify-gitleaks.ps1'
                )
            ) `
            -WorkingDirectory $Worktree

    if (
        $Gitleaks.ExitCode -ne 0 -or
        $Gitleaks.Text -notmatch
            'Gitleaks committed-content baseline: PASS'
    ) {
        throw 'Committed Gitleaks baseline verifier failed.'
    }

    $GitleaksSafePath =
        Join-Path `
            $Worktree `
            '.runtime/security/gitleaks/current-safe.json'

    $GitleaksSafeSha256 =
        Get-FileSha256 -Path $GitleaksSafePath

    $Vulnerability =
        Invoke-Captured `
            -FilePath 'powershell.exe' `
            -Arguments @(
                '-NoProfile',
                '-ExecutionPolicy',
                'Bypass',
                '-File',
                (
                    Join-Path `
                        $Worktree `
                        'scripts/security/verify-vulnerability-review.ps1'
                ),
                '-ExpectedHead',
                $ExpectedHead
            ) `
            -WorkingDirectory $Worktree

    if (
        $Vulnerability.ExitCode -ne 0 -or
        $Vulnerability.Text -notmatch 'Vulnerability review: PASS'
    ) {
        throw 'Committed vulnerability review failed.'
    }

    $VulnerabilitySafePath =
        Join-Path `
            $Worktree `
            ".runtime/security/vulnerability-review/$ExpectedHead/current-safe.json"

    $VulnerabilitySafeSha256 =
        Get-FileSha256 -Path $VulnerabilitySafePath

    $VulnerabilitySafe =
        Get-Content `
            -LiteralPath $VulnerabilitySafePath `
            -Raw `
            -Encoding UTF8 |
        ConvertFrom-Json

    if ([bool] $VulnerabilitySafe.review.unresolvedCriticalHighBlocker) {
        throw 'Vulnerability review reports an unresolved Critical/High blocker.'
    }

    if ([bool] $VulnerabilitySafe.review.suppressionOrRetuningUsed) {
        throw 'Vulnerability review unexpectedly used suppression/retuning.'
    }

    $ProvenancePath =
        Join-Path `
            $Worktree `
            ".runtime/security/supply-chain/$ExpectedHead/local-build-provenance.json"

    $ProvenanceSha256 =
        Get-FileSha256 -Path $ProvenancePath

    $Provenance =
        Get-Content `
            -LiteralPath $ProvenancePath `
            -Raw `
            -Encoding UTF8 |
        ConvertFrom-Json

    Assert-Equal `
        -Label 'Supply-chain evidence HEAD' `
        -Actual ([string] $Provenance.scope.commitSha) `
        -Expected $ExpectedHead

    $SbomSha256 =
        [string] $Provenance.sbom.evidenceSha256

    $Docker =
        Get-Command docker -ErrorAction Stop

    $DockerExe =
        $Docker.Source

    $DockerVersion = (
        Invoke-Required `
            -FilePath $DockerExe `
            -Arguments @(
                'version',
                '--format',
                '{{.Server.Version}}'
            ) `
            -FailureMessage 'Docker daemon is unavailable.'
    ).Text.Trim()

    $ComposeVersion = (
        Invoke-Required `
            -FilePath $DockerExe `
            -Arguments @('compose', 'version', '--short') `
            -FailureMessage 'Docker Compose is unavailable.'
    ).Text.Trim()

    Invoke-Required `
        -FilePath $DockerExe `
        -Arguments @('pull', $BuilderImage) `
        -FailureMessage 'Unable to pull immutable builder image.' |
    Out-Null

    Invoke-Required `
        -FilePath $DockerExe `
        -Arguments @('volume', 'create', $JwtVolume) `
        -FailureMessage 'Unable to create isolated JWT rehearsal volume.' |
    Out-Null

    $JwtVolumeCreated = $true

    $JwtMount =
        "type=volume,source=$JwtVolume,target=/keys"

    $KeyGeneration =
        Invoke-Captured `
            -FilePath $DockerExe `
            -Arguments @(
                'run',
                '--rm',
                '--mount',
                $JwtMount,
                '--entrypoint',
                'sh',
                $BuilderImage,
                '-lc',
                (
                    'set -eu; ' +
                    'openssl genpkey -algorithm RSA ' +
                    '-pkeyopt rsa_keygen_bits:2048 ' +
                    '-out /keys/active-private.pem >/dev/null 2>&1; ' +
                    'openssl pkey -in /keys/active-private.pem ' +
                    '-pubout -out /keys/active-public.pem >/dev/null 2>&1; ' +
                    'chmod 0400 /keys/active-private.pem; ' +
                    'chmod 0444 /keys/active-public.pem; ' +
                    'chown 10001:10001 /keys/active-private.pem ' +
                    '/keys/active-public.pem'
                )
            )

    if ($KeyGeneration.ExitCode -ne 0) {
        throw 'Synthetic JWT key generation failed.'
    }

    $JwtReadProbe =
        Invoke-Captured `
            -FilePath $DockerExe `
            -Arguments @(
                'run',
                '--rm',
                '--user',
                '10001:10001',
                '--mount',
                $JwtMount,
                '--entrypoint',
                'sh',
                $BuilderImage,
                '-lc',
                (
                    'test -r /keys/active-private.pem && ' +
                    'test -r /keys/active-public.pem && ' +
                    'test -s /keys/active-private.pem && ' +
                    'test -s /keys/active-public.pem'
                )
            )

    if ($JwtReadProbe.ExitCode -ne 0) {
        throw 'UID 10001 cannot read JWT rehearsal keys.'
    }

    $GrafanaPassword =
        [guid]::NewGuid().ToString('N')

    $MailKey =
        New-RandomBase64

    $MfaKey =
        New-RandomBase64

    Set-Item `
        -LiteralPath 'Env:GRAFANA_ADMIN_PASSWORD' `
        -Value $GrafanaPassword

    Set-Item `
        -LiteralPath 'Env:MFA_SECRET_ENCRYPTION_KEY' `
        -Value $MfaKey

    Set-Item `
        -LiteralPath 'Env:REHEARSAL_JWT_VOLUME' `
        -Value $JwtVolume

    $OverrideText = @"
services:
  postgres:
    ports: !override []
  redis:
    ports: !override []
  kafka:
    ports: !override []
  mailpit:
    ports: !override []
  app:
    ports: !override
      - "127.0.0.1::8080"
    environment:
      SPRING_PROFILES_ACTIVE: production
      JWT_ACTIVE_KEY_ID: release-rehearsal-active
      JWT_ACTIVE_PRIVATE_KEY_LOCATION: file:/run/secrets/payflow/jwt/active-private.pem
      JWT_ACTIVE_PUBLIC_KEY_LOCATION: file:/run/secrets/payflow/jwt/active-public.pem
    volumes:
      - rehearsal-jwt:/run/secrets/payflow/jwt:ro

volumes:
  rehearsal-jwt:
    external: true
    name: `${REHEARSAL_JWT_VOLUME:?REHEARSAL_JWT_VOLUME must be set}
"@

    Write-Utf8NoBom `
        -Path $OverridePath `
        -Text ($OverrideText.TrimStart() + "`n")

    $ComposeReady = $true

    Remove-Item `
        -LiteralPath "Env:$RequiredConfigName" `
        -ErrorAction SilentlyContinue

    $FailFast =
        Invoke-Captured `
            -FilePath $DockerExe `
            -Arguments @(
                'compose',
                '-p', $ComposeProject,
                '-f', 'compose.yml',
                '-f', $OverridePath,
                '--profile', 'app',
                'config',
                '--quiet'
            ) `
            -WorkingDirectory $Worktree

    if ($FailFast.ExitCode -eq 0) {
        throw 'Required production configuration unexpectedly passed.'
    }

    if (-not $FailFast.Text.Contains($RequiredConfigMarker)) {
        throw 'Required-config fail-fast marker was not observed.'
    }

    $CreatedContainers = (
        Invoke-Required `
            -FilePath $DockerExe `
            -Arguments @(
                'ps',
                '-a',
                '--filter',
                "label=com.docker.compose.project=$ComposeProject",
                '--format',
                '{{.ID}}'
            ) `
            -FailureMessage 'Unable to verify fail-fast container state.'
    ).Text

    if (-not [string]::IsNullOrWhiteSpace($CreatedContainers)) {
        throw 'Required-config fail-fast unexpectedly created containers.'
    }

    Set-Item `
        -LiteralPath "Env:$RequiredConfigName" `
        -Value $MailKey

    Invoke-Required `
        -FilePath $DockerExe `
        -Arguments @(
            'compose',
            '-p', $ComposeProject,
            '-f', 'compose.yml',
            '-f', $OverridePath,
            '--profile', 'app',
            'config',
            '--quiet'
        ) `
        -FailureMessage 'Production rehearsal Compose validation failed.' `
        -WorkingDirectory $Worktree |
    Out-Null

    $Up =
        Invoke-Captured `
            -FilePath $DockerExe `
            -Arguments @(
                'compose',
                '-p', $ComposeProject,
                '-f', 'compose.yml',
                '-f', $OverridePath,
                '--profile', 'app',
                'up',
                '-d',
                '--build',
                'postgres',
                'redis',
                'kafka',
                'app'
            ) `
            -WorkingDirectory $Worktree

    if ($Up.ExitCode -ne 0) {
        throw 'Production rehearsal stack startup failed.'
    }

    $PublishedPort = (
        Invoke-Required `
            -FilePath $DockerExe `
            -Arguments @(
                'compose',
                '-p', $ComposeProject,
                '-f', 'compose.yml',
                '-f', $OverridePath,
                '--profile', 'app',
                'port',
                'app',
                '8080'
            ) `
            -FailureMessage 'Unable to resolve dynamic app health port.' `
            -WorkingDirectory $Worktree
    ).Text.Trim()

    $PortMatch =
        [regex]::Match(
            $PublishedPort,
            '^127\.0\.0\.1:(\d+)$'
        )

    if (-not $PortMatch.Success) {
        throw "Unexpected app health binding: $PublishedPort"
    }

    $HealthPort =
        [int] $PortMatch.Groups[1].Value

    $HealthUri =
        "http://127.0.0.1:$HealthPort/api/v1/system/health"

    $Healthy = $false
    $HealthResponse = $null

    for ($Attempt = 1; $Attempt -le 75; $Attempt++) {
        try {
            $HealthResponse =
                Invoke-WebRequest `
                    -UseBasicParsing `
                    -Uri $HealthUri `
                    -Method Get `
                    -TimeoutSec 3 `
                    -ErrorAction Stop

            if ([int] $HealthResponse.StatusCode -eq 200) {
                $Healthy = $true
                break
            }
        }
        catch {
        }

        Start-Sleep -Seconds 2
    }

    if (-not $Healthy) {
        throw 'Production-profile health endpoint did not become ready.'
    }

    $CorrelationId =
        [string] $HealthResponse.Headers['X-Correlation-ID']

    if ([string]::IsNullOrWhiteSpace($CorrelationId)) {
        throw 'Health response did not expose X-Correlation-ID.'
    }

    if ($CorrelationId.Length -gt 64) {
        throw 'Health response correlation ID exceeds reviewed bound.'
    }

    $AppLogs =
        Invoke-Captured `
            -FilePath $DockerExe `
            -Arguments @(
                'compose',
                '-p', $ComposeProject,
                '-f', 'compose.yml',
                '-f', $OverridePath,
                '--profile', 'app',
                'logs',
                '--no-color',
                'app'
            ) `
            -WorkingDirectory $Worktree

    if ($AppLogs.ExitCode -ne 0) {
        throw 'Unable to inspect production app logs.'
    }

    $CompletionEvent =
        $AppLogs.Text.Contains(
            '"event":"http.request.completed"'
        )

    $CorrelationBinding =
        $AppLogs.Text.Contains(
            '"correlationId":"' + $CorrelationId + '"'
        )

    $RouteBinding =
        $AppLogs.Text.Contains(
            '"http.route":"/api/v1/system/health"'
        )

    $SuccessOutcome =
        $AppLogs.Text.Contains(
            '"outcome":"SUCCESS"'
        )

    if (
        -not $CompletionEvent -or
        -not $CorrelationBinding -or
        -not $RouteBinding -or
        -not $SuccessOutcome
    ) {
        throw 'Structured completion-log contract failed.'
    }

    $SafeEvidence = [ordered]@{
        schemaVersion = 1
        evidenceKind = 'payflow-clean-environment-release-rehearsal'
        source = [ordered]@{
            commitSha = $ExpectedHead
            gitTreeSha = $TreeSha
            projectVersion = $ProjectVersion
            checkoutKind = 'detached-git-worktree'
            preexistingTarget = $false
            preexistingRuntime = $false
        }
        toolchain = [ordered]@{
            javaVersion = $JavaVersion
            mavenVersion = $MavenVersion
            mavenWrapperMode = '100755'
            mavenWrapperDistributionSha256 =
                $ExpectedWrapperDistributionSha256
            dockerServerVersion = $DockerVersion
            dockerComposeVersion = $ComposeVersion
            dockerBuilder = $BuilderImage
            dockerRuntime = $RuntimeImage
        }
        completeVerification = [ordered]@{
            tests = [long] $TestSummary.Tests
            failures = [long] $TestSummary.Failures
            errors = [long] $TestSummary.Errors
            skipped = [long] $TestSummary.Skipped
            logSha256 = $VerifyLogSha256
        }
        artifactRehearsal = [ordered]@{
            artifactName = $JarName
            artifactSizeBytes = [long] $JarInfo.Length
            artifactSha256 = $ArtifactSha256
            checksumFormat = 'sha256sum-compatible'
            checksumEvidenceSha256 = $ChecksumEvidenceSha256
            tagCreated = $false
            releasePublished = $false
        }
        committedEvidence = [ordered]@{
            gitleaksBaselinePass = $true
            gitleaksSafeEvidenceSha256 = $GitleaksSafeSha256
            vulnerabilityReviewPass = $true
            vulnerabilitySafeEvidenceSha256 = $VulnerabilitySafeSha256
            supplyChainLocalProvenanceSha256 = $ProvenanceSha256
            supplyChainSbomSha256 = $SbomSha256
            unresolvedCriticalHighBlocker = $false
            suppressionOrRetuningUsed = $false
        }
        requiredConfigurationFailFast = [ordered]@{
            configuration = $RequiredConfigName
            valueIntentionallyOmitted = $true
            validationLayer = 'docker-compose-pre-start'
            reviewedMarkerObserved = $true
            containersCreated = $false
            rawFailureOutputRetained = $false
        }
        productionDockerSmoke = [ordered]@{
            composeProfile = 'app'
            springProfile = 'production'
            jwtStorage = 'docker-managed-named-volume'
            jwtOwner = '10001:10001'
            jwtPrivateMode = '0400'
            jwtPublicMode = '0444'
            jwtUid10001ReadProbe = $true
            hostServicePortsRequired = $false
            appHostBinding = 'dynamic-loopback'
            healthEndpoint = '/api/v1/system/health'
            httpStatus = 200
            correlationIdPresent = $true
            correlationIdLength = $CorrelationId.Length
            completionEventPresent = $CompletionEvent
            correlationLogBindingPresent = $CorrelationBinding
            routeLogPresent = $RouteBinding
            successOutcomePresent = $SuccessOutcome
            rawApplicationLogsRetained = $false
        }
        evidenceBoundary = [ordered]@{
            generatedSecretsRecorded = $false
            privateKeysRecorded = $false
            rawApplicationLogsRecorded = $false
            machineSpecificPathRecorded = $false
            signingClaim = $false
            slsaClaim = $false
            reproducibleBuildClaim = $false
            provenanceAttestationClaim = $false
            productionCertificationClaim = $false
            publicationClaim = $false
        }
    }

    Write-Utf8NoBom `
        -Path $SafeEvidencePath `
        -Text (
            (
                $SafeEvidence |
                    ConvertTo-Json -Depth 12
            ) + "`n"
        )

    $SafeEvidenceSha256 =
        Get-FileSha256 -Path $SafeEvidencePath

    $Passed = $true
}
finally {
    if (
        $ComposeReady -and
        $WorktreeCreated -and
        $null -ne $DockerExe
    ) {
        Invoke-Captured `
            -FilePath $DockerExe `
            -Arguments @(
                'compose',
                '-p', $ComposeProject,
                '-f', 'compose.yml',
                '-f', $OverridePath,
                '--profile', 'app',
                'down',
                '-v',
                '--remove-orphans'
            ) `
            -WorkingDirectory $Worktree |
        Out-Null
    }

    if (
        $JwtVolumeCreated -and
        $null -ne $DockerExe
    ) {
        Invoke-Captured `
            -FilePath $DockerExe `
            -Arguments @(
                'volume',
                'rm',
                '-f',
                $JwtVolume
            ) |
        Out-Null
    }

    Restore-Environment `
        -Snapshot $EnvironmentSnapshot

    Remove-Item `
        -LiteralPath $OverridePath `
        -Force `
        -ErrorAction SilentlyContinue

    if ($WorktreeCreated) {
        Invoke-Captured `
            -FilePath 'git' `
            -Arguments @(
                'worktree',
                'remove',
                '--force',
                $Worktree
            ) `
            -WorkingDirectory $RepoRoot |
        Out-Null

        & git worktree prune
    }

    Set-Location -LiteralPath $RepoRoot
}

if (-not $Passed) {
    throw 'Clean-environment release rehearsal did not reach PASS.'
}

$FinalHead = (
    Invoke-Required `
        -FilePath 'git' `
        -Arguments @('rev-parse', 'HEAD') `
        -FailureMessage 'Unable to resolve final source HEAD.'
).Text.Trim()

Assert-Equal `
    -Label 'Final source HEAD' `
    -Actual $FinalHead `
    -Expected $ExpectedHead

$FinalStatus = (
    Invoke-Required `
        -FilePath 'git' `
        -Arguments @(
            'status',
            '--porcelain=v1',
            '--untracked-files=all'
        ) `
        -FailureMessage 'Unable to inspect final source working tree.'
).Text

if (-not [string]::IsNullOrWhiteSpace($FinalStatus)) {
    throw 'Source repository changed during rehearsal.'
}

$FinalDiffCheck =
    Invoke-Captured `
        -FilePath 'git' `
        -Arguments @('diff', '--check')

if ($FinalDiffCheck.ExitCode -ne 0) {
    throw 'Final git diff --check failed.'
}

Write-Host ''
Write-Host '=============================================' `
    -ForegroundColor Green
Write-Host 'PAYFLOW CLEAN-ENVIRONMENT RELEASE REHEARSAL PASS' `
    -ForegroundColor Green
Write-Host '=============================================' `
    -ForegroundColor Green
Write-Host "HEAD                    : $ExpectedHead"
Write-Host "Project version         : $ProjectVersion"
Write-Host "Java                    : $JavaVersion"
Write-Host "Maven Wrapper           : $MavenVersion"
Write-Host (
    'Tests                   : ' +
    "$($TestSummary.Tests) / " +
    "$($TestSummary.Failures) / " +
    "$($TestSummary.Errors) / " +
    "$($TestSummary.Skipped)"
)
Write-Host "Verify log SHA          : $VerifyLogSha256"
Write-Host "Artifact SHA            : $ArtifactSha256"
Write-Host "Checksum evidence SHA   : $ChecksumEvidenceSha256"
Write-Host 'Gitleaks baseline       : PASS'
Write-Host "Gitleaks evidence SHA   : $GitleaksSafeSha256"
Write-Host 'Vulnerability review    : PASS'
Write-Host "Vulnerability evidence  : $VulnerabilitySafeSha256"
Write-Host "Supply-chain SBOM SHA   : $SbomSha256"
Write-Host 'Required config failfast: PASS'
Write-Host 'Production Docker smoke : PASS'
Write-Host 'JWT storage             : Docker-managed named volume'
Write-Host 'JWT owner/modes         : 10001:10001 / 0400 / 0444'
Write-Host 'Host service ports      : NOT REQUIRED'
Write-Host 'App host binding        : dynamic loopback'
Write-Host 'Health/correlation/log  : PASS'
Write-Host "Safe evidence SHA       : $SafeEvidenceSha256"
Write-Host 'Source working tree     : CLEAN'
Write-Host 'Tag/release mutation    : NONE'
