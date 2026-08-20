# PayFlow local PostgreSQL backup/restore rehearsal.
# Generates runtime artifacts only under ignored .runtime/.
# Does not migrate the source database and never restores over the source.
param(
    [string] $ComposeProject = 'payflow',
    [string] $PostgresService = 'postgres',
    [string] $AppService = 'app',
    [string] $SourceDatabase = 'payflow',
    [string] $SourceUser = 'payflow',
    [string] $TargetImage = 'postgres:17-alpine',
    [switch] $KeepDump,
    [switch] $SkipPackage
)

$ErrorActionPreference = 'Stop'

$TargetDatabase = 'payflow_restore'
$TargetUser = 'payflow_restore'

if ($TargetImage -notmatch '^postgres:17(?:-|$)') {
    throw "This v0.16 rehearsal requires PostgreSQL 17 target tooling; got: $TargetImage"
}

if ($SourceDatabase -eq $TargetDatabase) {
    throw 'Source and isolated target database names must differ.'
}

function Invoke-Checked {
    param(
        [Parameter(Mandatory)][string] $FilePath,
        [Parameter(Mandatory)][string[]] $Arguments,
        [string] $FailureMessage = 'Command failed.'
    )

    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$FailureMessage Exit code: $LASTEXITCODE"
    }
}

function Invoke-Captured {
    param(
        [Parameter(Mandatory)][string] $FilePath,
        [Parameter(Mandatory)][string[]] $Arguments
    )

    $Output = @(& $FilePath @Arguments 2>&1)
    $Exit = $LASTEXITCODE

    return [pscustomobject]@{
        ExitCode = $Exit
        Text = (($Output | ForEach-Object { "$_" }) -join "`n").Trim()
    }
}

function Get-FreeTcpPort {
    $Listener = [System.Net.Sockets.TcpListener]::new(
        [System.Net.IPAddress]::Loopback,
        0
    )

    try {
        $Listener.Start()
        return ([System.Net.IPEndPoint] $Listener.LocalEndpoint).Port
    }
    finally {
        $Listener.Stop()
    }
}

function Wait-PostgresReady {
    param(
        [Parameter(Mandatory)][string] $ContainerId,
        [Parameter(Mandatory)][string] $User,
        [Parameter(Mandatory)][string] $Database,
        [int] $TimeoutSeconds = 60
    )

    $Deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)

    while ([DateTime]::UtcNow -lt $Deadline) {
        $Ready = Invoke-Captured `
            -FilePath 'docker' `
            -Arguments @(
                'exec',
                $ContainerId,
                'pg_isready',
                '-U',
                $User,
                '-d',
                $Database
            )

        if ($Ready.ExitCode -eq 0) {
            return
        }

        Start-Sleep -Seconds 1
    }

    throw "PostgreSQL container did not become ready within $TimeoutSeconds seconds."
}

function Invoke-PsqlScalar {
    param(
        [Parameter(Mandatory)][string] $ContainerId,
        [Parameter(Mandatory)][string] $User,
        [Parameter(Mandatory)][string] $Database,
        [Parameter(Mandatory)][string] $Sql
    )

    $Result = Invoke-Captured `
        -FilePath 'docker' `
        -Arguments @(
            'exec',
            $ContainerId,
            'psql',
            '-U',
            $User,
            '-d',
            $Database,
            '-X',
            '-A',
            '-t',
            '-v',
            'ON_ERROR_STOP=1',
            '-c',
            $Sql
        )

    if ($Result.ExitCode -ne 0) {
        throw "psql query failed: $($Result.Text)"
    }

    return $Result.Text.Trim()
}

