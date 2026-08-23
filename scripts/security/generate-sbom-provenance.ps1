param(
    [Parameter(Mandatory)]
    [ValidatePattern('^[0-9a-f]{40}$')]
    [string] $ExpectedHead
)

$ErrorActionPreference = 'Stop'

$SyftVersion =
    '1.50.0'

$SyftImage =
    'ghcr.io/anchore/syft@sha256:1288ea4c8b38767b4e620c1e312c8cb26b6e887a99b4f07ab6cd19fc6f225026'

$ExpectedMavenVersion =
    '3.9.16'

$ExpectedJavaMajor =
    '21'

$ExpectedWrapperDistributionSha256 =
    '5af3b743dd8b876b5c45da33b676251e5f1687712644abb4ee519ca56e1d89ce'

$ExpectedBuilderLine =
    'FROM maven:3.9.16-eclipse-temurin-21-noble@sha256:613124833fa6718ded9d655a2ebfab6425818c178f899116b93560b6f1c9ffe9 AS build'

$ExpectedRuntimeLine =
    'FROM eclipse-temurin:21-jre-noble@sha256:981e055f0f1d1518a0e7307840f22247e55d91fe000f4b0f5bd01681d79ed126'

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
        Lines = @(
            $Output |
                ForEach-Object { "$_" }
        )
        Text = (
            (
                $Output |
                    ForEach-Object { "$_" }
            ) -join "`n"
        ).Trim()
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
        if (-not [string]::IsNullOrWhiteSpace($Result.Text)) {
            Write-Host $Result.Text
        }

        throw "$FailureMessage Exit code: $($Result.ExitCode)"
    }

    return $Result
}

