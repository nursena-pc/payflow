# PayFlow v1.0.0 release-candidate Flyway clean-install / historical upgrade rehearsal.
# Uses fresh PostgreSQL 17 targets, immutable v0.13.0/V17 as the approved
# upgrade source, synthetic data only, never repairs Flyway history, and
# does not implement a down-migration.
$ErrorActionPreference = 'Stop'

$ExpectedV013Commit = '726f631a0de800870813ccb0c00b2676eb5d172b'
$PostgresImage = 'postgres:17-alpine'

$V17Tables = @(
    'users','wallets','payment_transactions','ledger_entries',
    'outbox_events','processed_kafka_events',
    'transfer_completed_event_audits','kafka_dead_letter_records',
    'kafka_dead_letter_command_audits','refresh_token_families',
    'refresh_token_records','account_action_credentials',
    'mail_outbox_messages'
)

$CurrentTables = @(
    $V17Tables +
    @(
        'mfa_authenticators','mfa_login_challenges','mfa_recovery_codes',
        'step_up_grants','account_security_audits','flyway_schema_history'
    )
)

function Run-Captured([string]$Exe,[string[]]$CommandArgs) {
    $old = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $out = @(& $Exe @CommandArgs 2>&1)
        $code = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $old
    }
    [pscustomobject]@{
        Code = $code
        Text = (($out | ForEach-Object { "$_" }) -join "`n").Trim()
    }
}

function Run-Checked([string]$Exe,[string[]]$CommandArgs,[string]$Message) {
    & $Exe @CommandArgs
    if ($LASTEXITCODE -ne 0) {
        throw "$Message Exit code: $LASTEXITCODE"
    }
}

function Git-Scalar([string[]]$CommandArgs) {
    $r = Run-Captured 'git' $CommandArgs
    if ($r.Code -ne 0) {
        throw "git $($CommandArgs -join ' ') failed: $($r.Text)"
    }
    $r.Text.Trim()
}

function Free-Port {
    $l = [System.Net.Sockets.TcpListener]::new(
        [System.Net.IPAddress]::Loopback, 0
    )
    try {
        $l.Start()
        ([System.Net.IPEndPoint]$l.LocalEndpoint).Port
    }
    finally {
        $l.Stop()
    }
}

function Wait-Pg([string]$Id,[string]$User,[string]$Db) {
    $deadline = [DateTime]::UtcNow.AddSeconds(60)
    while ([DateTime]::UtcNow -lt $deadline) {
        $r = Run-Captured 'docker' @(
            'exec',$Id,'pg_isready','-U',$User,'-d',$Db
        )
        if ($r.Code -eq 0) { return }
        Start-Sleep -Seconds 1
    }
    throw 'PostgreSQL readiness timeout.'
}

function Start-Pg(
    [string]$Name,[string]$Db,[string]$User,[string]$Password,[int]$Port
) {
    $r = Run-Captured 'docker' @(
        'run','--detach','--rm','--name',$Name,
        '-e',"POSTGRES_DB=$Db",
        '-e',"POSTGRES_USER=$User",
        '-e',"POSTGRES_PASSWORD=$Password",
        '-p',"127.0.0.1:$Port`:5432",
        $PostgresImage
    )
    if ($r.Code -ne 0) {
        throw "PostgreSQL container start failed: $($r.Text)"
    }
    $id = $r.Text.Trim()
    Wait-Pg $id $User $Db
    $id
}

function Psql(
    [string]$Id,[string]$User,[string]$Db,[string]$Sql
) {
    $r = Run-Captured 'docker' @(
        'exec',$Id,'psql','-U',$User,'-d',$Db,
        '-X','-A','-t','-v','ON_ERROR_STOP=1','-c',$Sql
    )
    if ($r.Code -ne 0) {
        throw "psql failed: $($r.Text)"
    }
    $r.Text.Trim()
}

function Psql-File(
    [string]$Id,[string]$User,[string]$Db,
    [string]$HostPath,[string]$ContainerPath
) {
    Run-Checked 'docker' @(
        'cp',$HostPath,"$Id`:$ContainerPath"
    ) 'docker cp failed.'
    try {
        Run-Checked 'docker' @(
            'exec',$Id,'psql','-U',$User,'-d',$Db,
            '-X','-v','ON_ERROR_STOP=1','-f',$ContainerPath
        ) 'psql file execution failed.'
    }
    finally {
        $null = Run-Captured 'docker' @(
            'exec',$Id,'rm','-f',$ContainerPath
        )
    }
}

function Expected-Migrations([string]$Ref) {
    $paths = @(
        git ls-tree -r --name-only $Ref -- 'src/main/resources/db/migration'
    )
    if ($LASTEXITCODE -ne 0) {
        throw "Could not list migrations at $Ref."
    }

    @(
        $paths |
        Where-Object { $_ -match '/V(\d+)__.+\.sql$' } |
        ForEach-Object {
            $_ -match '/V(\d+)__(.+)\.sql$' | Out-Null
            [pscustomobject]@{
                Version = [int]$Matches[1]
                Script = [IO.Path]::GetFileName($_)
            }
        } |
        Sort-Object Version
    )
}