function Get-DatabaseFingerprint {
    param(
        [Parameter(Mandatory)][string] $ContainerId,
        [Parameter(Mandatory)][string] $User,
        [Parameter(Mandatory)][string] $Database
    )

    $TableText = Invoke-PsqlScalar `
        -ContainerId $ContainerId `
        -User $User `
        -Database $Database `
        -Sql @'
select tablename
from pg_tables
where schemaname = 'public'
order by tablename;
'@

    $Tables = @(
        $TableText -split "`n" |
            ForEach-Object { $_.Trim() } |
            Where-Object { $_ }
    )

    $Counts = [ordered]@{}

    foreach ($Table in $Tables) {
        if ($Table -notmatch '^[a-z_][a-z0-9_]*$') {
            throw "Unsafe table identifier encountered: $Table"
        }

        $Count = Invoke-PsqlScalar `
            -ContainerId $ContainerId `
            -User $User `
            -Database $Database `
            -Sql "select count(*) from `"$Table`";"

        $Counts[$Table] = [long] $Count
    }

    $FlywaySuccessCount = Invoke-PsqlScalar `
        -ContainerId $ContainerId `
        -User $User `
        -Database $Database `
        -Sql @'
select count(*)
from flyway_schema_history
where success = true;
'@

    $FlywayLatest = Invoke-PsqlScalar `
        -ContainerId $ContainerId `
        -User $User `
        -Database $Database `
        -Sql @'
select coalesce(version, '')
from flyway_schema_history
where success = true
  and version is not null
order by installed_rank desc
limit 1;
'@

    $FlywayDigest = Invoke-PsqlScalar `
        -ContainerId $ContainerId `
        -User $User `
        -Database $Database `
        -Sql @'
select md5(
    string_agg(
        coalesce(installed_rank::text, '') || '|' ||
        coalesce(version, '') || '|' ||
        coalesce(description, '') || '|' ||
        coalesce(type, '') || '|' ||
        coalesce(script, '') || '|' ||
        coalesce(checksum::text, '') || '|' ||
        coalesce(success::text, ''),
        E'\n'
        order by installed_rank
    )
)
from flyway_schema_history;
'@

    return [pscustomobject]@{
        Tables = $Tables
        Counts = $Counts
        FlywaySuccessCount = [long] $FlywaySuccessCount
        FlywayLatest = $FlywayLatest
        FlywayDigest = $FlywayDigest
    }
}

function Assert-FingerprintEqual {
    param(
        [Parameter(Mandatory)] $Expected,
        [Parameter(Mandatory)] $Actual,
        [Parameter(Mandatory)][string] $Label
    )

    $ExpectedTables = @($Expected.Tables | Sort-Object)
    $ActualTables = @($Actual.Tables | Sort-Object)

    $TableDiff = @(
        Compare-Object `
            -ReferenceObject $ExpectedTables `
            -DifferenceObject $ActualTables
    )

    if ($TableDiff.Count -ne 0) {
        $TableDiff | Format-Table | Out-Host
        throw "$Label table set differs."
    }

    foreach ($Table in $ExpectedTables) {
        $ExpectedCount = [long] $Expected.Counts[$Table]
        $ActualCount = [long] $Actual.Counts[$Table]

        if ($ExpectedCount -ne $ActualCount) {
            throw "$Label row count mismatch for $Table`: expected $ExpectedCount, got $ActualCount."
        }
    }

    if ($Expected.FlywaySuccessCount -ne $Actual.FlywaySuccessCount) {
        throw "$Label Flyway successful-row count differs."
    }

    if ($Expected.FlywayLatest -ne $Actual.FlywayLatest) {
        throw "$Label Flyway latest version differs."
    }

    if ($Expected.FlywayDigest -ne $Actual.FlywayDigest) {
        throw "$Label Flyway metadata digest differs."
    }
}

function Get-JarPath {
    $Jar = @(
        Get-ChildItem `
            -LiteralPath 'target' `
            -Filter 'payflow-0.16.0-SNAPSHOT.jar' `
            -File `
            -ErrorAction SilentlyContinue
    )

    if ($Jar.Count -ne 1) {
        return $null
    }

    return $Jar[0].FullName
}