function Get-FileSha256 {
    param(
        [Parameter(Mandatory)]
        [string] $Path
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Missing file: $Path"
    }

    return (
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

$RepoRoot = (
    Invoke-Required `
        -FilePath 'git' `
        -Arguments @(
            'rev-parse',
            '--show-toplevel'
        ) `
        -FailureMessage 'Not inside a Git repository.'
).Text.Trim()

Set-Location -LiteralPath $RepoRoot

$Head = (
    Invoke-Required `
        -FilePath 'git' `
        -Arguments @(
            'rev-parse',
            'HEAD'
        ) `
        -FailureMessage 'Unable to resolve HEAD.'
).Text.Trim()

if ($Head -ne $ExpectedHead) {
    throw "Expected HEAD $ExpectedHead but found $Head."
}

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
    throw 'Supply-chain evidence generation requires a clean working tree.'
}

$DiffCheck = Invoke-Captured `
    -FilePath 'git' `
    -Arguments @(
        'diff',
        '--check'
    )

if ($DiffCheck.ExitCode -ne 0) {
    throw 'git diff --check failed.'
}

$TreeSha = (
    Invoke-Required `
        -FilePath 'git' `
        -Arguments @(
            'rev-parse',
            'HEAD^{tree}'
        ) `
        -FailureMessage 'Unable to resolve Git tree SHA.'
).Text.Trim()

$MvnwMode = (
    Invoke-Required `
        -FilePath 'git' `
        -Arguments @(
            'ls-tree',
            'HEAD',
            '--',
            'mvnw'
        ) `
        -FailureMessage 'Unable to inspect committed mvnw mode.'
).Text.Trim()

if ($MvnwMode -notmatch '^100755\s+blob\s+') {
    throw "Committed mvnw mode is not 100755: $MvnwMode"
}

$MvnwCmd =
    Join-Path $RepoRoot 'mvnw.cmd'

$MavenVersionOutput = Invoke-Required `
    -FilePath $MvnwCmd `
    -Arguments @(
        '-version'
    ) `
    -FailureMessage 'Maven Wrapper version probe failed.'

$MavenLine = @(
    $MavenVersionOutput.Lines |
        Where-Object {
            $_ -match '^Apache Maven\s+'
        }
)

$JavaLine = @(
    $MavenVersionOutput.Lines |
        Where-Object {
            $_ -match '^Java version:\s+'
        }
)

if (
    $MavenLine.Count -ne 1 -or
    $JavaLine.Count -ne 1
) {
    throw 'Unable to identify Maven/Java versions.'
}

$MavenVersion =
    (
        $MavenLine[0] -replace '^Apache Maven\s+', ''
    ).Split(' ')[0].Trim()

if ($MavenVersion -ne $ExpectedMavenVersion) {
    throw "Expected Maven $ExpectedMavenVersion; got $MavenVersion."
}

$JavaVersionMatch =
    [regex]::Match(
        $JavaLine[0],
        '^Java version:\s*([^,\s]+)'
    )

if (-not $JavaVersionMatch.Success) {
    throw 'Unable to parse Java version.'
}

$JavaVersion =
    $JavaVersionMatch.Groups[1].Value

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
        -FailureMessage 'Unable to resolve project version.'
).Text.Trim()

if ([string]::IsNullOrWhiteSpace($ProjectVersion)) {
    throw 'Project version is empty.'
}

$WrapperPath =
    Join-Path `
        $RepoRoot `
        '.mvn/wrapper/maven-wrapper.properties'

$WrapperText =
    Get-Content `
        -LiteralPath $WrapperPath `
        -Raw `
        -Encoding UTF8

$DistributionUrlMatch =
    [regex]::Match(
        $WrapperText,
        '(?m)^distributionUrl=(.+)$'
    )

$DistributionShaMatch =
    [regex]::Match(
        $WrapperText,
        '(?m)^distributionSha256Sum=([0-9a-fA-F]{64})$'
    )

if (
    -not $DistributionUrlMatch.Success -or
    -not $DistributionShaMatch.Success
) {
    throw 'Maven Wrapper distribution pin is incomplete.'
}

$WrapperDistributionUrl =
    $DistributionUrlMatch.Groups[1].Value.Trim()

$WrapperDistributionSha256 =
    $DistributionShaMatch.Groups[1].Value.ToLowerInvariant()

if (
    $WrapperDistributionSha256 -ne
    $ExpectedWrapperDistributionSha256
) {
    throw 'Maven Wrapper distribution SHA-256 drifted.'
}

$DockerfilePath =
    Join-Path $RepoRoot 'Dockerfile'

$DockerfileLines =
    Get-Content `
        -LiteralPath $DockerfilePath `
        -Encoding UTF8

if (
    @(
        $DockerfileLines |
            Where-Object {
                $_ -eq $ExpectedBuilderLine
            }
    ).Count -ne 1
) {
    throw 'Reviewed immutable Docker builder pin drifted.'
}

if (
    @(
        $DockerfileLines |
            Where-Object {
                $_ -eq $ExpectedRuntimeLine
            }
    ).Count -ne 1
) {
    throw 'Reviewed immutable Docker runtime pin drifted.'
}

$IgnoreProbe = Invoke-Captured `
    -FilePath 'git' `
    -Arguments @(
        'check-ignore',
        '-q',
        '.runtime/'
    )

if ($IgnoreProbe.ExitCode -ne 0) {
    throw '.runtime must remain ignored.'
}

$EvidenceDir =
    Join-Path `
        $RepoRoot `
        ".runtime/security/supply-chain/$ExpectedHead"

New-Item `
    -ItemType Directory `
    -Path $EvidenceDir `
    -Force |
    Out-Null

$DependencyTreePath =
    Join-Path $EvidenceDir 'dependency-tree.txt'

$GitTreeManifestPath =
    Join-Path $EvidenceDir 'git-tree-manifest.txt'

$ProvenancePath =
    Join-Path $EvidenceDir 'local-build-provenance.json'

$BuildLogPath =
    Join-Path $EvidenceDir 'build.log'

$Build = Invoke-Captured `
    -FilePath $MvnwCmd `
    -Arguments @(
        '-B',
        '-ntp',
        'clean',
        'package',
        '-DskipTests'
    )

$Build.Lines |
    Set-Content `
        -LiteralPath $BuildLogPath `
        -Encoding UTF8

if ($Build.ExitCode -ne 0) {
    throw "Candidate JAR build failed. Log: $BuildLogPath"
}

if ($Build.Text -notmatch 'BUILD SUCCESS') {
    throw 'Candidate JAR build did not report BUILD SUCCESS.'
}

$JarName =
    "payflow-$ProjectVersion.jar"

$JarPath =
    Join-Path `
        (Join-Path $RepoRoot 'target') `
        $JarName

if (-not (Test-Path -LiteralPath $JarPath -PathType Leaf)) {
    throw "Expected artifact missing: target/$JarName"
}

$JarInfo =
    Get-Item -LiteralPath $JarPath

$JarSha256 =
    Get-FileSha256 -Path $JarPath

$ArtifactChecksumPath =
    Join-Path `
        $EvidenceDir `
        "$JarName.sha256"

Write-Utf8NoBom `
    -Path $ArtifactChecksumPath `
    -Text "$JarSha256  $JarName`n"

$ArtifactChecksumSha256 =
    Get-FileSha256 `
        -Path $ArtifactChecksumPath

$DependencyTreeRelative =
    ".runtime/security/supply-chain/$ExpectedHead/dependency-tree.txt"

$DependencyTree = Invoke-Captured `
    -FilePath $MvnwCmd `
    -Arguments @(
        '-q',
        'dependency:tree',
        '-DoutputType=text',
        '-DappendOutput=false',
        "-DoutputFile=$DependencyTreeRelative"
    )

if ($DependencyTree.ExitCode -ne 0) {
    throw 'Dependency tree generation failed.'
}

if (-not (
    Test-Path `
        -LiteralPath $DependencyTreePath `
        -PathType Leaf
)) {
    throw 'Dependency tree evidence was not produced.'
}

$DependencyTreeSha256 =
    Get-FileSha256 `
        -Path $DependencyTreePath

$GitTreeManifest = Invoke-Required `
    -FilePath 'git' `
    -Arguments @(
        'ls-tree',
        '-r',
        '--full-tree',
        'HEAD'
    ) `
    -FailureMessage 'Unable to create Git tree manifest.'

Write-Utf8NoBom `
    -Path $GitTreeManifestPath `
    -Text (
        ($GitTreeManifest.Lines -join "`n") +
        "`n"
    )