function Flyway-Rows([string]$Id,[string]$User,[string]$Db) {
    $text = Psql $Id $User $Db @'
select installed_rank::text || '|' ||
       version || '|' ||
       script || '|' ||
       coalesce(checksum::text,'') || '|' ||
       success::text
from flyway_schema_history
where version is not null
order by installed_rank;
'@
    $rows = @()
    foreach ($line in ($text -split "`n")) {
        if (-not $line.Trim()) { continue }
        $p = $line.Split('|')
        if ($p.Count -ne 5) { throw "Bad Flyway row: $line" }
        $rows += [pscustomobject]@{
            Rank = [int]$p[0]
            Version = [int]$p[1]
            Script = $p[2]
            Checksum = $p[3]
            Success = $p[4]
        }
    }
    $rows
}

function Assert-Flyway(
    [string]$Id,[string]$User,[string]$Db,$Expected,[string]$Label
) {
    $actual = @(Flyway-Rows $Id $User $Db)
    if ($actual.Count -ne $Expected.Count) {
        throw "$Label Flyway count mismatch: expected $($Expected.Count), got $($actual.Count)."
    }
    for ($i = 0; $i -lt $Expected.Count; $i++) {
        if ($actual[$i].Version -ne $Expected[$i].Version) {
            throw "$Label Flyway version mismatch at index $i."
        }
        if ($actual[$i].Script -ne $Expected[$i].Script) {
            throw "$Label Flyway script mismatch at V$($Expected[$i].Version)."
        }
        if ($actual[$i].Success -ne 'true') {
            throw "$Label Flyway V$($actual[$i].Version) was not successful."
        }
    }
    $actual
}

function History-Prefix-Digest(
    [string]$Id,[string]$User,[string]$Db,[int]$Max
) {
    Psql $Id $User $Db @"
select md5(string_agg(
    installed_rank::text || '|' || version || '|' ||
    description || '|' || type || '|' || script || '|' ||
    coalesce(checksum::text,'') || '|' || success::text,
    E'\n' order by installed_rank))
from flyway_schema_history
where version is not null and version::integer <= $Max;
"@
}

function Table-Set([string]$Id,[string]$User,[string]$Db) {
    $t = Psql $Id $User $Db @'
select tablename
from pg_tables
where schemaname='public'
order by tablename;
'@
    @($t -split "`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ })
}

function Assert-Set([string[]]$Actual,[string[]]$Expected,[string]$Label) {
    $d = @(
        Compare-Object `
            -ReferenceObject @($Expected | Sort-Object -Unique) `
            -DifferenceObject @($Actual | Sort-Object -Unique)
    )
    if ($d.Count -ne 0) {
        $d | Format-Table | Out-Host
        throw "$Label mismatch."
    }
}

function Fingerprints(
    [string]$Id,[string]$User,[string]$Db,[string[]]$Tables
) {
    $map = @{}
    foreach ($table in $Tables) {
        if ($table -notmatch '^[a-z_][a-z0-9_]*$') {
            throw "Unsafe table: $table"
        }
        $v = Psql $Id $User $Db @"
select count(*)::text || '|' ||
       coalesce(md5(string_agg(to_jsonb(t)::text, E'\n'
           order by to_jsonb(t)::text)), md5(''))
from "$table" t;
"@
        $p = $v.Split('|')
        if ($p.Count -ne 2) { throw "Bad fingerprint for $table`: $v" }
        $map[$table] = [pscustomobject]@{
            Count = [long]$p[0]
            Digest = $p[1]
        }
    }
    $map
}

function Assert-Fingerprints($Before,$After,[string[]]$Tables) {
    foreach ($table in $Tables) {
        if ($Before[$table].Count -ne $After[$table].Count) {
            throw "Count changed for $table."
        }
        if ($Before[$table].Digest -ne $After[$table].Digest) {
            throw "Content fingerprint changed for $table."
        }
    }
}

function Set-Env([hashtable]$Values) {
    $old = @{}
    foreach ($k in $Values.Keys) {
        $item = Get-Item "Env:$k" -ErrorAction SilentlyContinue
        $old[$k] = if ($null -eq $item) { $null } else { $item.Value }
        Set-Item "Env:$k" -Value $Values[$k]
    }
    $old
}

function Restore-Env([hashtable]$Old) {
    foreach ($k in $Old.Keys) {
        if ($null -eq $Old[$k]) {
            Remove-Item "Env:$k" -ErrorAction SilentlyContinue
        }
        else {
            Set-Item "Env:$k" -Value $Old[$k]
        }
    }
}

function Resolve-Java21 {
    if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        throw 'JAVA_HOME must point to the Java 21 JDK used by PayFlow.'
    }

    $javaExecutable = Join-Path `
        $env:JAVA_HOME `
        'bin\java.exe'

    if (-not (
        Test-Path `
            -LiteralPath $javaExecutable `
            -PathType Leaf
    )) {
        throw "JAVA_HOME does not contain bin\java.exe: $env:JAVA_HOME"
    }

    $version = Run-Captured `
        $javaExecutable `
        @('-version')

    if ($version.Code -ne 0) {
        throw "JAVA_HOME Java version check failed: $($version.Text)"
    }

    $match = [regex]::Match(
        $version.Text,
        '(?im)^(?:openjdk|java) version "([0-9]+)(?:\.|")'
    )

    if (-not $match.Success) {
        throw "Could not parse JAVA_HOME Java version: $($version.Text)"
    }

    $major = [int] $match.Groups[1].Value

    if ($major -ne 21) {
        throw "Flyway rehearsal requires JAVA_HOME Java 21; got Java $major."
    }

    return $javaExecutable
}

