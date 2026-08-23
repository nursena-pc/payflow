param()

$ErrorActionPreference = 'Stop'

function Invoke-Captured {
    param(
        [Parameter(Mandatory)]
        [string] $FilePath,

        [Parameter(Mandatory)]
        [string[]] $Arguments
    )

    $PreviousErrorActionPreference = $ErrorActionPreference
    $HasNativePreference = $null -ne (
        Get-Variable `
            -Name PSNativeCommandUseErrorActionPreference `
            -ErrorAction SilentlyContinue
    )

    if ($HasNativePreference) {
        $PreviousNativePreference =
            $PSNativeCommandUseErrorActionPreference
    }

    try {
        $ErrorActionPreference = 'Continue'

        if ($HasNativePreference) {
            $PSNativeCommandUseErrorActionPreference = $false
        }

        $Output = @(& $FilePath @Arguments 2>&1)
        $ExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $PreviousErrorActionPreference

        if ($HasNativePreference) {
            $PSNativeCommandUseErrorActionPreference =
                $PreviousNativePreference
        }
    }

    return [pscustomobject]@{
        ExitCode = [int] $ExitCode
        Lines = @($Output | ForEach-Object { "$_" })
        Text = (($Output | ForEach-Object { "$_" }) -join "`n").Trim()
    }
}

function Invoke-Required {
    param(
        [Parameter(Mandatory)]
        [string] $FilePath,

        [Parameter(Mandatory)]
        [string[]] $Arguments,

        [Parameter(Mandatory)]
        [string] $FailureMessage
    )

    $Result = Invoke-Captured `
        -FilePath $FilePath `
        -Arguments $Arguments

    if ($Result.ExitCode -ne 0) {
        throw "$FailureMessage Exit code: $($Result.ExitCode)"
    }

    return $Result
}

function Get-FileSha256 {
    param(
        [Parameter(Mandatory)]
        [string] $Path
    )

    return (
        Get-FileHash `
            -LiteralPath $Path `
            -Algorithm SHA256
    ).Hash.ToLowerInvariant()
}

function Convert-ScannerPathToRepoPath {
    param(
        [Parameter(Mandatory)]
        [string] $File
    )

    $Normalized =
        $File.Replace('\', '/').Trim()

    if ($Normalized.StartsWith('/src/')) {
        $Normalized =
            $Normalized.Substring('/src/'.Length)
    }

    while ($Normalized.StartsWith('./')) {
        $Normalized =
            $Normalized.Substring(2)
    }

    if (
        [string]::IsNullOrWhiteSpace($Normalized) -or
        $Normalized.StartsWith('/') -or
        $Normalized -match '^[A-Za-z]:/' -or
        $Normalized -eq '..' -or
        $Normalized.StartsWith('../') -or
        $Normalized.Contains('/../')
    ) {
        throw "Unsafe scanner path: $File"
    }

    return $Normalized
}

function Get-LineDigest {
    param(
        [Parameter(Mandatory)]
        [string] $SnapshotRoot,

        [Parameter(Mandatory)]
        [string] $RepoPath,

        [Parameter(Mandatory)]
        [int] $LineNumber
    )

    $Path =
        Join-Path `
            $SnapshotRoot `
            ($RepoPath.Replace('/', [IO.Path]::DirectorySeparatorChar))

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Finding path is missing from the committed snapshot: $RepoPath"
    }

    $Lines =
        Get-Content `
            -LiteralPath $Path `
            -Encoding UTF8

    if (
        $LineNumber -lt 1 -or
        $LineNumber -gt $Lines.Count
    ) {
        throw "Finding line is outside the committed file: ${RepoPath}:$LineNumber"
    }

    $Line =
        [string] $Lines[$LineNumber - 1]

    $Bytes =
        [Text.Encoding]::UTF8.GetBytes($Line)

    $Hasher =
        [Security.Cryptography.SHA256]::Create()

    try {
        return (
            [BitConverter]::ToString(
                $Hasher.ComputeHash($Bytes)
            )
        ).Replace('-', '').ToLowerInvariant()
    }
    finally {
        $Hasher.Dispose()
    }
}

function Get-FindingKey {
    param(
        [Parameter(Mandatory)]
        [string] $RuleId,

        [Parameter(Mandatory)]
        [string] $Path,

        [Parameter(Mandatory)]
        [int] $StartLine,

        [Parameter(Mandatory)]
        [int] $EndLine
    )

    return (
        '{0}|{1}|{2}|{3}' -f
        $RuleId,
        $Path,
        $StartLine,
        $EndLine
    )
}