$GitTreeManifestSha256 =
    Get-FileSha256 `
        -Path $GitTreeManifestPath

$Docker =
    Get-Command `
        'docker' `
        -ErrorAction Stop

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
        $SyftImage
    ) `
    -FailureMessage 'Unable to pull pinned Syft image.' |
    Out-Null

$SyftVersionOutput = Invoke-Required `
    -FilePath $Docker.Source `
    -Arguments @(
        'run',
        '--rm',
        $SyftImage,
        'version'
    ) `
    -FailureMessage 'Syft version probe failed.'

if ($SyftVersionOutput.Text -notmatch [regex]::Escape($SyftVersion)) {
    throw "Pinned Syft image did not report version $SyftVersion."
}

$SbomName =
    "$JarName.cdx.json"

$SbomPath =
    Join-Path $EvidenceDir $SbomName

$ArtifactMount =
    "type=bind,source=$(Split-Path -Parent $JarPath),target=/input,readonly"

$EvidenceMount =
    "type=bind,source=$EvidenceDir,target=/evidence"

$Syft = Invoke-Captured `
    -FilePath $Docker.Source `
    -Arguments @(
        'run',
        '--rm',
        '--mount',
        $ArtifactMount,
        '--mount',
        $EvidenceMount,
        $SyftImage,
        'scan',
        "file:/input/$JarName",
        '-o',
        "cyclonedx-json=/evidence/$SbomName"
    )

if ($Syft.ExitCode -ne 0) {
    throw 'Syft CycloneDX generation failed.'
}

if (-not (Test-Path -LiteralPath $SbomPath -PathType Leaf)) {
    throw 'CycloneDX SBOM was not produced.'
}

$Sbom =
    Get-Content `
        -LiteralPath $SbomPath `
        -Raw `
        -Encoding UTF8 |
    ConvertFrom-Json

if ([string] $Sbom.bomFormat -ne 'CycloneDX') {
    throw 'SBOM format is not CycloneDX.'
}

$Components =
    @($Sbom.components)

if ($Components.Count -lt 10) {
    throw 'SBOM component inventory is unexpectedly sparse.'
}

$PurlCount =
    @(
        $Components |
            Where-Object {
                -not [string]::IsNullOrWhiteSpace(
                    [string] $_.purl
                )
            }
    ).Count

if ($PurlCount -lt 10) {
    throw 'SBOM package-URL inventory is unexpectedly sparse.'
}

$SyftTool =
    @(
        $Sbom.metadata.tools.components |
            Where-Object {
                [string] $_.name -eq 'syft'
            }
    )

if (
    $SyftTool.Count -ne 1 -or
    [string] $SyftTool[0].version -ne $SyftVersion
) {
    throw 'SBOM metadata does not identify the pinned Syft version.'
}

$SbomSha256 =
    Get-FileSha256 -Path $SbomPath

$BuildLogSha256 =
    Get-FileSha256 -Path $BuildLogPath