function Run-App(
    [string]$Jar,[string]$Url,[string]$User,[string]$Password,
    [string]$LogPrefix,[string]$Label
) {
    $port = Free-Port

    $old = Set-Env @{
        DB_URL = $Url
        DB_USERNAME = $User
        DB_PASSWORD = $Password
        SERVER_PORT = "$port"
        LOGIN_RATE_LIMIT_ENABLED = 'false'
        ABUSE_PROTECTION_ENABLED = 'false'
        MAIL_OUTBOX_POLLING_ENABLED = 'false'
        OUTBOX_POLLING_ENABLED = 'false'
        TRANSFER_COMPLETED_CONSUMER_ENABLED = 'false'
        TRANSFER_COMPLETED_DLT_INTAKE_ENABLED = 'false'
        JWT_KEY_PROVIDER_MODE = 'ephemeral'
        MAIL_CONTENT_PROTECTION_MODE = 'ephemeral'
        MFA_SECRET_PROTECTION_MODE = 'ephemeral'
        MANAGEMENT_HEALTH_REDIS_ENABLED = 'false'
    }

    $javaExecutable = Resolve-Java21

    Write-Host "$Label runtime: JAVA_HOME Java 21"

    $stdoutPath = "$LogPrefix.stdout.log"
    $stderrPath = "$LogPrefix.stderr.log"

    $proc = $null
    $stdoutTask = $null
    $stderrTask = $null

    try {
        $startInfo = [System.Diagnostics.ProcessStartInfo]::new()

        $startInfo.FileName = $javaExecutable
        $startInfo.Arguments = "-jar `"$Jar`""
        $startInfo.UseShellExecute = $false
        $startInfo.CreateNoWindow = $true
        $startInfo.RedirectStandardOutput = $true
        $startInfo.RedirectStandardError = $true
        $startInfo.WorkingDirectory = (Get-Location).Path

        $proc = [System.Diagnostics.Process]::new()
        $proc.StartInfo = $startInfo

        if (-not $proc.Start()) {
            throw "$Label process start returned false."
        }

        $stdoutTask = $proc.StandardOutput.ReadToEndAsync()
        $stderrTask = $proc.StandardError.ReadToEndAsync()

        $deadline = [DateTime]::UtcNow.AddSeconds(150)
        $status = $null

        while ([DateTime]::UtcNow -lt $deadline) {
            $proc.Refresh()

            if ($proc.HasExited) {
                $proc.WaitForExit()

                [int] $exitCode = $proc.ExitCode

                $out = $stdoutTask.Result
                $err = $stderrTask.Result

                [IO.File]::WriteAllText(
                    $stdoutPath,
                    $out,
                    [Text.UTF8Encoding]::new($false)
                )

                [IO.File]::WriteAllText(
                    $stderrPath,
                    $err,
                    [Text.UTF8Encoding]::new($false)
                )

                $outTail = (
                    @(
                        $out -split '\r?\n'
                    ) |
                        Select-Object -Last 120
                ) -join "`n"

                $errTail = (
                    @(
                        $err -split '\r?\n'
                    ) |
                        Select-Object -Last 120
                ) -join "`n"

                throw @"
$Label exited early.
Exit code: $exitCode
STDOUT:
$outTail
STDERR:
$errTail
"@
            }

            try {
                $response = Invoke-WebRequest `
                    -Uri "http://127.0.0.1:$port/api/v1/system/health" `
                    -UseBasicParsing `
                    -TimeoutSec 2

                $status = [int] $response.StatusCode

                if ($status -eq 200) {
                    break
                }
            }
            catch {
                # Application startup is still in progress.
            }

            Start-Sleep -Seconds 2
        }

        if ($status -ne 200) {
            throw "$Label health did not reach HTTP 200."
        }

        Write-Host "$Label health: HTTP 200" `
            -ForegroundColor Green
    }
    finally {
        if ($null -ne $proc) {
            try {
                $proc.Refresh()

                if (-not $proc.HasExited) {
                    $proc.Kill()
                }

                $proc.WaitForExit()
            }
            catch {}

            try {
                if ($null -ne $stdoutTask) {
                    $out = $stdoutTask.Result

                    [IO.File]::WriteAllText(
                        $stdoutPath,
                        $out,
                        [Text.UTF8Encoding]::new($false)
                    )
                }

                if ($null -ne $stderrTask) {
                    $err = $stderrTask.Result

                    [IO.File]::WriteAllText(
                        $stderrPath,
                        $err,
                        [Text.UTF8Encoding]::new($false)
                    )
                }
            }
            catch {}
        }

        Restore-Env $old
    }
}
function Assert-Current-Schema(
    [string]$Id,[string]$User,[string]$Db,[string]$Label
) {
    Assert-Set (Table-Set $Id $User $Db) $CurrentTables "$Label table set"

    $refresh = Psql $Id $User $Db @'
select pg_get_constraintdef(oid)
from pg_constraint
where conname='chk_refresh_token_families_revocation_reason';
'@
    if (-not $refresh.Contains('PASSWORD_RECOVERY') -or
        -not $refresh.Contains('MFA_DISABLED')) {
        throw "$Label refresh-family constraint mismatch."
    }

    $audit = Psql $Id $User $Db @'
select pg_get_constraintdef(oid)
from pg_constraint
where conname='chk_account_security_audits_action';
'@
    if (-not $audit.Contains('MFA_DISABLED') -or
        -not $audit.Contains('RECOVERY_CODES_ROTATED')) {
        throw "$Label account-security constraint mismatch."
    }
}

