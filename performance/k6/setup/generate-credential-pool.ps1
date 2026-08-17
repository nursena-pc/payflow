param(
    [ValidateRange(1, 4)]
    [int] $Count = 1,

    [string] $BaseUrl = 'http://localhost:18080',

    [string] $MailpitUrl = 'http://localhost:18025',

    [string] $OutputPath
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

. (Join-Path $PSScriptRoot 'totp.ps1')

function Assert-NativeSuccess {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Step
    )

    if ($LASTEXITCODE -ne 0) {
        throw "$Step failed with exit code $LASTEXITCODE."
    }
}

function New-RuntimePassword {
    $Bytes = New-Object byte[] 24
    $Rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()

    try {
        $Rng.GetBytes($Bytes)
    }
    finally {
        $Rng.Dispose()
    }

    return 'Pf!' + [Convert]::ToBase64String($Bytes)
}

function Invoke-PayFlowJson {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet('POST')]
        [string] $Method,

        [Parameter(Mandatory = $true)]
        [string] $Path,

        [Parameter(Mandatory = $true)]
        [int] $ExpectedStatus,

        [Parameter(Mandatory = $true)]
        [string] $Step,

        [object] $Body,

        [string] $BearerToken
    )

    $Headers = @{}

    if (-not [string]::IsNullOrWhiteSpace($BearerToken)) {
        $Headers.Authorization = "Bearer $BearerToken"
    }

    $Parameters = @{
        Method = $Method
        Uri = "$BaseUrl$Path"
        Headers = $Headers
        ContentType = 'application/json'
        UseBasicParsing = $true
        ErrorAction = 'Stop'
    }

    if ($null -ne $Body) {
        $Parameters.Body = ConvertTo-Json `
            -InputObject $Body `
            -Compress `
            -Depth 8
    }

    try {
        $Response = Invoke-WebRequest @Parameters
    }
    catch {
        $FailureResponse = $_.Exception.Response

        if ($null -ne $FailureResponse) {
            $FailureStatus = [int] $FailureResponse.StatusCode
            throw "$Step returned HTTP status $FailureStatus."
        }

        throw "$Step failed before the expected HTTP response was received."
    }

    if ([int] $Response.StatusCode -ne $ExpectedStatus) {
        throw "$Step returned unexpected HTTP status $($Response.StatusCode)."
    }

    if ([string]::IsNullOrWhiteSpace($Response.Content)) {
        return $null
    }

    try {
        return $Response.Content | ConvertFrom-Json
    }
    catch {
        throw "$Step returned an invalid JSON response."
    }
}

function Register-RuntimeUser {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Email,

        [Parameter(Mandatory = $true)]
        [string] $Password
    )

    $null = Invoke-PayFlowJson `
        -Method POST `
        -Path '/api/v1/auth/register' `
        -ExpectedStatus 201 `
        -Step 'runtime registration' `
        -Body @{
            email = $Email
            password = $Password
        }
}

function Test-CanonicalAccountActionCredential {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Credential
    )

    if (
        [string]::IsNullOrWhiteSpace($Credential) -or
        $Credential.Length -ne 43
    ) {
        return $false
    }

    $Base64 = $Credential.Replace('-', '+').Replace('_', '/')

    switch ($Base64.Length % 4) {
        0 { }
        2 { $Base64 += '==' }
        3 { $Base64 += '=' }
        default { return $false }
    }

    $Decoded = $null

    try {
        $Decoded = [Convert]::FromBase64String($Base64)

        if ($Decoded.Length -ne 32) {
            return $false
        }

        $Canonical = [Convert]::ToBase64String($Decoded)
        $Canonical = $Canonical.TrimEnd('=').Replace('+', '-').Replace('/', '_')

        return [string]::Equals(
            $Canonical,
            $Credential,
            [StringComparison]::Ordinal
        )
    }
    catch {
        return $false
    }
    finally {
        if ($null -ne $Decoded) {
            [Array]::Clear($Decoded, 0, $Decoded.Length)
        }
    }
}