$Provenance = [ordered]@{
    schemaVersion = 1
    evidenceKind = 'payflow-local-build-and-sbom-evidence'
    generatedAtUtc = (
        [DateTime]::UtcNow.ToString(
            'yyyy-MM-ddTHH:mm:ss.fffZ'
        )
    )
    scope = [ordered]@{
        repository = 'nursena-pc/payflow'
        commitSha = $Head
        gitTreeSha = $TreeSha
        projectVersion = $ProjectVersion
        stabilizationCandidate = $true
        publishedRelease = $false
    }
    sourceInputs = [ordered]@{
        gitTreeManifestSha256 = $GitTreeManifestSha256
        pomSha256 = (
            Get-FileSha256 `
                -Path (
                    Join-Path $RepoRoot 'pom.xml'
                )
        )
        mavenWrapperPropertiesSha256 = (
            Get-FileSha256 -Path $WrapperPath
        )
        mvnwSha256 = (
            Get-FileSha256 `
                -Path (
                    Join-Path $RepoRoot 'mvnw'
                )
        )
        mvnwGitMode = '100755'
        dockerfileSha256 = (
            Get-FileSha256 -Path $DockerfilePath
        )
        dependencyTreeSha256 = $DependencyTreeSha256
    }
    toolchain = [ordered]@{
        javaVersion = $JavaVersion
        mavenVersion = $MavenVersion
        wrapperDistributionUrl = $WrapperDistributionUrl
        wrapperDistributionSha256 = $WrapperDistributionSha256
        dockerBuilder = $ExpectedBuilderLine
        dockerRuntime = $ExpectedRuntimeLine
    }
    localBuild = [ordered]@{
        command = '.\mvnw.cmd -B -ntp clean package -DskipTests'
        artifact = "target/$JarName"
        artifactSizeBytes = [long] $JarInfo.Length
        artifactSha256 = $JarSha256
        checksumEvidence = (
            ".runtime/security/supply-chain/$ExpectedHead/$JarName.sha256"
        )
        checksumEvidenceSha256 = $ArtifactChecksumSha256
        buildLogSha256 = $BuildLogSha256
        testsExecutedByThisBuild = $false
    }
    sbom = [ordered]@{
        tool = 'Syft'
        toolVersion = $SyftVersion
        toolImage = $SyftImage
        format = 'CycloneDX JSON'
        cyclonedxSpecVersion = [string] $Sbom.specVersion
        source = "file:/input/$JarName"
        command = (
            "syft scan file:/input/$JarName " +
            "-o cyclonedx-json=/evidence/$SbomName"
        )
        artifactSha256 = $JarSha256
        componentCount = [int] $Components.Count
        componentPurlCount = [int] $PurlCount
        evidence = (
            ".runtime/security/supply-chain/$ExpectedHead/$SbomName"
        )
        evidenceSha256 = $SbomSha256
    }
    evidenceBoundary = [ordered]@{
        generatedLocally = $true
        githubHostedWorkflowEvidence = $false
        slsaClaim = $false
        reproducibleBuildClaim = $false
        signingClaim = $false
        provenanceAttestationClaim = $false
        productionCertificationClaim = $false
        releasePublicationClaim = $false
        containsCredentialsTokensPrivateKeysOrPersonalData = $false
    }
}

Write-Utf8NoBom `
    -Path $ProvenancePath `
    -Text (
        (
            $Provenance |
                ConvertTo-Json -Depth 10
        ) + "`n"
    )

$ProvenanceSha256 =
    Get-FileSha256 -Path $ProvenancePath

$FinalHead = (
    Invoke-Required `
        -FilePath 'git' `
        -Arguments @(
            'rev-parse',
            'HEAD'
        ) `
        -FailureMessage 'Unable to resolve final HEAD.'
).Text.Trim()

if ($FinalHead -ne $ExpectedHead) {
    throw 'HEAD changed during evidence generation.'
}

$FinalStatus = (
    Invoke-Required `
        -FilePath 'git' `
        -Arguments @(
            'status',
            '--porcelain=v1',
            '--untracked-files=all'
        ) `
        -FailureMessage 'Unable to inspect final working tree.'
).Text

if (-not [string]::IsNullOrWhiteSpace($FinalStatus)) {
    throw 'Tracked repository state changed during evidence generation.'
}

Write-Host '=============================================' `
    -ForegroundColor Green
Write-Host 'PAYFLOW SUPPLY-CHAIN EVIDENCE PASS' `
    -ForegroundColor Green
Write-Host '=============================================' `
    -ForegroundColor Green
Write-Host "HEAD                 : $Head"
Write-Host "Git tree             : $TreeSha"
Write-Host "Project version      : $ProjectVersion"
Write-Host "Java                 : $JavaVersion"
Write-Host "Maven Wrapper        : $MavenVersion"
Write-Host "Syft                 : $SyftVersion"
Write-Host "Syft image           : $SyftImage"
Write-Host "Artifact bytes       : $($JarInfo.Length)"
Write-Host "Artifact SHA         : $JarSha256"
Write-Host "Dependency tree SHA  : $DependencyTreeSha256"
Write-Host "Git tree manifest SHA: $GitTreeManifestSha256"
Write-Host "CycloneDX spec       : $($Sbom.specVersion)"
Write-Host "SBOM components      : $($Components.Count)"
Write-Host "SBOM purl components : $PurlCount"
Write-Host "SBOM SHA             : $SbomSha256"
Write-Host "Local provenance SHA : $ProvenanceSha256"
Write-Host "Evidence directory   : .runtime/security/supply-chain/$ExpectedHead"
Write-Host 'SLSA/signing/reproducible-build claims: NONE'
Write-Host 'Tracked repository mutation: NONE'