Write-Host '=== 1. VERIFY CLEAN REHEARSAL BASELINE ===' -ForegroundColor Cyan

$RepoRoot = Git-Scalar @('rev-parse','--show-toplevel')
Set-Location $RepoRoot

$Branch = Git-Scalar @('branch','--show-current')
$Head = Git-Scalar @('rev-parse','HEAD')

Write-Host "Branch: $Branch"
Write-Host "HEAD  : $Head"

if ([string]::IsNullOrWhiteSpace($Branch)) {
    throw 'Detached HEAD is not accepted for a reproducible Flyway rehearsal.'
}

if (@(git status --porcelain=v1).Count -ne 0) {
    git status --short
    throw 'Working tree must be clean.'
}

$V013Commit = Git-Scalar @('rev-list','-n','1','v0.13.0')
if ($V013Commit -ne $ExpectedV013Commit) {
    throw "Unexpected immutable v0.13.0 commit: $V013Commit"
}

$docker = Run-Captured 'docker' @('version','--format','{{.Server.Version}}')
if ($docker.Code -ne 0) {
    throw "Docker unavailable: $($docker.Text)"
}

Write-Host 'Clean branch/tag baseline PASS.' -ForegroundColor Green

Write-Host ''
Write-Host '=== 2. VERIFY MIGRATION HISTORY BASELINES ===' -ForegroundColor Cyan

$CurrentExpected = @(Expected-Migrations 'HEAD')
$V013Expected = @(Expected-Migrations 'v0.13.0')

if ($CurrentExpected.Count -ne 24) { throw 'Current migration count is not 24.' }
if ($V013Expected.Count -ne 17) { throw 'v0.13.0 migration count is not 17.' }

for ($i=0; $i -lt 17; $i++) {
    if ($CurrentExpected[$i].Version -ne $V013Expected[$i].Version -or
        $CurrentExpected[$i].Script -ne $V013Expected[$i].Script) {
        throw "Historical migration name/version drift at index $i."
    }

    $a = Git-Scalar @(
        'rev-parse',
        "HEAD:src/main/resources/db/migration/$($CurrentExpected[$i].Script)"
    )
    $b = Git-Scalar @(
        'rev-parse',
        "v0.13.0:src/main/resources/db/migration/$($V013Expected[$i].Script)"
    )
    if ($a -ne $b) {
        throw "Historical blob drift: $($CurrentExpected[$i].Script)"
    }
}

$delta = @(
    $CurrentExpected |
        Where-Object { $_.Version -gt 17 } |
        ForEach-Object { "$($_.Version)" }
)
Assert-Set $delta @('18','19','20','21','22','23','24') 'V18..V24 delta'
Write-Host 'V1..V17 immutable; V18..V24 exact delta PASS.' -ForegroundColor Green

$Stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$RuntimeRoot = Join-Path `
    $RepoRoot `
    ".runtime\flyway-rehearsal\$Stamp"
$ExternalWorktreeRoot = Join-Path `
    (Split-Path -Parent $RepoRoot) `
    "payflow.runtime\flyway-worktree\$Stamp"
$Worktree = Join-Path $ExternalWorktreeRoot 'v0.13.0-source'
$SeedFile = Join-Path $RuntimeRoot 'v0.13.0-upgrade-seed.sql'
$ConstraintFile = Join-Path $RuntimeRoot 'current-constraint-probe.sql'
$Evidence = Join-Path $RuntimeRoot 'evidence.txt'