function Wait-EmailVerificationCredential {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Email
    )

    # Poll the documented mailbox-list API and perform exact recipient
    # matching locally. This avoids search-index timing and UI-render routes.
    $MailboxUrl = "$MailpitUrl/api/v1/messages?limit=50"

    for ($Attempt = 1; $Attempt -le 60; $Attempt++) {
        try {
            $Mailbox = Invoke-RestMethod `
                -Uri $MailboxUrl `
                -Method Get `
                -TimeoutSec 5 `
                -ErrorAction Stop
        }
        catch {
            Start-Sleep -Seconds 1
            continue
        }

        $Summaries = @($Mailbox.messages)
        $Selected = $null

        foreach ($Summary in $Summaries) {
            foreach ($Recipient in @($Summary.To)) {
                if (
                    $null -ne $Recipient -and
                    [string] $Recipient.Address -ceq $Email
                ) {
                    $Selected = $Summary
                    break
                }
            }

            if ($null -ne $Selected) {
                break
            }
        }

        if ($null -eq $Selected) {
            Start-Sleep -Seconds 1
            continue
        }

        $MessageId = [string] $Selected.ID

        if ([string]::IsNullOrWhiteSpace($MessageId)) {
            throw 'Mailpit returned a recipient-matched message without an ID.'
        }

        $EncodedMessageId = [Uri]::EscapeDataString($MessageId)

        try {
            # Use Mailpit's documented message API instead of /view routes.
            $Message = Invoke-RestMethod `
                -Uri "$MailpitUrl/api/v1/message/$EncodedMessageId" `
                -Method Get `
                -TimeoutSec 5 `
                -ErrorAction Stop
        }
        catch {
            Start-Sleep -Seconds 1
            continue
        }

        $RecipientConfirmed = $false

        foreach ($Recipient in @($Message.To)) {
            if (
                $null -ne $Recipient -and
                [string] $Recipient.Address -ceq $Email
            ) {
                $RecipientConfirmed = $true
                break
            }
        }

        if (-not $RecipientConfirmed) {
            throw 'Mailpit message recipient did not match the disposable runtime user.'
        }

        $Body = [string] $Message.Text

        if ([string]::IsNullOrWhiteSpace($Body)) {
            throw 'Runtime verification mail did not contain a text body.'
        }

        $TokenMatches = [regex]::Matches(
            $Body,
            '(?:[?&])token=([A-Za-z0-9_-]{43})(?=[^A-Za-z0-9_-]|$)'
        )

        if ($TokenMatches.Count -ne 1) {
            throw 'Runtime verification mail did not contain exactly one canonical credential link.'
        }

        $Credential = [string] $TokenMatches[0].Groups[1].Value

        if (-not (Test-CanonicalAccountActionCredential `
            -Credential $Credential)) {
            throw 'Runtime verification mail contained a malformed credential.'
        }

        return $Credential
    }

    throw 'Runtime verification mail was not observed in the Mailpit mailbox within the bounded wait.'
}

function Verify-RuntimeEmail {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Email
    )

    # Registration already issues the first email-verification credential.
    # Requesting another one here would supersede the credential referenced by
    # the registration mail and create a race with asynchronous mail delivery.
    $Credential = Wait-EmailVerificationCredential -Email $Email

    $null = Invoke-PayFlowJson `
        -Method POST `
        -Path '/api/v1/auth/email-verification/confirm' `
        -ExpectedStatus 204 `
        -Step 'confirm runtime email verification' `
        -Body @{
            credential = $Credential
        }
}

function Invoke-PasswordLogin {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Email,

        [Parameter(Mandatory = $true)]
        [string] $Password,

        [Parameter(Mandatory = $true)]
        [int] $ExpectedStatus
    )

    return Invoke-PayFlowJson `
        -Method POST `
        -Path '/api/v1/auth/login' `
        -ExpectedStatus $ExpectedStatus `
        -Step 'runtime password login' `
        -Body @{
            email = $Email
            password = $Password
        }
}

function Enable-RuntimeMfa {
    param(
        [Parameter(Mandatory = $true)]
        [string] $AccessToken,

        [Parameter(Mandatory = $true)]
        [string] $Password
    )

    $Enrollment = Invoke-PayFlowJson `
        -Method POST `
        -Path '/api/v1/users/me/mfa/enrollment' `
        -ExpectedStatus 200 `
        -Step 'begin runtime MFA enrollment' `
        -BearerToken $AccessToken `
        -Body @{
            currentPassword = $Password
        }

    if ([string]::IsNullOrWhiteSpace($Enrollment.secret)) {
        throw 'Runtime MFA enrollment did not return a secret.'
    }

    Wait-PayFlowStableTotpWindow
    $Code = New-PayFlowTotpCode `
        -Base32Secret $Enrollment.secret

    $Confirmation = Invoke-PayFlowJson `
        -Method POST `
        -Path '/api/v1/users/me/mfa/enrollment/confirm' `
        -ExpectedStatus 200 `
        -Step 'confirm runtime MFA enrollment' `
        -BearerToken $AccessToken `
        -Body @{
            code = $Code
        }

    $RecoveryCodes = @($Confirmation.recoveryCodes)

    if ($RecoveryCodes.Count -lt 2) {
        throw 'Runtime MFA enrollment returned an insufficient recovery-code set.'
    }

    return $RecoveryCodes
}

function New-MfaChallengeFixture {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Email,

        [Parameter(Mandatory = $true)]
        [string] $Password
    )

    Register-RuntimeUser -Email $Email -Password $Password
    Verify-RuntimeEmail -Email $Email

    $InitialLogin = Invoke-PasswordLogin `
        -Email $Email `
        -Password $Password `
        -ExpectedStatus 200

    $RecoveryCodes = Enable-RuntimeMfa `
        -AccessToken $InitialLogin.accessToken `
        -Password $Password

    $Challenge = Invoke-PasswordLogin `
        -Email $Email `
        -Password $Password `
        -ExpectedStatus 202

    if ([string]::IsNullOrWhiteSpace($Challenge.challengeToken)) {
        throw 'Runtime MFA login did not return a challenge token.'
    }

    return [ordered] @{
        challengeToken = [string] $Challenge.challengeToken
        code = [string] $RecoveryCodes[0]
    }
}