$RepoRoot = (
    Invoke-Required `
        -FilePath 'git' `
        -Arguments @('rev-parse', '--show-toplevel') `
        -FailureMessage 'Not inside a Git repository.'
).Text.Trim()

Set-Location -LiteralPath $RepoRoot

$Status = (
    Invoke-Required `
        -FilePath 'git' `
        -Arguments @(
            'status',
            '--porcelain=v1',
            '--untracked-files=all'
        ) `
        -FailureMessage 'Unable to inspect working tree.'
).Text

if (-not [string]::IsNullOrWhiteSpace($Status)) {
    throw 'Secret-scan verification requires a clean working tree.'
}

$BaselinePath =
    Join-Path $RepoRoot 'docs/security/gitleaks-baseline.json'

if (-not (Test-Path -LiteralPath $BaselinePath -PathType Leaf)) {
    throw 'Missing docs/security/gitleaks-baseline.json.'
}

$Baseline =
    Get-Content `
        -LiteralPath $BaselinePath `
        -Raw `
        -Encoding UTF8 |
    ConvertFrom-Json

if ([int] $Baseline.schemaVersion -ne 1) {
    throw 'Unsupported Gitleaks baseline schema version.'
}

$Image =
    [string] $Baseline.scanner.image

if (
    [string]::IsNullOrWhiteSpace($Image) -or
    $Image -notmatch '^ghcr\.io/gitleaks/gitleaks@sha256:[0-9a-f]{64}$'
) {
    throw 'Baseline does not pin an immutable Gitleaks image.'
}

$ExpectedFindings =
    @($Baseline.findings)

if ($ExpectedFindings.Count -eq 0) {
    throw 'Baseline must contain the explicitly reviewed finding set.'
}

$ExpectedByKey = @{}

foreach ($Finding in $ExpectedFindings) {
    $Key =
        Get-FindingKey `
            -RuleId ([string] $Finding.ruleId) `
            -Path ([string] $Finding.path) `
            -StartLine ([int] $Finding.startLine) `
            -EndLine ([int] $Finding.endLine)

    if ($ExpectedByKey.ContainsKey($Key)) {
        throw "Duplicate baseline finding key: $Key"
    }

    $ExpectedByKey[$Key] = $Finding
}

$Docker =
    Get-Command 'docker' -ErrorAction Stop

Invoke-Required `
    -FilePath $Docker.Source `
    -Arguments @(
        'version',
        '--format',
        '{{.Server.Version}}'
    ) `
    -FailureMessage 'Docker daemon is unavailable.' |
    Out-Null

Invoke-Required `
    -FilePath $Docker.Source `
    -Arguments @(
        'pull',
        $Image
    ) `
    -FailureMessage 'Unable to pull pinned Gitleaks image.' |
    Out-Null

$RuntimeDir =
    Join-Path $RepoRoot '.runtime/security/gitleaks'

$IgnoreProbe = Invoke-Captured `
    -FilePath 'git' `
    -Arguments @('check-ignore', '-q', '.runtime/')

if ($IgnoreProbe.ExitCode -ne 0) {
    throw '.runtime must remain ignored before secret-scan evidence is written.'
}

New-Item `
    -ItemType Directory `
    -Path $RuntimeDir `
    -Force |
    Out-Null

$SafeEvidencePath =
    Join-Path $RuntimeDir 'current-safe.json'

$TempRoot =
    Join-Path `
        ([IO.Path]::GetTempPath()) `
        ('payflow-gitleaks-' + [guid]::NewGuid().ToString('N'))

$SnapshotDir =
    Join-Path $TempRoot 'snapshot'

$RawDir =
    Join-Path $TempRoot 'raw'

$ArchivePath =
    Join-Path $TempRoot 'head.zip'

New-Item -ItemType Directory -Path $SnapshotDir -Force | Out-Null
New-Item -ItemType Directory -Path $RawDir -Force | Out-Null