New-Item -ItemType Directory -Path $RuntimeRoot -Force | Out-Null
New-Item -ItemType Directory -Path $ExternalWorktreeRoot -Force | Out-Null

$CleanId = $null
$UpgradeId = $null
$WorktreeAdded = $false

try {
    Write-Host ''
    Write-Host '=== 3. BUILD CURRENT V1 CANDIDATE JAR ===' -ForegroundColor Cyan

    & .\mvnw.cmd -B -ntp -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw 'Current package build failed.' }

    [xml] $CurrentPom = Get-Content `
        -LiteralPath (Join-Path $RepoRoot 'pom.xml') `
        -Raw

    $CurrentArtifactId = [string] $CurrentPom.project.artifactId
    $CurrentVersion = [string] $CurrentPom.project.version

    if (
        [string]::IsNullOrWhiteSpace($CurrentArtifactId) -or
        [string]::IsNullOrWhiteSpace($CurrentVersion)
    ) {
        throw 'Could not resolve current Maven artifact coordinates.'
    }

    $CurrentJarName = "$CurrentArtifactId-$CurrentVersion.jar"

    $CurrentJar = Join-Path `
        $RepoRoot `
        "target\$CurrentJarName"

    if (-not (Test-Path -LiteralPath $CurrentJar -PathType Leaf)) {
        throw "Current JAR missing: $CurrentJarName"
    }

    Write-Host ''
    Write-Host '=== 4. CLEAN INSTALL: EMPTY POSTGRESQL 17 -> V24 ===' -ForegroundColor Cyan

    $CleanPort = Free-Port
    $CleanDb = 'payflow_clean'
    $CleanUser = 'payflow_clean'
    $CleanPass = [Guid]::NewGuid().ToString('N')
    $CleanId = Start-Pg `
        "payflow-flyway-clean-$Stamp" `
        $CleanDb $CleanUser $CleanPass $CleanPort

    Run-App `
        $CurrentJar `
        "jdbc:postgresql://127.0.0.1:$CleanPort/$CleanDb" `
        $CleanUser $CleanPass `
        (Join-Path $RuntimeRoot 'clean-current') `
        'Current clean-install app'

    $cleanHistory = @(
        Assert-Flyway $CleanId $CleanUser $CleanDb $CurrentExpected 'Clean install'
    )
    Assert-Current-Schema $CleanId $CleanUser $CleanDb 'Clean install'
    Write-Host "Clean install history: $($cleanHistory.Count) successful rows." `
        -ForegroundColor Green

    Write-Host ''
    Write-Host '=== 5. BUILD IMMUTABLE V0.13.0 JAR ===' -ForegroundColor Cyan

    Run-Checked 'git' @(
        'worktree','add','--detach',$Worktree,'v0.13.0'
    ) 'Temporary v0.13.0 worktree creation failed.'
    $WorktreeAdded = $true

    $wtHead = (Run-Captured 'git' @('-C',$Worktree,'rev-parse','HEAD'))
    if ($wtHead.Code -ne 0 -or $wtHead.Text.Trim() -ne $ExpectedV013Commit) {
        throw 'Temporary v0.13.0 worktree has unexpected HEAD.'
    }

    Push-Location $Worktree
    try {
        & .\mvnw.cmd -B -ntp -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw 'v0.13.0 package build failed.' }
    }
    finally {
        Pop-Location
    }

    $V013Jar = Join-Path $Worktree 'target\payflow-0.13.0.jar'
    if (-not (Test-Path $V013Jar)) { throw 'v0.13.0 JAR missing.' }

    Write-Host ''
    Write-Host '=== 6. IMMUTABLE HISTORICAL V0.13.0 BASELINE -> V17 ===' `
        -ForegroundColor Cyan

    $UpgradePort = Free-Port
    $UpgradeDb = 'payflow_upgrade'
    $UpgradeUser = 'payflow_upgrade'
    $UpgradePass = [Guid]::NewGuid().ToString('N')
    $UpgradeId = Start-Pg `
        "payflow-flyway-upgrade-$Stamp" `
        $UpgradeDb $UpgradeUser $UpgradePass $UpgradePort

    Run-App `
        $V013Jar `
        "jdbc:postgresql://127.0.0.1:$UpgradePort/$UpgradeDb" `
        $UpgradeUser $UpgradePass `
        (Join-Path $RuntimeRoot 'upgrade-v013') `
        'Immutable v0.13.0 baseline app'

    $null = Assert-Flyway `
        $UpgradeId $UpgradeUser $UpgradeDb $V013Expected 'v0.13.0 baseline'

    Assert-Set `
        (Table-Set $UpgradeId $UpgradeUser $UpgradeDb) `
        @($V17Tables + 'flyway_schema_history') `
        'v0.13.0 table set'

    $HistoryBefore = History-Prefix-Digest `
        $UpgradeId $UpgradeUser $UpgradeDb 17

    Write-Host 'Immutable v0.13.0 V17 baseline PASS.' -ForegroundColor Green

    Write-Host ''
    Write-Host '=== 7. SEED ALL 13 V17 DATA TABLES ===' -ForegroundColor Cyan

    $seed = @'
BEGIN;

INSERT INTO users
(id,email,password_hash,role,status,created_at,updated_at,email_verified_at)
VALUES
('00000000-0000-0000-0000-000000000101','upgrade-user-1@example.invalid','synthetic-hash-not-a-credential','USER','ACTIVE','2026-01-01T00:00:00Z','2026-01-01T00:00:00Z','2026-01-01T00:00:00Z'),
('00000000-0000-0000-0000-000000000102','upgrade-user-2@example.invalid','synthetic-hash-not-a-credential','USER','ACTIVE','2026-01-01T00:00:00Z','2026-01-01T00:00:00Z','2026-01-01T00:00:00Z');

INSERT INTO wallets
(id,owner_id,balance,currency,status,version,created_at,updated_at)
VALUES
('00000000-0000-0000-0000-000000000201','00000000-0000-0000-0000-000000000101',900.00,'USD','ACTIVE',1,'2026-01-01T00:00:00Z','2026-01-01T00:10:00Z'),
('00000000-0000-0000-0000-000000000202','00000000-0000-0000-0000-000000000102',1100.00,'USD','ACTIVE',1,'2026-01-01T00:00:00Z','2026-01-01T00:10:00Z');

INSERT INTO payment_transactions
(id,source_wallet_id,target_wallet_id,transaction_type,status,amount,currency,idempotency_key,failure_reason,created_at,completed_at)
VALUES
('00000000-0000-0000-0000-000000000301','00000000-0000-0000-0000-000000000201','00000000-0000-0000-0000-000000000202','TRANSFER','COMPLETED',100.00,'USD','v013-upgrade-rehearsal',NULL,'2026-01-01T00:05:00Z','2026-01-01T00:05:01Z');

INSERT INTO ledger_entries
(id,transaction_id,wallet_id,entry_type,amount,currency,created_at)
VALUES
('00000000-0000-0000-0000-000000000401','00000000-0000-0000-0000-000000000301','00000000-0000-0000-0000-000000000201','DEBIT',100.00,'USD','2026-01-01T00:05:01Z'),
('00000000-0000-0000-0000-000000000402','00000000-0000-0000-0000-000000000301','00000000-0000-0000-0000-000000000202','CREDIT',100.00,'USD','2026-01-01T00:05:01Z');

INSERT INTO outbox_events
(id,aggregate_type,aggregate_id,event_type,event_version,topic,partition_key,deduplication_key,payload,status,attempt_count,available_at,locked_at,locked_until,locked_by,created_at,published_at,last_error)
VALUES
('00000000-0000-0000-0000-000000000501','PAYMENT_TRANSACTION','00000000-0000-0000-0000-000000000301','wallet.transfer.completed',1,'wallet.transfer.completed','00000000-0000-0000-0000-000000000301','v013-upgrade-rehearsal-outbox','{"synthetic":true,"source":"v0.13.0"}'::jsonb,'PENDING',0,'2026-01-01T00:05:01Z',NULL,NULL,NULL,'2026-01-01T00:05:01Z',NULL,NULL);

INSERT INTO processed_kafka_events
(consumer_name,event_id,event_type,event_version,topic,partition_number,record_offset,processed_at)
VALUES
('v013-upgrade-rehearsal-consumer','00000000-0000-0000-0000-000000000601','wallet.transfer.completed',1,'wallet.transfer.completed',0,42,'2026-01-01T00:06:00Z');

INSERT INTO transfer_completed_event_audits
(event_id,event_type,event_version,occurred_at,transaction_id,source_wallet_id,target_wallet_id,amount,currency,recorded_at)
VALUES
('00000000-0000-0000-0000-000000000601','wallet.transfer.completed',1,'2026-01-01T00:05:01Z','00000000-0000-0000-0000-000000000301','00000000-0000-0000-0000-000000000201','00000000-0000-0000-0000-000000000202',100.00,'USD','2026-01-01T00:06:00Z');

INSERT INTO kafka_dead_letter_records
(id,dlt_topic,dlt_partition,dlt_offset,original_topic,original_partition,original_offset,original_consumer_group,record_key,payload,exception_type,exception_message,status,replay_count,received_at,last_replayed_at,replay_lease_owner,replay_lease_until,last_replay_error,replay_origin_id,replay_attempt_base)
VALUES
('00000000-0000-0000-0000-000000000701','wallet.transfer.completed.dlt',0,7,'wallet.transfer.completed',0,6,'v013-upgrade-rehearsal-group',NULL,NULL,'SyntheticRehearsalException',NULL,'RECEIVED',0,'2026-01-01T00:07:00Z',NULL,NULL,NULL,NULL,'00000000-0000-0000-0000-000000000701',0);

INSERT INTO kafka_dead_letter_command_audits
(id,command_id,stage,operator_id,dead_letter_record_id,command_type,outcome,error_code,occurred_at)
VALUES
('00000000-0000-0000-0000-000000000801','00000000-0000-0000-0000-000000000802','ATTEMPTED','00000000-0000-0000-0000-000000000101','00000000-0000-0000-0000-000000000701','REPLAY',NULL,NULL,'2026-01-01T00:08:00Z');

INSERT INTO refresh_token_families
(id,user_id,created_at,expires_at,revoked_at,revocation_reason)
VALUES
('00000000-0000-0000-0000-000000000901','00000000-0000-0000-0000-000000000101','2026-01-01T00:00:00Z','2026-01-31T00:00:00Z','2026-01-02T00:00:00Z','PASSWORD_RECOVERY');

INSERT INTO refresh_token_records
(id,family_id,token_digest,issued_at,expires_at,consumed_at,successor_id)
VALUES
('00000000-0000-0000-0000-000000000902','00000000-0000-0000-0000-000000000901',decode(repeat('11',32),'hex'),'2026-01-01T00:00:00Z','2026-01-07T00:00:00Z',NULL,NULL);

INSERT INTO account_action_credentials
(id,user_id,purpose,credential_digest,issued_at,expires_at,consumed_at,superseded_at)
VALUES
('00000000-0000-0000-0000-000000001001','00000000-0000-0000-0000-000000000101','PASSWORD_RECOVERY',decode(repeat('22',32),'hex'),'2026-01-01T01:00:00Z','2026-01-01T02:00:00Z','2026-01-01T01:30:00Z',NULL);

INSERT INTO mail_outbox_messages
(id,user_id,purpose,recipient,subject,protected_body,message_id,status,attempt_count,available_at,expires_at,locked_at,locked_until,locked_by,created_at,sent_at,last_error)
VALUES
('00000000-0000-0000-0000-000000001101','00000000-0000-0000-0000-000000000101','PASSWORD_RECOVERY','upgrade-mail@example.invalid','Synthetic Flyway upgrade rehearsal',NULL,'<v013-upgrade-rehearsal@payflow.invalid>','SENT',1,'2026-01-01T01:00:00Z','2026-01-01T02:00:00Z',NULL,NULL,NULL,'2026-01-01T01:00:00Z','2026-01-01T01:05:00Z',NULL);

COMMIT;
'@

    [IO.File]::WriteAllText(
        $SeedFile,$seed + "`r`n",[Text.UTF8Encoding]::new($true)
    )
    Psql-File `
        $UpgradeId $UpgradeUser $UpgradeDb `
        $SeedFile '/tmp/v013-seed.sql'

    $Before = Fingerprints `
        $UpgradeId $UpgradeUser $UpgradeDb $V17Tables

    foreach ($table in $V17Tables) {
        if ($Before[$table].Count -le 0) {
            throw "Seed left $table empty."
        }
    }

    Write-Host '13 / 13 V17 data tables seeded.' -ForegroundColor Green

    Write-Host ''
    Write-Host '=== 8. UPGRADE V17 -> V24 WITH CURRENT APP ===' -ForegroundColor Cyan

    Run-App `
        $CurrentJar `
        "jdbc:postgresql://127.0.0.1:$UpgradePort/$UpgradeDb" `
        $UpgradeUser $UpgradePass `
        (Join-Path $RuntimeRoot 'upgrade-current') `
        'Current upgrade app'

    $null = Assert-Flyway `
        $UpgradeId $UpgradeUser $UpgradeDb $CurrentExpected `
        'v0.13.0 -> current upgrade'

    $HistoryAfter = History-Prefix-Digest `
        $UpgradeId $UpgradeUser $UpgradeDb 17
    if ($HistoryBefore -ne $HistoryAfter) {
        throw 'Historical V1..V17 Flyway metadata changed.'
    }

    $After = Fingerprints `
        $UpgradeId $UpgradeUser $UpgradeDb $V17Tables
    Assert-Fingerprints $Before $After $V17Tables
    Assert-Current-Schema `
        $UpgradeId $UpgradeUser $UpgradeDb 'Upgrade'

    Write-Host 'All 13 V17 row-content fingerprints preserved.' `
        -ForegroundColor Green

    Write-Host ''
    Write-Host '=== 9. VERIFY V23/V24 NEW CONSTRAINT VALUES WITH ROLLBACK ===' `
        -ForegroundColor Cyan

    $constraintSql = @'