function New-StepUpFixture {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Email,

        [Parameter(Mandatory = $true)]
        [string] $Password
    )

    Register-RuntimeUser -Email $Email -Password $Password
    Verify-RuntimeEmail -Email $Email

    $InitialLogin = Invoke-PasswordLogin `
        -Email $Email `
        -Password $Password `
        -ExpectedStatus 200

    $RecoveryCodes = Enable-RuntimeMfa `
        -AccessToken $InitialLogin.accessToken `
        -Password $Password

    $Challenge = Invoke-PasswordLogin `
        -Email $Email `
        -Password $Password `
        -ExpectedStatus 202

    $Authenticated = Invoke-PayFlowJson `
        -Method POST `
        -Path '/api/v1/auth/mfa/challenges/confirm' `
        -ExpectedStatus 200 `
        -Step 'complete runtime MFA login challenge' `
        -Body @{
            challengeToken = $Challenge.challengeToken
            code = $RecoveryCodes[0]
        }

    if ([string]::IsNullOrWhiteSpace($Authenticated.accessToken)) {
        throw 'Runtime MFA challenge completion did not return an access token.'
    }

    return [ordered] @{
        accessToken = [string] $Authenticated.accessToken
        purpose = 'mfa-disable'
        code = [string] $RecoveryCodes[1]
    }
}

$RepositoryRoot = (
    Resolve-Path (Join-Path $PSScriptRoot '..\..\..')
).Path

$RuntimeDirectory = Join-Path `
    $RepositoryRoot `
    'performance\results\runtime'

if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $OutputPath = Join-Path `
        $RuntimeDirectory `
        'credential-pool.json'
}

$BaseUri = $null

if (
    -not [Uri]::TryCreate(
        $BaseUrl,
        [UriKind]::Absolute,
        [ref] $BaseUri
    ) -or
    $BaseUri.Scheme -notin @('http', 'https')
) {
    throw 'BaseUrl must be an absolute HTTP or HTTPS URI.'
}

$BaseUrl = $BaseUrl.TrimEnd('/')

$MailpitUri = $null

if (
    -not [Uri]::TryCreate(
        $MailpitUrl,
        [UriKind]::Absolute,
        [ref] $MailpitUri
    ) -or
    $MailpitUri.Scheme -notin @('http', 'https')
) {
    throw 'MailpitUrl must be an absolute HTTP or HTTPS URI.'
}

$MailpitUrl = $MailpitUrl.TrimEnd('/')
$RuntimeRoot = [IO.Path]::GetFullPath($RuntimeDirectory)
$OutputFullPath = [IO.Path]::GetFullPath($OutputPath)
$RuntimePrefix = $RuntimeRoot.TrimEnd(
    [IO.Path]::DirectorySeparatorChar,
    [IO.Path]::AltDirectorySeparatorChar
) + [IO.Path]::DirectorySeparatorChar

if (-not $OutputFullPath.StartsWith(
    $RuntimePrefix,
    [StringComparison]::OrdinalIgnoreCase
)) {
    throw 'Runtime credential output must stay under performance/results/runtime.'
}

New-Item `
    -ItemType Directory `
    -Force `
    -Path $RuntimeRoot `
    | Out-Null

if (Test-Path -LiteralPath $OutputFullPath) {
    Remove-Item -LiteralPath $OutputFullPath -Force
}

$RepositoryPrefix = $RepositoryRoot.TrimEnd(
    [IO.Path]::DirectorySeparatorChar,
    [IO.Path]::AltDirectorySeparatorChar
) + [IO.Path]::DirectorySeparatorChar

if (-not $OutputFullPath.StartsWith(
    $RepositoryPrefix,
    [StringComparison]::OrdinalIgnoreCase
)) {
    throw 'Runtime credential output must stay under repository root.'
}

$RelativeOutput = $OutputFullPath.Substring(
    $RepositoryPrefix.Length
).Replace('\', '/')

Push-Location $RepositoryRoot

try {
    git check-ignore --quiet --no-index -- $RelativeOutput

    if ($LASTEXITCODE -ne 0) {
        throw 'Runtime credential output is not protected by .gitignore.'
    }
}
finally {
    Pop-Location
}

$RunId = [Guid]::NewGuid().ToString('N').Substring(0, 12)
$MfaFixtures = New-Object System.Collections.Generic.List[object]
$StepUpFixtures = New-Object System.Collections.Generic.List[object]

for ($Index = 0; $Index -lt $Count; $Index++) {
    $MfaEmail = "payflow-perf-$RunId-mfa-$Index@example.invalid"
    $MfaPassword = New-RuntimePassword

    $MfaFixtures.Add(
        (New-MfaChallengeFixture `
            -Email $MfaEmail `
            -Password $MfaPassword)
    )

    $StepUpEmail = "payflow-perf-$RunId-stepup-$Index@example.invalid"
    $StepUpPassword = New-RuntimePassword

    $StepUpFixtures.Add(
        (New-StepUpFixture `
            -Email $StepUpEmail `
            -Password $StepUpPassword)
    )
}

$Document = [ordered] @{
    mfaChallengeConfirm = $MfaFixtures.ToArray()
    stepUpGrant = $StepUpFixtures.ToArray()
}

$Json = ConvertTo-Json `
    -InputObject $Document `
    -Depth 8

[IO.File]::WriteAllText(
    $OutputFullPath,
    $Json,
    [Text.UTF8Encoding]::new($false)
)

Write-Host "Generated runtime fixture pool: $RelativeOutput"
Write-Host "MFA challenge fixtures: $($MfaFixtures.Count)"
Write-Host "Step-up fixtures: $($StepUpFixtures.Count)"
Write-Host 'Sensitive fixture values are not printed.'