function Get-ExpectedFlywayVersion {
    $MigrationFiles = @(
        Get-ChildItem `
            -LiteralPath 'src\main\resources\db\migration' `
            -Filter 'V*__*.sql' `
            -File `
            -ErrorAction Stop
    )

    $Versions = @(
        foreach ($File in $MigrationFiles) {
            if ($File.Name -match '^V(\d+)__') {
                [int] $Matches[1]
            }
        }
    )

    if ($Versions.Count -eq 0) {
        throw 'No versioned Flyway migrations were found.'
    }

    $Latest = ($Versions | Measure-Object -Maximum).Maximum
    $Expected = @(1..$Latest)
    $Missing = @(
        $Expected |
            Where-Object { $Versions -notcontains $_ }
    )

    if ($Missing.Count -ne 0) {
        throw "Flyway migration sequence contains gaps: $($Missing -join ', ')."
    }

    return [string] $Latest
}

function Set-TemporaryEnvironment {
    param(
        [Parameter(Mandatory)][hashtable] $Values
    )

    $Previous = @{}

    foreach ($Key in $Values.Keys) {
        $Item = Get-Item "Env:$Key" -ErrorAction SilentlyContinue

        $Previous[$Key] = if ($null -eq $Item) {
            $null
        }
        else {
            $Item.Value
        }

        Set-Item "Env:$Key" -Value $Values[$Key]
    }

    return $Previous
}

function Restore-Environment {
    param(
        [Parameter(Mandatory)][hashtable] $Previous
    )

    foreach ($Key in $Previous.Keys) {
        if ($null -eq $Previous[$Key]) {
            Remove-Item "Env:$Key" -ErrorAction SilentlyContinue
        }
        else {
            Set-Item "Env:$Key" -Value $Previous[$Key]
        }
    }
}

Write-Host '=== 1. VERIFY REPRODUCIBLE REHEARSAL BASELINE ===' -ForegroundColor Cyan

$RepoRoot = (& git rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0) {
    throw 'Not inside a Git repository.'
}

Set-Location -LiteralPath $RepoRoot

$Branch = (& git branch --show-current).Trim()
$Head = (& git rev-parse HEAD).Trim()
$ExpectedFlywayVersion = Get-ExpectedFlywayVersion

Write-Host "Branch          : $Branch"
Write-Host "HEAD            : $Head"
Write-Host "Expected Flyway : V$ExpectedFlywayVersion"

if ([string]::IsNullOrWhiteSpace($Branch)) {
    throw 'Detached HEAD is not accepted for a reproducible rehearsal.'
}

if (@(& git status --porcelain=v1).Count -ne 0) {
    & git status --short
    throw 'Working tree must be clean.'
}

Write-Host 'Clean repository baseline PASS.' -ForegroundColor Green

Write-Host ''
Write-Host '=== 2. VERIFY DOCKER + SOURCE POSTGRES ===' -ForegroundColor Cyan

$DockerVersion = Invoke-Captured `
    -FilePath 'docker' `
    -Arguments @('version', '--format', '{{.Server.Version}}')

if ($DockerVersion.ExitCode -ne 0) {
    throw "Docker daemon is unavailable: $($DockerVersion.Text)"
}

$SourceContainer = Invoke-Captured `
    -FilePath 'docker' `
    -Arguments @(
        'ps',
        '--filter',
        "label=com.docker.compose.project=$ComposeProject",
        '--filter',
        "label=com.docker.compose.service=$PostgresService",
        '--format',
        '{{.ID}}'
    )

if ($SourceContainer.ExitCode -ne 0) {
    throw "Could not resolve PayFlow PostgreSQL container: $($SourceContainer.Text)"
}

$SourceRows = @(
    $SourceContainer.Text -split "`n" |
        ForEach-Object { $_.Trim() } |
        Where-Object { $_ }
)

if ($SourceRows.Count -ne 1) {
    throw "Expected exactly one running source PostgreSQL container, found $($SourceRows.Count)."
}