BEGIN;
UPDATE refresh_token_families
SET revocation_reason='MFA_DISABLED'
WHERE id='00000000-0000-0000-0000-000000000901';

INSERT INTO account_security_audits
(id,subject_user_id,action,occurred_at)
VALUES
('00000000-0000-0000-0000-000000001201',
 '00000000-0000-0000-0000-000000000101',
 'RECOVERY_CODES_ROTATED',
 '2026-01-03T00:00:00Z');
ROLLBACK;
'@

    [IO.File]::WriteAllText(
        $ConstraintFile,
        $constraintSql + "`r`n",
        [Text.UTF8Encoding]::new($true)
    )
    Psql-File `
        $UpgradeId $UpgradeUser $UpgradeDb `
        $ConstraintFile '/tmp/constraint-probe.sql'

    $AfterProbe = Fingerprints `
        $UpgradeId $UpgradeUser $UpgradeDb $V17Tables
    Assert-Fingerprints $After $AfterProbe $V17Tables

    if ([long](Psql $UpgradeId $UpgradeUser $UpgradeDb `
        'select count(*) from account_security_audits;') -ne 0) {
        throw 'Constraint probe persisted data unexpectedly.'
    }

    Write-Host 'V23/V24 constraint expansion PASS; transaction rolled back.' `
        -ForegroundColor Green

    Write-Host ''
    Write-Host '=== 10. WRITE SANITIZED EVIDENCE ===' -ForegroundColor Cyan

    $lines = New-Object System.Collections.Generic.List[string]
    [void]$lines.Add('PayFlow v1.0.0 CP5 Flyway Clean-Install / Upgrade Rehearsal Evidence')
    [void]$lines.Add("Branch: $Branch")
    [void]$lines.Add("HEAD: $Head")
    [void]$lines.Add("Historical upgrade source: v0.13.0/V17 @ $ExpectedV013Commit")
    [void]$lines.Add("PostgreSQL image: $PostgresImage")
    [void]$lines.Add('Historical V1..V17 blob drift: 0')
    [void]$lines.Add('Clean install V1..V24: PASS')
    [void]$lines.Add('Clean-install health HTTP 200: PASS')
    [void]$lines.Add('Immutable v0.13.0 V1..V17 baseline: PASS')
    [void]$lines.Add('Synthetic V17 seed: all 13 data tables non-empty')
    [void]$lines.Add('Upgrade V18..V24: PASS')
    [void]$lines.Add('Historical V1..V17 Flyway metadata preserved: PASS')
    [void]$lines.Add('All 13 V17 row-content fingerprints preserved: PASS')
    [void]$lines.Add('Upgrade health HTTP 200: PASS')
    [void]$lines.Add('V23 MFA_DISABLED constraint expansion: PASS')
    [void]$lines.Add('V24 RECOVERY_CODES_ROTATED constraint expansion: PASS')
    [void]$lines.Add('')
    [void]$lines.Add('V17 table evidence (count + digest only):')
    foreach ($table in $V17Tables) {
        [void]$lines.Add(
            "  $table count=$($After[$table].Count) digest=$($After[$table].Digest)"
        )
    }
    [void]$lines.Add('')
    [void]$lines.Add(
        'No real credentials, customer data, plaintext security material, row values, dumps, or production data are included.'
    )

    [IO.File]::WriteAllText(
        $Evidence,
        ($lines -join "`r`n") + "`r`n",
        [Text.UTF8Encoding]::new($true)
    )

    $EvidenceHash = (
        Get-FileHash $Evidence -Algorithm SHA256
    ).Hash.ToLowerInvariant()

    if (@(git status --porcelain=v1).Count -ne 0) {
        git status --short
        throw 'Repository changed during probe.'
    }

    Write-Host ''
    Write-Host '=============================================' -ForegroundColor Green
    Write-Host 'v1.0.0 CP5 FLYWAY CLEAN/UPGRADE REHEARSAL PASS' -ForegroundColor Green
    Write-Host '=============================================' -ForegroundColor Green
    Write-Host "Branch           : $Branch"
    Write-Host "HEAD             : $Head"
    Write-Host 'Clean install    : V1 -> V24 PASS'
    Write-Host 'Clean health     : HTTP 200'
    Write-Host 'Upgrade source   : immutable v0.13.0 / V17'
    Write-Host 'Upgrade delta    : V18 -> V24 PASS'
    Write-Host 'Historical drift : 0'
    Write-Host 'Seeded V17 tables: 13 / 13'
    Write-Host 'Data fingerprints: PRESERVED'
    Write-Host 'Upgrade health   : HTTP 200'
    Write-Host 'V23 constraint   : PASS'
    Write-Host 'V24 constraint   : PASS'
    Write-Host 'Repo tree        : CLEAN'
    Write-Host "Evidence         : $Evidence"
    Write-Host "SHA256           : $EvidenceHash"
}
finally {
    if ($null -ne $CleanId) {
        $null = Run-Captured 'docker' @('rm','-f',$CleanId)
    }
    if ($null -ne $UpgradeId) {
        $null = Run-Captured 'docker' @('rm','-f',$UpgradeId)
    }
    if ($WorktreeAdded) {
        $null = Run-Captured 'git' @(
            'worktree','remove','--force',$Worktree
        )
        $null = Run-Captured 'git' @('worktree','prune')
    }

    if (@(git status --porcelain=v1).Count -ne 0) {
        Write-Warning 'Repository is not clean after probe.'
        git status --short
    }
}