try {
    Invoke-Required `
        -FilePath 'git' `
        -Arguments @(
            'archive',
            '--format=zip',
            "--output=$ArchivePath",
            'HEAD'
        ) `
        -FailureMessage 'Unable to archive committed HEAD.' |
        Out-Null

    Expand-Archive `
        -LiteralPath $ArchivePath `
        -DestinationPath $SnapshotDir `
        -Force

    $RawReport =
        Join-Path $RawDir 'gitleaks-raw.json'

    $SnapshotMount =
        "type=bind,source=$SnapshotDir,target=/src,readonly"

    $RawMount =
        "type=bind,source=$RawDir,target=/work"

    $Scan = Invoke-Captured `
        -FilePath $Docker.Source `
        -Arguments @(
            'run',
            '--rm',
            '--mount',
            $SnapshotMount,
            '--mount',
            $RawMount,
            $Image,
            'dir',
            '--redact=100',
            '--exit-code=5',
            '--report-format=json',
            '--report-path=/work/gitleaks-raw.json',
            '--no-banner',
            '--no-color',
            '--log-level=fatal',
            '/src'
        )

    if (
        $Scan.ExitCode -ne 0 -and
        $Scan.ExitCode -ne 5
    ) {
        throw "Gitleaks operational failure. Exit code: $($Scan.ExitCode)"
    }

    $RawFindings = @()

    if (Test-Path -LiteralPath $RawReport -PathType Leaf) {
        $RawText =
            Get-Content `
                -LiteralPath $RawReport `
                -Raw `
                -Encoding UTF8

        if (-not [string]::IsNullOrWhiteSpace($RawText)) {
            $Parsed =
                $RawText | ConvertFrom-Json

            if ($null -ne $Parsed) {
                $RawFindings =
                    if ($Parsed -is [System.Array]) {
                        @($Parsed)
                    }
                    else {
                        @($Parsed)
                    }
            }
        }
    }

    $ActualSafe = @()
    $ActualByKey = @{}

    foreach ($Finding in $RawFindings) {
        $RepoPath =
            Convert-ScannerPathToRepoPath `
                -File ([string] $Finding.File)

        $StartLine = [int] $Finding.StartLine
        $EndLine = [int] $Finding.EndLine
        $RuleId = [string] $Finding.RuleID

        $LineDigest =
            Get-LineDigest `
                -SnapshotRoot $SnapshotDir `
                -RepoPath $RepoPath `
                -LineNumber $StartLine

        $Key =
            Get-FindingKey `
                -RuleId $RuleId `
                -Path $RepoPath `
                -StartLine $StartLine `
                -EndLine $EndLine

        if ($ActualByKey.ContainsKey($Key)) {
            throw "Duplicate current finding key: $Key"
        }

        $SafeRow = [pscustomobject]@{
            ruleId = $RuleId
            path = $RepoPath
            startLine = $StartLine
            endLine = $EndLine
            lineDigestSha256 = $LineDigest
        }

        $ActualByKey[$Key] = $SafeRow
        $ActualSafe += $SafeRow
    }

    if (Test-Path -LiteralPath $RawReport -PathType Leaf) {
        Remove-Item -LiteralPath $RawReport -Force
    }

    $Problems = @()

    foreach ($Key in $ActualByKey.Keys) {
        if (-not $ExpectedByKey.ContainsKey($Key)) {
            $Problems += "NEW:$Key"
            continue
        }

        $ExpectedDigest =
            [string] $ExpectedByKey[$Key].lineDigestSha256

        $ActualDigest =
            [string] $ActualByKey[$Key].lineDigestSha256

        if ($ActualDigest -ne $ExpectedDigest) {
            $Problems += "CHANGED:$Key"
        }
    }

    foreach ($Key in $ExpectedByKey.Keys) {
        if (-not $ActualByKey.ContainsKey($Key)) {
            $Problems += "MISSING:$Key"
        }
    }

    $Head = (
        Invoke-Required `
            -FilePath 'git' `
            -Arguments @('rev-parse', 'HEAD') `
            -FailureMessage 'Unable to resolve HEAD.'
    ).Text.Trim()

    $Evidence = [ordered]@{
        schemaVersion = 1
        head = $Head
        scannerImage = $Image
        findingCount = $ActualSafe.Count
        findings = @(
            $ActualSafe |
                Sort-Object path, startLine, ruleId
        )
    }

    $EvidenceJson =
        $Evidence |
            ConvertTo-Json -Depth 6

    [IO.File]::WriteAllText(
        $SafeEvidencePath,
        ($EvidenceJson + "`n"),
        [Text.UTF8Encoding]::new($false)
    )

    $EvidenceHash =
        Get-FileSha256 -Path $SafeEvidencePath

    Write-Host "Committed HEAD       : $Head"
    Write-Host "Pinned scanner       : $Image"
    Write-Host "Reviewed findings    : $($ExpectedFindings.Count)"
    Write-Host "Observed findings    : $($ActualSafe.Count)"
    Write-Host "Safe evidence SHA-256: $EvidenceHash"
    Write-Host 'Secret/match/source lines printed: NO'

    if ($Problems.Count -ne 0) {
        foreach ($Problem in ($Problems | Sort-Object)) {
            Write-Host $Problem
        }

        throw 'Committed-content secret-scan baseline mismatch.'
    }

    Write-Host 'Gitleaks committed-content baseline: PASS' `
        -ForegroundColor Green
}
finally {
    if (Test-Path -LiteralPath $TempRoot) {
        Remove-Item `
            -LiteralPath $TempRoot `
            -Recurse `
            -Force `
            -ErrorAction SilentlyContinue
    }
}