$SourceContainerId = $SourceRows[0]

$SourceRunning = Invoke-Captured `
    -FilePath 'docker' `
    -Arguments @(
        'inspect',
        '--format',
        '{{.State.Running}}|{{.Config.Image}}|{{.Name}}',
        $SourceContainerId
    )

if ($SourceRunning.ExitCode -ne 0) {
    throw "Could not inspect source PostgreSQL container: $($SourceRunning.Text)"
}

$SourceParts = $SourceRunning.Text -split '\|'
$SourceIsRunning = $SourceParts[0].Trim()
$SourceImage = $SourceParts[1].Trim()
$SourceName = $SourceParts[2].TrimStart('/').Trim()

if ($SourceIsRunning -ne 'true') {
    throw 'Source PostgreSQL container is not running.'
}

if ($SourceImage -notmatch '^postgres:17(?:-|$)') {
    throw "Expected PostgreSQL 17 source tooling, got image: $SourceImage"
}

Wait-PostgresReady `
    -ContainerId $SourceContainerId `
    -User $SourceUser `
    -Database $SourceDatabase

$SourceServerVersion = Invoke-PsqlScalar `
    -ContainerId $SourceContainerId `
    -User $SourceUser `
    -Database $SourceDatabase `
    -Sql "select current_setting('server_version');"

$SourceCurrentDatabase = Invoke-PsqlScalar `
    -ContainerId $SourceContainerId `
    -User $SourceUser `
    -Database $SourceDatabase `
    -Sql 'select current_database();'

if ($SourceCurrentDatabase -ne $SourceDatabase) {
    throw "Resolved source database is unexpected: $SourceCurrentDatabase"
}

Write-Host "Source container : $SourceName"
Write-Host "Source image     : $SourceImage"
Write-Host "PostgreSQL       : $SourceServerVersion"

$SourceApp = Invoke-Captured `
    -FilePath 'docker' `
    -Arguments @(
        'ps',
        '--filter',
        "label=com.docker.compose.project=$ComposeProject",
        '--filter',
        "label=com.docker.compose.service=$AppService",
        '--format',
        '{{.ID}}|{{.Names}}'
    )

if ($SourceApp.ExitCode -ne 0) {
    throw "Could not inspect source app container: $($SourceApp.Text)"
}

$SourceAppRows = @(
    $SourceApp.Text -split "`n" |
        ForEach-Object { $_.Trim() } |
        Where-Object { $_ }
)

if ($SourceAppRows.Count -gt 0) {
    throw @"
The source PayFlow app is running and may write to PostgreSQL:
  $($SourceAppRows -join ', ')
Stop the source app before starting the rehearsal. The script never stops
or restarts source application containers implicitly.
"@
}

Write-Host 'Source app is stopped; backup window is quiescent.' `
    -ForegroundColor Green

Write-Host ''
Write-Host '=== 3. CAPTURE SOURCE FINGERPRINT ===' -ForegroundColor Cyan

$SourceBefore = Get-DatabaseFingerprint `
    -ContainerId $SourceContainerId `
    -User $SourceUser `
    -Database $SourceDatabase

if ($SourceBefore.FlywayLatest -ne $ExpectedFlywayVersion) {
    throw "Source database is not at current Flyway V$ExpectedFlywayVersion; got V$($SourceBefore.FlywayLatest). Upgrade rehearsal belongs to a separate checkpoint."
}

$RepresentativeTables = @(
    'users',
    'refresh_token_families',
    'refresh_token_records',
    'wallets',
    'payment_transactions',
    'ledger_entries',
    'outbox_events',
    'kafka_dead_letter_records',
    'kafka_dead_letter_command_audits'
)

foreach ($Table in $RepresentativeTables) {
    if ($SourceBefore.Tables -notcontains $Table) {
        throw "Representative persistence table is missing from source: $Table"
    }
}

Write-Host "Source public tables : $($SourceBefore.Tables.Count)"
Write-Host "Flyway success rows  : $($SourceBefore.FlywaySuccessCount)"
Write-Host "Flyway latest        : V$($SourceBefore.FlywayLatest)"

Write-Host ''
Write-Host '=== 4. CREATE CUSTOM-FORMAT BACKUP ===' -ForegroundColor Cyan

$Timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$RuntimeRoot = Join-Path `
    $RepoRoot `
    ".runtime\postgres-rehearsal\$Timestamp"

New-Item -ItemType Directory -Path $RuntimeRoot -Force | Out-Null

$DumpName = "payflow-$Timestamp.dump"
$HostDumpPath = Join-Path $RuntimeRoot $DumpName
$ContainerDumpPath = "/tmp/$DumpName"

$TargetContainerName = "payflow-pg-restore-$Timestamp"
$TargetContainerId = $null
$TargetPassword = [Guid]::NewGuid().ToString('N')
$AppProcess = $null
$AppStdout = Join-Path $RuntimeRoot 'app.stdout.log'
$AppStderr = Join-Path $RuntimeRoot 'app.stderr.log'
$ReportPath = Join-Path `
    $RuntimeRoot `
    'evidence.txt'

try {
    Invoke-Checked `
        -FilePath 'docker' `
        -Arguments @(
            'exec',
            $SourceContainerId,
            'pg_dump',
            '-U',
            $SourceUser,
            '-d',
            $SourceDatabase,
            '--format=custom',
            '--no-owner',
            '--no-privileges',
            "--file=$ContainerDumpPath"
        ) `
        -FailureMessage 'pg_dump failed.'

    Invoke-Checked `
        -FilePath 'docker' `
        -Arguments @(
            'cp',
            "$SourceContainerId`:$ContainerDumpPath",
            $HostDumpPath
        ) `
        -FailureMessage 'docker cp of backup failed.'

    Invoke-Checked `
        -FilePath 'docker' `
        -Arguments @(
            'exec',
            $SourceContainerId,
            'rm',
            '-f',
            $ContainerDumpPath
        ) `
        -FailureMessage 'Could not remove temporary dump from source container.'

    if (-not (Test-Path -LiteralPath $HostDumpPath -PathType Leaf)) {
        throw 'Host backup file was not created.'
    }

    $DumpLength = (Get-Item -LiteralPath $HostDumpPath).Length

    if ($DumpLength -le 0) {
        throw 'Backup file is empty.'
    }

    $DumpHash = (
        Get-FileHash `
            -LiteralPath $HostDumpPath `
            -Algorithm SHA256
    ).Hash.ToLowerInvariant()

    Write-Host "Backup bytes  : $DumpLength"
    Write-Host "Backup SHA256 : $DumpHash"

    Write-Host ''
    Write-Host '=== 5. VERIFY SOURCE DID NOT DRIFT DURING BACKUP ===' -ForegroundColor Cyan

    $SourceAfter = Get-DatabaseFingerprint `
        -ContainerId $SourceContainerId `
        -User $SourceUser `
        -Database $SourceDatabase

    Assert-FingerprintEqual `
        -Expected $SourceBefore `
        -Actual $SourceAfter `
        -Label 'Source pre/post-backup'

    Write-Host 'Source pre/post-backup fingerprint PASS.' -ForegroundColor Green

    Write-Host ''
    Write-Host '=== 6. START CLEAN ISOLATED POSTGRES 17 TARGET ===' -ForegroundColor Cyan

    $TargetPort = Get-FreeTcpPort

    $RunTarget = Invoke-Captured `
        -FilePath 'docker' `
        -Arguments @(
            'run',
            '--detach',
            '--rm',
            '--name',
            $TargetContainerName,
            '-e',
            "POSTGRES_DB=$TargetDatabase",
            '-e',
            "POSTGRES_USER=$TargetUser",
            '-e',
            "POSTGRES_PASSWORD=$TargetPassword",
            '-p',
            "127.0.0.1:$TargetPort`:5432",
            $TargetImage
        )

    if ($RunTarget.ExitCode -ne 0) {
        throw "Could not start isolated restore target: $($RunTarget.Text)"
    }

    $TargetContainerId = $RunTarget.Text.Trim()

    if ($TargetContainerId -eq $SourceContainerId) {
        throw 'Safety violation: target container resolved to source container.'
    }

    Wait-PostgresReady `
        -ContainerId $TargetContainerId `
        -User $TargetUser `
        -Database $TargetDatabase

    Write-Host "Target container: $TargetContainerName"
    Write-Host "Target image    : $TargetImage"
    Write-Host "Target database : $TargetDatabase"
    Write-Host "Target port     : $TargetPort"

    Write-Host ''
    Write-Host '=== 7. RESTORE BACKUP INTO CLEAN TARGET ===' -ForegroundColor Cyan

    $TargetDumpPath = "/tmp/$DumpName"

    Invoke-Checked `
        -FilePath 'docker' `
        -Arguments @(
            'cp',
            $HostDumpPath,
            "$TargetContainerId`:$TargetDumpPath"
        ) `
        -FailureMessage 'Could not copy backup into target container.'

    Invoke-Checked `
        -FilePath 'docker' `
        -Arguments @(
            'exec',
            $TargetContainerId,
            'pg_restore',
            '-U',
            $TargetUser,
            '-d',
            $TargetDatabase,
            '--exit-on-error',
            '--no-owner',
            '--no-privileges',
            $TargetDumpPath
        ) `
        -FailureMessage 'pg_restore failed.'

    Invoke-Checked `
        -FilePath 'docker' `
        -Arguments @(
            'exec',
            $TargetContainerId,
            'rm',
            '-f',
            $TargetDumpPath
        ) `
        -FailureMessage 'Could not remove temporary dump from target container.'

    Write-Host 'Restore completed without pg_restore errors.' -ForegroundColor Green

    Write-Host ''
    Write-Host '=== 8. COMPARE RESTORED PERSISTENCE ===' -ForegroundColor Cyan

    $TargetFingerprint = Get-DatabaseFingerprint `
        -ContainerId $TargetContainerId `
        -User $TargetUser `
        -Database $TargetDatabase

    Assert-FingerprintEqual `
        -Expected $SourceAfter `
        -Actual $TargetFingerprint `
        -Label 'Restored database'

    foreach ($Table in $RepresentativeTables) {
        Write-Host (
            '{0,-40} source={1,-8} restored={2,-8}' -f
                $Table,
                $SourceAfter.Counts[$Table],
                $TargetFingerprint.Counts[$Table]
        )
    }

    Write-Host 'Full public-table row-count + Flyway comparison PASS.' `
        -ForegroundColor Green

    Write-Host ''
    Write-Host '=== 9. BUILD CURRENT APPLICATION ARTIFACT ===' -ForegroundColor Cyan

    if (-not $SkipPackage) {
        $MavenWrapper = if (Test-Path -LiteralPath 'mvnw.cmd') {
            '.\mvnw.cmd'
        }
        elseif (Test-Path -LiteralPath 'mvnw') {
            '.\mvnw'
        }
        else {
            throw 'Maven wrapper was not found.'
        }

        & $MavenWrapper -B -ntp -DskipTests package

        if ($LASTEXITCODE -ne 0) {
            throw 'Maven package for restored-database startup verification failed.'
        }
    }

    $JarPath = Get-JarPath

    if ($null -eq $JarPath) {
        throw 'Expected payflow-0.16.0-SNAPSHOT.jar was not found. Run without -SkipPackage first.'
    }

    Write-Host "Application JAR: $JarPath"

    Write-Host ''
    Write-Host '=== 10. START PAYFLOW AGAINST RESTORED DATABASE ===' -ForegroundColor Cyan

    $AppPort = Get-FreeTcpPort

    $EnvironmentValues = @{
        DB_URL = "jdbc:postgresql://127.0.0.1:$TargetPort/$TargetDatabase"
        DB_USERNAME = $TargetUser
        DB_PASSWORD = $TargetPassword
        SERVER_PORT = "$AppPort"
        REDIS_HOST = '127.0.0.1'
        REDIS_PORT = '6379'
        KAFKA_BOOTSTRAP_SERVERS = '127.0.0.1:9092'
        MAIL_OUTBOX_POLLING_ENABLED = 'false'
        OUTBOX_POLLING_ENABLED = 'false'
        TRANSFER_COMPLETED_CONSUMER_ENABLED = 'false'
        TRANSFER_COMPLETED_DLT_INTAKE_ENABLED = 'false'
        ABUSE_PROTECTION_ENABLED = 'false'
        JWT_KEY_PROVIDER_MODE = 'ephemeral'
        MAIL_CONTENT_PROTECTION_MODE = 'ephemeral'
        MFA_SECRET_PROTECTION_MODE = 'ephemeral'
    }

    $PreviousEnvironment = Set-TemporaryEnvironment `
        -Values $EnvironmentValues

    try {
        $AppProcess = Start-Process `
            -FilePath 'java' `
            -ArgumentList @('-jar', $JarPath) `
            -PassThru `
            -RedirectStandardOutput $AppStdout `
            -RedirectStandardError $AppStderr

        $Deadline = [DateTime]::UtcNow.AddSeconds(120)
        $HealthUri = "http://127.0.0.1:$AppPort/api/v1/system/health"
        $HealthStatus = $null

        while ([DateTime]::UtcNow -lt $Deadline) {
            $AppProcess.Refresh()

            if ($AppProcess.HasExited) {
                $StdoutTail = if (Test-Path $AppStdout) {
                    (Get-Content $AppStdout -Tail 40) -join "`n"
                }
                else {
                    ''
                }

                $StderrTail = if (Test-Path $AppStderr) {
                    (Get-Content $AppStderr -Tail 40) -join "`n"
                }
                else {
                    ''
                }

                throw @"
PayFlow exited before restored-database startup verification completed.
STDOUT tail:
$StdoutTail

STDERR tail:
$StderrTail
"@
            }

            try {
                $Response = Invoke-WebRequest `
                    -Uri $HealthUri `
                    -Method Get `
                    -UseBasicParsing `
                    -TimeoutSec 2

                $HealthStatus = [int] $Response.StatusCode

                if ($HealthStatus -eq 200) {
                    break
                }
            }
            catch {
                # Startup is still in progress.
            }

            Start-Sleep -Seconds 2
        }

        if ($HealthStatus -ne 200) {
            throw 'PayFlow did not return HTTP 200 from /api/v1/system/health within 120 seconds.'
        }

        Write-Host "Restored DB startup health: HTTP $HealthStatus" `
            -ForegroundColor Green
    }
    finally {
        Restore-Environment -Previous $PreviousEnvironment
    }

    Write-Host ''
    Write-Host '=== 11. WRITE SANITIZED REHEARSAL EVIDENCE ===' -ForegroundColor Cyan

    $Evidence = New-Object System.Collections.Generic.List[string]

    [void] $Evidence.Add('PayFlow PostgreSQL Backup/Restore Rehearsal Evidence')
    [void] $Evidence.Add("Branch: $Branch")
    [void] $Evidence.Add("Baseline HEAD: $Head")
    [void] $Evidence.Add("Source image: $SourceImage")
    [void] $Evidence.Add("Source PostgreSQL version: $SourceServerVersion")
    [void] $Evidence.Add("Target image: $TargetImage")
    [void] $Evidence.Add("Backup bytes: $DumpLength")
    [void] $Evidence.Add("Backup SHA-256: $DumpHash")
    [void] $Evidence.Add("Public table count: $($SourceAfter.Tables.Count)")
    [void] $Evidence.Add("Flyway successful rows: $($SourceAfter.FlywaySuccessCount)")
    [void] $Evidence.Add("Flyway latest: V$($SourceAfter.FlywayLatest)")
    [void] $Evidence.Add("Flyway metadata digest: $($SourceAfter.FlywayDigest)")
    [void] $Evidence.Add('Source stable across backup: PASS')
    [void] $Evidence.Add('Clean isolated restore: PASS')
    [void] $Evidence.Add('All public table row counts preserved: PASS')
    [void] $Evidence.Add('Flyway metadata preserved: PASS')
    [void] $Evidence.Add('PayFlow startup against restored database: PASS')
    [void] $Evidence.Add('System health against restored database: HTTP 200')
    [void] $Evidence.Add('')
    [void] $Evidence.Add('Representative persistence row counts:')

    foreach ($Table in $RepresentativeTables) {
        [void] $Evidence.Add(
            "  $Table=$($SourceAfter.Counts[$Table])"
        )
    }

    [void] $Evidence.Add('')
    [void] $Evidence.Add('No row values, credentials, tokens, MFA secrets, protected mail content, or passwords are included in this evidence.')

    [System.IO.File]::WriteAllText(
        $ReportPath,
        (($Evidence -join "`r`n") + "`r`n"),
        [System.Text.UTF8Encoding]::new($true)
    )

    $EvidenceHash = (
        Get-FileHash `
            -LiteralPath $ReportPath `
            -Algorithm SHA256
    ).Hash.ToLowerInvariant()

    Write-Host "Evidence: $ReportPath"
    Write-Host "SHA256 : $EvidenceHash"

    if (@(& git status --porcelain=v1).Count -ne 0) {
        & git status --short
        throw 'Repository changed during rehearsal.'
    }

    Write-Host ''
    Write-Host '=============================================' -ForegroundColor Green
    Write-Host 'POSTGRES BACKUP/RESTORE REHEARSAL PASS' -ForegroundColor Green
    Write-Host '=============================================' -ForegroundColor Green
    Write-Host "Branch       : $Branch"
    Write-Host "HEAD         : $Head"
    Write-Host "PostgreSQL   : $SourceServerVersion"
    Write-Host "Flyway latest: V$($SourceAfter.FlywayLatest)"
    Write-Host "Public tables: $($SourceAfter.Tables.Count)"
    Write-Host 'Backup       : PASS'
    Write-Host 'Restore      : PASS'
    Write-Host 'Row counts   : PASS'
    Write-Host 'Flyway       : PASS'
    Write-Host 'App startup  : PASS'
    Write-Host 'Health       : HTTP 200'
    Write-Host 'Repo tree    : CLEAN'
    Write-Host "Evidence     : $ReportPath"
    Write-Host "SHA256       : $EvidenceHash"
}
finally {
    if ($null -ne $AppProcess) {
        try {
            $AppProcess.Refresh()

            if (-not $AppProcess.HasExited) {
                Stop-Process -Id $AppProcess.Id -Force
                [void] $AppProcess.WaitForExit(10000)
            }
        }
        catch {
            Write-Warning "Could not stop temporary PayFlow process cleanly: $($_.Exception.Message)"
        }
    }

    if ($null -ne $TargetContainerId) {
        $CleanupTarget = Invoke-Captured `
            -FilePath 'docker' `
            -Arguments @('rm', '-f', $TargetContainerId)

        if ($CleanupTarget.ExitCode -ne 0) {
            Write-Warning "Could not remove isolated target container: $($CleanupTarget.Text)"
        }
    }

    if (-not $KeepDump -and (Test-Path -LiteralPath $HostDumpPath)) {
        Remove-Item -LiteralPath $HostDumpPath -Force
    }

    if (@(& git status --porcelain=v1).Count -ne 0) {
        Write-Warning 'Repository became dirty during rehearsal; inspect git status.'
        & git status --short
    }
}
